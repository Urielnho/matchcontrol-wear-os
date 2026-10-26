import { PGlite } from '@electric-sql/pglite';
import { readFile, readdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
const db = new PGlite();
await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;
 create schema auth;create table auth.users(id uuid primary key);
 create function auth.uid() returns uuid language sql as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
 grant usage on schema auth,public to anon,authenticated,service_role;grant execute on function auth.uid() to authenticated;
`);
for(const file of (await readdir(new URL('../supabase/migrations/',import.meta.url))).filter(x=>x.endsWith('.sql')).sort()) {
 await db.exec(await readFile(new URL('../supabase/migrations/'+file,import.meta.url),'utf8'));
}
const alice='10000000-0000-0000-0000-000000000001',bob='10000000-0000-0000-0000-000000000002';
await db.query('insert into auth.users values($1),($2)',[alice,bob]);
await db.query('select public.ensure_demo_user($1),public.ensure_demo_user($2)',[alice,bob]);
await db.query("update public.profiles set data_mode='DEMO' where id=$1",[alice]);
await db.exec(`insert into public.matches(id,home_team,away_team,league_id,league_name,status,start_time,source) values
 ('m1','{"id":"t1","name":"Aurora","code":"AUR"}','{"id":"t2","name":"Horizonte","code":"HOR"}','l1','Campus','SCHEDULED',now()+interval '1 day','DEMO'),
 ('m2','{"id":"t3","name":"Valle","code":"VAL"}','{"id":"t4","name":"Norte","code":"NOR"}','l1','Campus','SCHEDULED',now()+interval '1 day','DEMO');
 insert into public.markets(id,match_id,market_type) values('20000000-0000-0000-0000-000000000001','m1','WINNER'),('20000000-0000-0000-0000-000000000002','m2','WINNER');
 insert into public.market_outcomes(id,market_id,outcome_key,label,decimal_odds) values('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','1','Local',1.85),('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000002','1','Local',1.85);
`);
const outcome='30000000-0000-0000-0000-000000000001',second='30000000-0000-0000-0000-000000000002',request='40000000-0000-0000-0000-000000000001';
let passed=0;
async function check(name,fn){await fn();passed++;console.log('PASS '+name)}
const scalar=async(sql,args=[])=>Object.values((await db.query(sql,args)).rows[0])[0];
const place=(id=request,stake=100,picks={[outcome]:18500})=>scalar('select public.place_demo_bet($1,$2,$3,$4)',[alice,id,stake,JSON.stringify(picks)]);
await check('initial 10000 and unique wallet',async()=>assert.equal(await scalar('select balance::text from public.wallets where user_id=$1',[alice]),'10000.00'));
const bet=await place();
await check('atomic debit',async()=>assert.equal(await scalar('select balance::text from public.wallets where user_id=$1',[alice]),'9900.00'));
await check('idempotent retry',async()=>assert.equal(await place(),bet));
await check('different payload rejected',async()=>assert.rejects(()=>place(request,200),/Idempotency/));
await check('insufficient credits rejected',async()=>assert.rejects(()=>place('40000000-0000-0000-0000-000000000002',99999),/Insufficient/));
await check('changed odds rejected',async()=>assert.rejects(()=>place('40000000-0000-0000-0000-000000000003',100,{[outcome]:20000}),/Odds/));
await check('unverified result pending',async()=>assert.equal(await scalar('select public.settle_demo_bet($1)',[bet]),'PENDING'));
await db.exec(`update public.matches set status='FINAL',verified=true,home_score=2,away_score=1 where id='m1';`);
await check('settlement win',async()=>assert.equal(await scalar('select public.settle_demo_bet($1)',[bet]),'WON'));
await check('credit only once',async()=>{await db.query('select public.settle_demo_bet($1)',[bet]);assert.equal(await scalar('select balance::text from public.wallets where user_id=$1',[alice]),'10085.00')});
await db.query("select set_config('request.jwt.claim.sub',$1,false)",[alice]);await db.exec('set role authenticated');
await check('RLS cannot read another wallet',async()=>assert.equal(await scalar('select count(*)::int from public.wallets where user_id=$1',[bob]),0));
await check('caller-scoped snapshot obeys RLS',async()=>{assert.equal((await scalar('select public.sandbox_snapshot($1)',[alice])).balance,1008500);assert.equal(await scalar('select public.sandbox_snapshot($1)',[bob]),null)});
await check('RLS cannot update balances',async()=>assert.rejects(()=>db.exec('update public.wallets set balance=999999'),/permission denied/));
await check('client cannot execute financial RPC',async()=>assert.rejects(()=>place(),/permission denied/));
await db.exec('reset role');
await check('snapshot wire format uses integer credit cents',async()=>{const s=await scalar('select public.sandbox_snapshot($1)',[alice]);assert.equal(s.balance,1008500);assert.equal(s.bets[0].stake,10000);assert.equal(s.bets[0].picks[0].lockedOdds,18500);assert.equal(s.profile.id,alice)});
await check('void refund and duplicate settlement',async()=>{
 const id=await place('40000000-0000-0000-0000-000000000004',100,{[second]:18500});
 await db.query('select public.finish_sandbox_fixture($1,null,null,true)',['m2']);
 assert.equal(await scalar('select public.settle_demo_bet($1)',[id]),'VOID');
 await db.query('select public.settle_demo_bet($1)',[id]);
 assert.equal(await scalar('select balance::text from public.wallets where user_id=$1',[alice]),'10085.00');
});
await check('reset keeps history and is idempotent',async()=>{
 await db.query('select public.reset_demo_wallet($1,$2)',[alice,request]);
 await db.query('select public.reset_demo_wallet($1,$2)',[alice,request]);
 assert.equal(await scalar('select count(*)::int from public.wallet_transactions where transaction_type=\'RESET\' and reference_id=$1',[request]),1);
 assert.equal(await scalar('select balance::text from public.wallets where user_id=$1',[alice]),'10000.00');
});
await check('provider daily budget is atomic and bounded',async()=>{for(let i=0;i<95;i++)assert.equal(await scalar('select public.reserve_provider_request(current_date)'),true);assert.equal(await scalar('select public.reserve_provider_request(current_date)'),false)});
await check('another user cannot inject own profile into favorites',async()=>{
 await db.query("select set_config('request.jwt.claim.sub',$1,false)",[alice]);await db.exec('set role authenticated');
 await assert.rejects(()=>db.query('insert into public.favorite_teams(user_id,team_id) values($1,$2)',[bob,'t1']),/row-level security/);
 await db.exec('reset role');
});
await check('RLS enabled on all public tables',async()=>assert.equal(await scalar("select count(*)::int from pg_tables where schemaname='public' and not rowsecurity"),0));
const providerFixture={fixture:{id:12345,date:new Date(Date.now()+86400000).toISOString(),status:{short:'NS'}},teams:{home:{id:47,name:'Provider Home',logo:'https://example.test/home.png'},away:{id:48,name:'Provider Away'}},league:{id:39,name:'Provider League'},goals:{home:null,away:null}};
const asof=new Date().toISOString();
await check('provider IDs are namespaced and kickoff is persisted',async()=>{await db.query('select public.ingest_football($1,$2)',[JSON.stringify([providerFixture]),asof]);assert.equal(await scalar("select home_team->>'id' from public.matches where id='api:12345'"),'api:47')});
await check('provider totals 1.5 and 2.5 preserve actual decimal odds',async()=>{
 await db.query('select public.ingest_football_odds($1)',[JSON.stringify([{fixture:{id:12345},bookmakers:[{bets:[{id:5,values:[{value:'Over 1.5',odd:'1.40'},{value:'Under 1.5',odd:'2.60'},{value:'Over 2.5',odd:'1.90'},{value:'Under 2.5',odd:'1.80'}]}]}]}])]);
 assert.equal(await scalar("select count(*)::int from public.market_outcomes o join public.markets m on m.id=o.market_id where m.match_id='api:12345'"),4);
});
const realOutcome=await scalar("select o.id from public.market_outcomes o join public.markets m on m.id=o.market_id where m.match_id='api:12345' and m.market_type='OVER_UNDER_25' and o.outcome_key='OVER'");
await check('real events cannot enter a demo-mode ticket',async()=>assert.rejects(()=>place('40000000-0000-0000-0000-000000000090',100,{[realOutcome]:19000}),/selected data mode/));
const realBet=await scalar('select public.place_demo_bet($1,$2,100,$3)',[bob,'40000000-0000-0000-0000-000000000091',JSON.stringify({[realOutcome]:19000})]);
await check('published extra-time score is not the regulation settlement score',async()=>{
 const final={...providerFixture,fixture:{...providerFixture.fixture,date:new Date(Date.now()-3600000).toISOString(),status:{short:'AET'}},goals:{home:3,away:2},score:{fulltime:{home:1,away:1}}};
 await db.query('select public.ingest_football($1,$2)',[JSON.stringify([final]),new Date(Date.now()+1000).toISOString()]);
 assert.equal(await scalar('select public.settle_demo_bet($1)',[realBet]),'LOST');
});
await check('older cached schedule cannot overwrite a newer result',async()=>{
 await db.query('select public.ingest_football($1,$2)',[JSON.stringify([providerFixture]),asof]);
 assert.equal(await scalar("select status from public.matches where id='api:12345'"),'FINAL');
});
console.log(JSON.stringify({passed,failed:0,engine:'PGlite PostgreSQL; auth schema mocked, no Supabase deployment'}));
await db.close();
