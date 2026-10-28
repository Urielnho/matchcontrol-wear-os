type Admin={rpc(name:string,args?:Record<string,unknown>):PromiseLike<{data:any;error:any}>};
export async function loadFootball(admin:Admin,body:Record<string,any>) {
 const allowed:Record<string,string[]>={fixtures:["date","timezone","id","league","season"],leagues:["id","season","search"],teams:["id","league","season","search"],"fixtures/events":["fixture"],"fixtures/statistics":["fixture"],odds:["fixture","date","league","season"]};
 if(!allowed[body.endpoint]) throw Error("Endpoint not allowed");
 const query=new URLSearchParams();
 for(const key of allowed[body.endpoint]) if(body.params?.[key]!==undefined) query.set(key,String(body.params[key]).slice(0,100));
 query.sort();const cacheKey=body.endpoint+"?"+query;
 const saved=await admin.rpc("sports_cache_read",{p_key:cacheKey});if(saved.error) throw saved.error;
 async function persist(data:{response:unknown[]},asof:string) {
  const rpc=body.endpoint==="fixtures"?"ingest_football":body.endpoint==="odds"?"ingest_football_odds":null;
  if(rpc) {const result=await admin.rpc(rpc,{p_rows:data.response,...(rpc==="ingest_football"?{p_asof:asof}:{})});if(result.error) throw result.error;
   if(body.endpoint==="odds") for(const row of data.response as any[]) {const stamped=await admin.rpc("provider_quote_time",{p_match:"api:"+row.fixture.id,p_asof:asof});if(stamped.error) throw stamped.error;}
  }
 }
 if(saved.data && Date.now()-Date.parse(saved.data.updated_at)<15*60*1000) {await persist(saved.data.payload,saved.data.updated_at);return {source:"API_FOOTBALL",stale:false,updatedAt:saved.data.updated_at,data:saved.data.payload};}
 try {
  const apiKey=Deno.env.get("FOOTBALL_API_KEY");if(!apiKey) throw Error("Provider unavailable");
  const budget=await admin.rpc("reserve_provider_request",{p_day:new Date().toISOString().slice(0,10)});if(budget.error || !budget.data) throw Error("Quota reached");
  const response=await fetch("https://v3.football.api-sports.io/"+cacheKey,{headers:{"x-apisports-key":apiKey},signal:AbortSignal.timeout(20000)});
  const data=await response.json();if(!response.ok || Object.keys(data.errors??{}).length || !Array.isArray(data.response)) throw Error("No coverage");
  const save=await admin.rpc("sports_cache_write",{p_key:cacheKey,p_payload:data});if(save.error) throw save.error;
  const asof=new Date().toISOString();await persist(data,asof);return {source:"API_FOOTBALL",stale:false,updatedAt:asof,data};
 } catch(error) {
  // Stale quotes are never re-ingested as fresh. Fixtures retain their original as-of.
  if(saved.data) {if(body.endpoint==="fixtures") await persist(saved.data.payload,saved.data.updated_at);return {source:"API_FOOTBALL",stale:true,updatedAt:saved.data.updated_at,data:saved.data.payload};}
  throw error;
 }
}
