-- Sport-specific educational settlement; existing football history is preserved.
alter table public.matches add column sport text not null default 'FOOTBALL' check(sport in ('FOOTBALL','BASKETBALL','BASEBALL','FORMULA1'));
alter table public.matches add column participants jsonb not null default '[]';
alter table public.matches add column race_results jsonb not null default '[]';
alter table public.matches add column classification_complete boolean not null default false;
alter table public.matches add column demo_owner uuid references public.profiles(id);
create index matches_demo_owner on public.matches(demo_owner,start_time);
alter table public.matches drop constraint matches_home_score_check;
alter table public.matches drop constraint matches_away_score_check;
alter table public.matches add check(home_score between 0 and 300);
alter table public.matches add check(away_score between 0 and 300);
alter table public.matches drop constraint matches_check;
alter table public.matches add check(status<>'FINAL' or (verified and ((sport='FORMULA1' and classification_complete) or (sport<>'FORMULA1' and home_score is not null and away_score is not null))));
alter table public.markets drop constraint markets_market_type_check;
alter table public.markets add check(market_type in ('WINNER','DOUBLE_CHANCE','OVER_UNDER_15','OVER_UNDER_25','BOTH_SCORE','BASKETBALL_WINNER','TOTAL_POINTS_2155','BASEBALL_WINNER','TOTAL_RUNS_85','RACE_WINNER','RACE_PODIUM','RACE_TOP10'));
alter table public.market_outcomes drop constraint market_outcomes_outcome_key_check;
alter table public.market_outcomes add check(length(outcome_key) between 1 and 100 and outcome_key~'^[A-Za-z0-9_-]+$');
drop policy read_matches on public.matches;
create policy read_matches on public.matches for select to authenticated using (demo_owner is null or demo_owner=(select auth.uid()));
drop policy read_markets on public.markets;
create policy read_markets on public.markets for select to authenticated using (exists(select 1 from public.matches f where f.id=match_id));
drop policy read_outcomes on public.market_outcomes;
create policy read_outcomes on public.market_outcomes for select to authenticated using (exists(select 1 from public.markets m where m.id=market_id));
create function public.valid_sport_market(p_sport text,p_market text,p_key text,p_drivers jsonb) returns boolean language sql immutable security invoker set search_path='' as $$
 select case p_sport
 when 'FOOTBALL' then (p_market='WINNER' and p_key in ('1','X','2')) or (p_market='DOUBLE_CHANCE' and p_key in ('1X','X2','12')) or (p_market in ('OVER_UNDER_15','OVER_UNDER_25') and p_key in ('OVER','UNDER')) or (p_market='BOTH_SCORE' and p_key in ('YES','NO'))
 when 'BASKETBALL' then (p_market='BASKETBALL_WINNER' and p_key in ('1','2')) or (p_market='TOTAL_POINTS_2155' and p_key in ('OVER','UNDER'))
 when 'BASEBALL' then (p_market='BASEBALL_WINNER' and p_key in ('1','2')) or (p_market='TOTAL_RUNS_85' and p_key in ('OVER','UNDER'))
 when 'FORMULA1' then p_market in ('RACE_WINNER','RACE_PODIUM','RACE_TOP10') and exists(select 1 from jsonb_array_elements(p_drivers) d where d->>'id'=p_key)
 else false end;
$$;
revoke all on function public.valid_sport_market(text,text,text,jsonb) from public,anon,authenticated;
grant execute on function public.valid_sport_market(text,text,text,jsonb) to service_role;
create or replace function public.place_demo_bet(p_user uuid,p_request uuid,p_stake numeric,p_selections jsonb) returns uuid language plpgsql security invoker set search_path='' as $$
declare wallet public.wallets; existing public.bets; oid text; row record; ids text[]='{}'; product numeric=1; bid uuid=gen_random_uuid(); payload jsonb; event_source text;
begin
 if p_user is null or p_request is null or p_stake is null or p_stake<1 or p_stake>100000 or p_stake<>trunc(p_stake,2) or p_selections is null or jsonb_typeof(p_selections)<>'object' then
  raise exception 'Invalid stake or selections';
 end if;
 payload=jsonb_build_object('stake',p_stake,'outcomes',p_selections);
 select * into wallet from public.wallets where user_id=p_user for update;
 if not found then raise exception 'Wallet missing'; end if;
 select * into existing from public.bets where user_id=p_user and request_id=p_request;
 if found then if existing.request_payload<>payload then raise exception 'Idempotency conflict'; end if;return existing.id;end if;
 if wallet.balance<p_stake then raise exception 'Insufficient credits'; end if;
 if (select count(*) from jsonb_object_keys(p_selections)) not between 1 and 10 then raise exception 'Select 1 to 10 events';end if;
 for oid in select jsonb_object_keys(p_selections) order by 1 loop
  select o.*,m.match_id,m.market_type,m.status market_status,f.start_time,f.status fixture_status,f.source,f.sport,f.participants,f.demo_owner into row from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where o.id=oid::uuid for update of o,m,f;
  if not found or row.market_status<>'OPEN' or row.fixture_status<>'SCHEDULED' or row.start_time<=now() or row.source not in ('DEMO','API_FOOTBALL') then raise exception 'Closed or unavailable event';end if;
  if row.demo_owner is not null and row.demo_owner<>p_user then raise exception 'Another demo owner';end if;
  if row.source<>(select data_mode from public.profiles where id=p_user) then raise exception 'Event is not in the selected data mode';end if;
  if row.source='API_FOOTBALL' and row.updated_at<now()-interval '30 minutes' then raise exception 'Stale odds';end if;
  if event_source is not null and event_source<>row.source then raise exception 'Cannot combine real and demo events';end if;
  event_source=row.source;
  if row.match_id=any(ids) then raise exception 'Only one selection per event';end if;
  if row.decimal_odds*10000<>(p_selections->>oid)::numeric then raise exception 'Odds changed';end if;
  if not public.valid_sport_market(row.sport,row.market_type,row.outcome_key,row.participants) then raise exception 'Invalid market outcome';end if;
  ids=array_append(ids,row.match_id);product=product*row.decimal_odds;
 end loop;
 insert into public.bets(id,user_id,request_id,request_payload,stake,total_odds,potential_return) values(bid,p_user,p_request,payload,p_stake,product,trunc(p_stake*product,2));
 insert into public.bet_selections(bet_id,match_id,outcome_id,market_type,outcome_key,label,fixture_label,locked_odds)
 select bid,m.match_id,o.id,m.market_type,o.outcome_key,o.label,case when f.sport='FORMULA1' then f.home_team->>'name' else (f.home_team->>'code')||' · '||(f.away_team->>'code') end,o.decimal_odds from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where o.id::text in (select jsonb_object_keys(p_selections));
 update public.wallets set balance=balance-p_stake,updated_at=now() where id=wallet.id;
 insert into public.wallet_transactions(wallet_id,transaction_type,amount,reference_id) values(wallet.id,'STAKE',-p_stake,bid::text);
 return bid;
end $$;
create or replace function public.sandbox_snapshot(p_user uuid) returns jsonb language sql security invoker set search_path='' as $$
select jsonb_build_object(
 'profile',jsonb_build_object('id',p.id,'displayName',p.display_name),
 'balance',(w.balance*100)::bigint,'source',p.data_mode,'notifications',coalesce((select enabled from public.notification_preferences where user_id=p_user),false),
 'fixtures',coalesce((select jsonb_agg(jsonb_build_object('id',f.id,'home',f.home_team,'away',f.away_team,'leagueId',f.league_id,'league',f.league_name,'startsAt',(extract(epoch from f.start_time)*1000)::bigint,'status',f.status,'homeScore',f.home_score,'awayScore',f.away_score,'verified',f.verified,'source',f.source,'events',f.events,'statistics',f.statistics,'sport',f.sport,'participants',f.participants,'raceResults',f.race_results,'classificationComplete',f.classification_complete,'regulationHomeScore',f.regulation_home_score,'regulationAwayScore',f.regulation_away_score) order by f.start_time) from public.matches f where f.source=p.data_mode and (f.demo_owner is null or f.demo_owner=p_user)),'[]'),
 'outcomes',coalesce((select jsonb_agg(jsonb_build_object('id',o.id,'matchId',m.match_id,'market',m.market_type,'key',o.outcome_key,'label',o.label,'odds',(o.decimal_odds*10000)::integer,'updatedAt',(extract(epoch from o.updated_at)*1000)::bigint,'open',m.status='OPEN' and f.status='SCHEDULED' and f.start_time>now())) from public.market_outcomes o join public.markets m on m.id=o.market_id join public.matches f on f.id=m.match_id where f.source=p.data_mode and (f.demo_owner is null or f.demo_owner=p_user)),'[]'),
 'bets',coalesce((select jsonb_agg(jsonb_build_object('id',b.id,'requestId',b.request_id,'stake',(b.stake*100)::bigint,'totalOdds',b.total_odds::text,'potential',(b.potential_return*100)::bigint,'source',coalesce((select f.source from public.bet_selections bs join public.matches f on f.id=bs.match_id where bs.bet_id=b.id limit 1),'DEMO'),'status',b.status,'createdAt',(extract(epoch from b.created_at)*1000)::bigint,'settledAt',(extract(epoch from b.settled_at)*1000)::bigint,'returned',(b.returned*100)::bigint,
  'picks',coalesce((select jsonb_agg(jsonb_build_object('outcomeId',s.outcome_id,'matchId',s.match_id,'market',s.market_type,'key',s.outcome_key,'label',s.label,'fixtureLabel',s.fixture_label,'lockedOdds',(s.locked_odds*10000)::integer,'status',s.status,'sport',(select sport from public.matches where id=s.match_id))) from public.bet_selections s where s.bet_id=b.id),'[]')) order by b.created_at) from public.bets b where b.user_id=p_user),'[]'),
 'transactions',coalesce((select jsonb_agg(jsonb_build_object('id',t.id,'kind',t.transaction_type,'amount',(t.amount*100)::bigint,'reference',t.reference_id,'createdAt',(extract(epoch from t.created_at)*1000)::bigint) order by t.created_at) from public.wallet_transactions t where t.wallet_id=w.id),'[]'),
 'favoriteTeams',coalesce((select jsonb_agg(team_id) from public.favorite_teams where user_id=p_user),'[]'),
 'favoriteLeagues',coalesce((select jsonb_agg(league_id) from public.favorite_leagues where user_id=p_user),'[]'),
 'favoriteMatches',coalesce((select jsonb_agg(match_id) from public.favorite_matches where user_id=p_user),'[]'),
 'dataUpdatedAt',coalesce((select (extract(epoch from max(nullif(f.provider_updated_at,'-infinity'::timestamptz)))*1000)::bigint from public.matches f where f.source=p.data_mode and (f.demo_owner is null or f.demo_owner=p_user)),0),'updatedAt',(extract(epoch from now())*1000)::bigint,'revision',(extract(epoch from now())*1000)::bigint
) from public.profiles p join public.wallets w on w.user_id=p.id where p.id=p_user;
$$;
create or replace function public.settle_demo_bet(p_bet uuid) returns text language plpgsql security invoker set search_path='' as $$
declare b public.bets; w public.wallets; row record; result text; has_pending boolean=false; has_loss boolean=false; active integer=0; product numeric=1; payout numeric=0;
begin
 -- All financial operations lock wallet before bet: consistent ordering avoids deadlocks.
 select w0.* into w from public.wallets w0 join public.bets b0 on b0.user_id=w0.user_id where b0.id=p_bet for update of w0;
 select * into b from public.bets where id=p_bet for update;
 if not found then raise exception 'Bet missing';end if;
 if b.status<>'PENDING' then return b.status;end if;
 for row in select s.*,f.status fixture_status,f.verified,f.sport,f.race_results,f.classification_complete,case when f.sport='FOOTBALL' then coalesce(f.regulation_home_score,case when f.source='DEMO' then f.home_score end) else f.home_score end h,case when f.sport='FOOTBALL' then coalesce(f.regulation_away_score,case when f.source='DEMO' then f.away_score end) else f.away_score end a from public.bet_selections s join public.matches f on f.id=s.match_id where s.bet_id=p_bet loop
  result='PENDING';
  if row.fixture_status='VOID' then result='VOID';
  elsif row.fixture_status='FINAL' and row.verified and row.sport='FORMULA1' and row.classification_complete then
   select case when coalesce((d->>'classified')::boolean,false) and (d->>'position')::integer between 1 and case row.market_type when 'RACE_WINNER' then 1 when 'RACE_PODIUM' then 3 when 'RACE_TOP10' then 10 else 0 end then 'WON' else 'LOST' end into result from jsonb_array_elements(row.race_results) d where d->>'driverId'=row.outcome_key;
   result=coalesce(result,'PENDING');
  elsif row.fixture_status='FINAL' and row.verified and row.sport='BASEBALL' and row.h=row.a and row.market_type='BASEBALL_WINNER' then result='VOID';
  elsif row.fixture_status='FINAL' and row.verified and row.sport='BASKETBALL' and row.h=row.a then result='PENDING';
  elsif row.fixture_status='FINAL' and row.verified and row.h is not null and row.a is not null then
   result=case when case row.market_type
    when 'WINNER' then (row.outcome_key='1' and row.h>row.a) or (row.outcome_key='X' and row.h=row.a) or (row.outcome_key='2' and row.h<row.a)
    when 'DOUBLE_CHANCE' then (row.outcome_key='1X' and row.h>=row.a) or (row.outcome_key='X2' and row.h<=row.a) or (row.outcome_key='12' and row.h<>row.a)
    when 'OVER_UNDER_15' then (row.outcome_key='OVER' and row.h+row.a>=2) or (row.outcome_key='UNDER' and row.h+row.a<=1)
    when 'OVER_UNDER_25' then (row.outcome_key='OVER' and row.h+row.a>=3) or (row.outcome_key='UNDER' and row.h+row.a<=2)
    when 'BOTH_SCORE' then (row.outcome_key='YES' and row.h>0 and row.a>0) or (row.outcome_key='NO' and (row.h=0 or row.a=0))
    when 'BASKETBALL_WINNER' then (row.outcome_key='1' and row.h>row.a) or (row.outcome_key='2' and row.h<row.a)
    when 'BASEBALL_WINNER' then (row.outcome_key='1' and row.h>row.a) or (row.outcome_key='2' and row.h<row.a)
    when 'TOTAL_POINTS_2155' then (row.outcome_key='OVER' and row.h+row.a>=216) or (row.outcome_key='UNDER' and row.h+row.a<=215)
    when 'TOTAL_RUNS_85' then (row.outcome_key='OVER' and row.h+row.a>=9) or (row.outcome_key='UNDER' and row.h+row.a<=8)
    else false end then 'WON' else 'LOST' end;
  end if;
  update public.bet_selections set status=result where id=row.id;
  if result='PENDING' then has_pending=true;end if;
  if result='LOST' then has_loss=true;end if;
  if result<>'VOID' then active=active+1;product=product*row.locked_odds;end if;
 end loop;
 if has_pending then return 'PENDING';end if;
 result=case when has_loss then 'LOST' when active=0 then 'VOID' else 'WON' end;
 payout=case when result='LOST' then 0 when result='VOID' then b.stake else trunc(b.stake*product,2) end;
 update public.bets set status=result,returned=payout,settled_at=now() where id=p_bet;
 update public.wallets set balance=balance+payout,updated_at=now() where id=w.id;
 insert into public.wallet_transactions(wallet_id,transaction_type,amount,reference_id) values(w.id,case when result='VOID' then 'REFUND' else 'SETTLEMENT' end,payout,p_bet::text);
 return result;
end $$;
create function public.create_multisport_demo(p_user uuid,p_request uuid) returns void language plpgsql security invoker set search_path='' as $$
declare sport text; fid text; home jsonb; away jsonb; drivers jsonb; market text; mid uuid; values_list jsonb; value jsonb; i integer;
begin
 perform 1 from public.profiles where id=p_user for update;
 if not found or p_request is null then raise exception 'Missing owner';end if;
 drivers='[{"id":"demo-driver-0","name":"Mateo Solís","code":"P1"},{"id":"demo-driver-1","name":"Sebastián Vega","code":"P2"},{"id":"demo-driver-2","name":"Leo Campos","code":"P3"},{"id":"demo-driver-3","name":"Bruno Torres","code":"P4"},{"id":"demo-driver-4","name":"Nicolás Marín","code":"P5"},{"id":"demo-driver-5","name":"Adrián Ríos","code":"P6"}]';
 foreach sport in array array['FOOTBALL','BASKETBALL','BASEBALL','FORMULA1'] loop
  fid='demo:'||p_user||':'||p_request||':'||lower(sport);
  if exists(select 1 from public.matches where id=fid) then continue;end if;
  home=jsonb_build_object('id','demo-'||sport||'-home','name',case sport when 'FOOTBALL' then 'Atlético Aurora' when 'BASKETBALL' then 'Halcones Campus' when 'BASEBALL' then 'Lobos del Parque' else 'Gran Premio Campus' end,'code','LOC');
  away=jsonb_build_object('id','demo-'||sport||'-away','name',case sport when 'FOOTBALL' then 'Deportivo Horizonte' when 'BASKETBALL' then 'Titanes del Valle' when 'BASEBALL' then 'Águilas del Sur' else 'Circuito Universitario' end,'code','VIS');
  insert into public.matches(id,home_team,away_team,league_id,league_name,status,start_time,source,sport,participants,demo_owner)
   values(fid,home,away,'demo-'||sport,'Campeonato Campus','SCHEDULED',now()+interval '90 minutes','DEMO',sport,case when sport='FORMULA1' then drivers else '[]'::jsonb end,p_user);
  foreach market in array case sport when 'FOOTBALL' then array['WINNER','DOUBLE_CHANCE','OVER_UNDER_15','OVER_UNDER_25','BOTH_SCORE'] when 'BASKETBALL' then array['BASKETBALL_WINNER','TOTAL_POINTS_2155'] when 'BASEBALL' then array['BASEBALL_WINNER','TOTAL_RUNS_85'] else array['RACE_WINNER','RACE_PODIUM','RACE_TOP10'] end loop
   mid=md5(fid||market)::uuid;
   insert into public.markets(id,match_id,market_type) values(mid,fid,market);
   if sport='FORMULA1' then
    for i in 0..jsonb_array_length(drivers)-1 loop
     insert into public.market_outcomes(id,market_id,outcome_key,label,decimal_odds) values(md5(mid::text||i)::uuid,mid,drivers->i->>'id',drivers->i->>'name',case market when 'RACE_WINNER' then 2.2+i*.65 when 'RACE_PODIUM' then 1.35+i*.22 else 1.1+i*.07 end);
    end loop;
   else
    values_list=case market
     when 'WINNER' then '[["1","Local",1.85],["X","Empate",3.25],["2","Visitante",2.40]]'::jsonb
     when 'DOUBLE_CHANCE' then '[["1X","1 o empate",1.35],["X2","Empate o 2",1.45],["12","Sin empate",1.25]]'::jsonb
     when 'OVER_UNDER_15' then '[["OVER","Más de 1.5",1.40],["UNDER","Menos de 1.5",2.60]]'::jsonb
     when 'OVER_UNDER_25' then '[["OVER","Más de 2.5",1.90],["UNDER","Menos de 2.5",1.80]]'::jsonb
     when 'BOTH_SCORE' then '[["YES","Sí",1.75],["NO","No",2.00]]'::jsonb
     when 'TOTAL_POINTS_2155' then '[["OVER","Más de 215.5",1.90],["UNDER","Menos de 215.5",1.90]]'::jsonb
     when 'TOTAL_RUNS_85' then '[["OVER","Más de 8.5",1.90],["UNDER","Menos de 8.5",1.90]]'::jsonb
     else '[["1","Local",1.85],["2","Visitante",2.05]]'::jsonb end;
    for value in select * from jsonb_array_elements(values_list) loop
     insert into public.market_outcomes(id,market_id,outcome_key,label,decimal_odds) values(md5(mid::text||(value->>0))::uuid,mid,value->>0,value->>1,(value->>2)::numeric);
    end loop;
   end if;
  end loop;
 end loop;
end $$;
revoke all on function public.create_multisport_demo(uuid,uuid) from public,anon,authenticated;
grant execute on function public.create_multisport_demo(uuid,uuid) to service_role;

create function public.finish_user_demo(p_user uuid,p_match text,p_home integer,p_away integer,p_order jsonb,p_void boolean) returns void language plpgsql security invoker set search_path='' as $$
declare f public.matches; results jsonb;
begin
 perform 1 from public.wallets where user_id=p_user for update;
 select * into f from public.matches where id=p_match for update;
 if not found or f.source<>'DEMO' or f.demo_owner is distinct from p_user then raise exception 'Not your educational event';end if;
 if f.status in ('FINAL','VOID') then raise exception 'Result already verified';end if;
 if p_void then update public.matches set status='VOID',verified=true where id=p_match;
 elsif f.sport='FORMULA1' then
  if jsonb_typeof(p_order)<>'array' or jsonb_array_length(p_order)<>jsonb_array_length(f.participants) or
   (select count(distinct x) from jsonb_array_elements_text(p_order) x)<>jsonb_array_length(f.participants) or
   exists(select 1 from jsonb_array_elements_text(p_order) x where not exists(select 1 from jsonb_array_elements(f.participants) d where d->>'id'=x)) then raise exception 'Full unique classification required';end if;
  select jsonb_agg(jsonb_build_object('driverId',x,'position',ord,'classified',true) order by ord) into results from jsonb_array_elements_text(p_order) with ordinality t(x,ord);
  update public.matches set status='FINAL',verified=true,classification_complete=true,race_results=results where id=p_match;
 else
  if p_home is null or p_away is null or p_home<0 or p_away<0 or p_home>(case when f.sport='BASKETBALL' then 300 else 100 end) or p_away>(case when f.sport='BASKETBALL' then 300 else 100 end) or (f.sport='BASKETBALL' and p_home=p_away) then raise exception 'Invalid final score';end if;
  update public.matches set status='FINAL',verified=true,home_score=p_home,away_score=p_away where id=p_match;
 end if;
 update public.markets set status='CLOSED' where match_id=p_match;
 perform public.reconcile_user_bets(p_user);
end $$;
revoke all on function public.finish_user_demo(uuid,text,integer,integer,jsonb,boolean) from public,anon,authenticated;
grant execute on function public.finish_user_demo(uuid,text,integer,integer,jsonb,boolean) to service_role;

create function public.ingest_sport_events(p_rows jsonb,p_asof timestamptz) returns integer language plpgsql security invoker set search_path='' as $$
declare r jsonb; count integer=0;
begin
 for r in select * from jsonb_array_elements(p_rows) loop
  if not ((r->>'sport'='BASKETBALL' and r->>'id'~'^basket:[0-9]+$') or (r->>'sport'='BASEBALL' and r->>'id'~'^base:[0-9]+$') or (r->>'sport'='FORMULA1' and r->>'id'~'^f1:[0-9]{4}:[0-9]+$')) then raise exception 'Invalid provider sport';end if;
  insert into public.matches(id,home_team,away_team,league_id,league_name,status,home_score,away_score,start_time,source,sport,participants,race_results,classification_complete,verified,events,statistics,provider_updated_at)
   values(r->>'id',r->'home',r->'away',r->>'leagueId',r->>'league',r->>'status',(r->>'homeScore')::integer,(r->>'awayScore')::integer,to_timestamp((r->>'startsAt')::numeric/1000),'API_FOOTBALL',r->>'sport',coalesce(r->'participants','[]'),coalesce(r->'raceResults','[]'),coalesce((r->>'classificationComplete')::boolean,false),coalesce((r->>'verified')::boolean,false),coalesce(r->'events','[]'),coalesce(r->'statistics','{}'),p_asof)
   on conflict(id) do update set home_team=excluded.home_team,away_team=excluded.away_team,status=excluded.status,home_score=excluded.home_score,away_score=excluded.away_score,start_time=excluded.start_time,participants=excluded.participants,race_results=excluded.race_results,classification_complete=excluded.classification_complete,verified=excluded.verified,events=excluded.events,statistics=excluded.statistics,provider_updated_at=excluded.provider_updated_at
    where excluded.provider_updated_at>=public.matches.provider_updated_at and (public.matches.status not in ('FINAL','VOID') or excluded.status in ('FINAL','VOID'));
  update public.markets set status='CLOSED' where match_id=r->>'id' and exists(select 1 from public.matches f where f.id=r->>'id' and (f.status<>'SCHEDULED' or f.start_time<=now()));
  count=count+1;
 end loop;
 return count;
end $$;
revoke all on function public.ingest_sport_events(jsonb,timestamptz) from public,anon,authenticated;
grant execute on function public.ingest_sport_events(jsonb,timestamptz) to service_role;

create function public.ingest_sport_odds(p_rows jsonb) returns integer language plpgsql security invoker set search_path='' as $$
declare r jsonb; f public.matches; mid uuid; count integer=0;
begin
 for r in select * from jsonb_array_elements(p_rows) loop
  select * into f from public.matches where id=r->>'matchId' and source='API_FOOTBALL';
  if not found or f.sport not in ('BASKETBALL','BASEBALL') or f.status<>'SCHEDULED' or f.start_time<=now() then continue;end if;
  if not public.valid_sport_market(f.sport,r->>'market',r->>'key',f.participants) or (r->>'odds')::numeric not between 1.0001 and 100 then raise exception 'Invalid provider odds';end if;
  mid=md5(f.id||(r->>'market'))::uuid;
  insert into public.markets(id,match_id,market_type) values(mid,f.id,r->>'market') on conflict(match_id,market_type) do update set status='OPEN' returning id into mid;
  insert into public.market_outcomes(id,market_id,outcome_key,label,decimal_odds,updated_at) values(md5(mid::text||(r->>'key'))::uuid,mid,r->>'key',r->>'label',(r->>'odds')::numeric,now())
   on conflict(market_id,outcome_key) do update set label=excluded.label,decimal_odds=excluded.decimal_odds,updated_at=now();
  count=count+1;
 end loop;
 return count;
end $$;
revoke all on function public.ingest_sport_odds(jsonb) from public,anon,authenticated;
grant execute on function public.ingest_sport_odds(jsonb) to service_role;
