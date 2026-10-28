type Row=Record<string,any>;
type Admin={rpc(name:string,args?:Record<string,unknown>):PromiseLike<{data:any;error:any}>};
export type Sport="BASKETBALL"|"BASEBALL"|"FORMULA1";
const prefix=(sport:Sport)=>sport==="BASKETBALL"?"basket:":sport==="BASEBALL"?"base:":"f1:";
async function cached(admin:Admin,sport:Sport,path:string) {
 const key=`sport:${sport}:${path}`;
 const saved=await admin.rpc("sports_cache_read",{p_key:key});if(saved.error) throw saved.error;
 if(saved.data && Date.now()-Date.parse(saved.data.updated_at)<15*60*1000) return {data:saved.data.payload,asof:saved.data.updated_at,stale:false};
 try {
  const budget=await admin.rpc("reserve_provider_request",{p_day:new Date().toISOString().slice(0,10)});if(budget.error || !budget.data) throw Error("Quota reached");
  const host=sport==="FORMULA1"?"https://api.jolpi.ca/ergast/f1/":`https://v1.${sport.toLowerCase()}.api-sports.io/`;
  const headers:Record<string,string>={"User-Agent":"MatchControl/2.0"};
  if(sport!=="FORMULA1") {const secret=Deno.env.get("FOOTBALL_API_KEY");if(!secret) throw Error("Provider unavailable");headers["x-apisports-key"]=secret;}
  const response=await fetch(host+path,{headers,signal:AbortSignal.timeout(20000)});const data=await response.json();
  if(!response.ok || (sport==="FORMULA1"?!Array.isArray(data.MRData?.RaceTable?.Races):!Array.isArray(data.response)||Object.keys(data.errors??{}).length>0)) throw Error("No coverage");
  const asof=new Date().toISOString();const save=await admin.rpc("sports_cache_write",{p_key:key,p_payload:data});if(save.error) throw save.error;
  return {data,asof,stale:false};
 } catch(e) {if(saved.data) return {data:saved.data.payload,asof:saved.data.updated_at,stale:true};throw e;}
}
export function normalizeGame(r:Row,sport:Sport) {
 const p=prefix(sport);const code=String(r.status?.short??"");
 let status=["FT","AOT"].includes(code)?"FINAL":["CANC","ABD","AWD","WO"].includes(code)?"VOID":["POST","PST","SUSP","INT"].includes(code)?"SUSPENDED":code==="NS"?"SCHEDULED":"LIVE";
 if(status==="FINAL" && (r.scores?.home?.total==null || r.scores?.away?.total==null || (sport==="BASKETBALL" && r.scores.home.total===r.scores.away.total))) status="SUSPENDED";
 const team=(t:Row)=>({id:p+"team:"+t.id,name:t.name,code:String(t.name).slice(0,3).toUpperCase(),logo:t.logo??null});
 const stats:Record<string,string>={};
 if(sport==="BASKETBALL") for(let i=1;i<=4;i++) stats[`Cuarto ${i}`]=`${r.scores?.home?.[`quarter_${i}`]??"—"} · ${r.scores?.away?.[`quarter_${i}`]??"—"}`;
 else {stats.Hits=`${r.scores?.home?.hits??"—"} · ${r.scores?.away?.hits??"—"}`;stats.Errores=`${r.scores?.home?.errors??"—"} · ${r.scores?.away?.errors??"—"}`;for(let i=1;i<=9;i++) stats[`Entrada ${i}`]=`${r.scores?.home?.innings?.[i]??"—"} · ${r.scores?.away?.innings?.[i]??"—"}`;}
 return {id:p+r.id,sport,home:team(r.teams.home),away:team(r.teams.away),leagueId:p+"league:"+r.league.id,league:r.league.name,startsAt:Date.parse(r.date),status,homeScore:r.scores?.home?.total??null,awayScore:r.scores?.away?.total??null,verified:status==="FINAL"||status==="VOID",source:"API_FOOTBALL",statistics:stats,events:[]};
}
export function normalizeRace(r:Row) {
 const results:Row[]=r.Results??[];const startsAt=Date.parse(r.date+"T"+(r.time??"00:00:00Z"));
 const complete=results.length>=2 && startsAt<Date.now()-4*60*60*1000 && new Set(results.map(x=>x.Driver.driverId)).size===results.length;
 const participants=results.map(x=>({id:x.Driver.driverId,name:x.Driver.givenName+" "+x.Driver.familyName,code:x.Driver.code??String(x.Driver.driverId).slice(0,3).toUpperCase()}));
 return {id:`f1:${r.season}:${r.round}`,sport:"FORMULA1",home:{id:"gp:"+r.round,name:r.raceName,code:"GP"},away:{id:r.Circuit.circuitId,name:r.Circuit.circuitName,code:"CIR"},leagueId:"f1:"+r.season,league:"Fórmula 1 · "+r.season,startsAt,status:complete?"FINAL":startsAt>Date.now()?"SCHEDULED":"SUSPENDED",verified:complete,source:"API_FOOTBALL",participants,raceResults:results.map(x=>({driverId:x.Driver.driverId,position:/^\d+$/.test(x.positionText)?Number(x.position):null,classified:/^\d+$/.test(x.positionText)})),classificationComplete:complete,events:[],statistics:{Circuito:r.Circuit.circuitName}};
}
export function normalizeOdds(rows:Row[],sport:Sport) {
 const found=new Map<string,Row>();
 for(const row of rows) for(const book of row.bookmakers??[]) for(const bet of book.bets??[]) {
  const money=/^(home\/away|moneyline|winner)( \(including ot\))?$/i.test(bet.name) && !(bet.values??[]).some((v:Row)=>v.value==="Draw");
  for(const value of bet.values??[]) {
   let market:string|undefined;let key:string|undefined;let label:string|undefined;
   if(money && ["Home","Away"].includes(value.value)) {market=sport==="BASKETBALL"?"BASKETBALL_WINNER":"BASEBALL_WINNER";key=value.value==="Home"?"1":"2";label=key==="1"?"Local":"Visitante";}
   const total=/^(Over|Under) (\d+\.5)$/.exec(value.value);
   if(bet.name==="Over/Under" && total && Number(total[2])===(sport==="BASKETBALL"?215.5:8.5)) {market=sport==="BASKETBALL"?"TOTAL_POINTS_2155":"TOTAL_RUNS_85";key=total[1]==="Over"?"OVER":"UNDER";label=(key==="OVER"?"Más de ":"Menos de ")+total[2];}
   const odds=Number(value.odd);if(!market || !key || !Number.isFinite(odds) || odds<=1 || odds>100) continue;
   const item={matchId:prefix(sport)+row.game.id,market,key,label,odds};const id=item.matchId+market+key;if(!found.has(id)) found.set(id,item);
  }
 }
 return [...found.values()];
}
export async function loadSport(admin:Admin,sport:Sport,body:Row) {
 let rows:Row[]=[];let stale=false;
 const persist=async(events:Row[],asof:string)=>{const result=await admin.rpc("ingest_sport_events",{p_rows:events,p_asof:asof});if(result.error) throw result.error;};
 if(sport==="FORMULA1") {
  if(body.action==="odds") return {count:0,stale:false,oddsAvailable:false};
  const year=String(body.id?.split(":")[1]??body.date?.slice(0,4)??new Date().getUTCFullYear());if(!/^20\d{2}$/.test(year)) throw Error("Invalid season");
  const paths=body.id?[`${year}/${String(body.id).split(":")[2]}/results.json?limit=100`]:[`${year}.json?limit=100`,`${year}/last/results.json?limit=100`];
  for(const path of paths) {if(!/^\d{4}\/(\d+\/results|last\/results)\.json\?limit=100$/.test(path) && !/^\d{4}\.json\?limit=100$/.test(path)) throw Error("Invalid race");const res=await cached(admin,sport,path);const events=res.data.MRData.RaceTable.Races.map(normalizeRace);await persist(events,res.asof);rows.push(...events);stale ||=res.stale;}
 } else {
  if(body.id && !new RegExp("^"+prefix(sport)+"[0-9]+$").test(body.id)) throw Error("Invalid event");
  if(body.action==="odds") {
   if(!body.id) throw Error("Missing event");const res=await cached(admin,sport,"odds?game="+body.id.split(":")[1]);
   const odds=res.stale?[]:normalizeOdds(res.data.response,sport);if(odds.length) {const saved=await admin.rpc("ingest_sport_odds",{p_rows:odds});if(saved.error) throw saved.error;}
   return {count:odds.length,stale:res.stale,oddsAvailable:odds.length>0};
  }
  if(!body.id && !/^\d{4}-\d{2}-\d{2}$/.test(body.date??"")) throw Error("Invalid date");
  const date=new Date(body.date??"2026-01-01");const next=new Date(date.getTime()+86400000).toISOString().slice(0,10);
  const paths=body.id?["games?id="+body.id.split(":")[1]]:["games?date="+body.date,"games?date="+next];
  for(const path of paths) {const res=await cached(admin,sport,path);const events=res.data.response.map((r:Row)=>normalizeGame(r,sport));await persist(events,res.asof);rows.push(...events);stale ||=res.stale;}
 }
 return {count:new Set(rows.map(r=>r.id)).size,stale};
}
