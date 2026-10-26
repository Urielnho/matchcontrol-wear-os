import { withSupabase } from "npm:@supabase/server@1.9.2";

// Shared backend cache: clients never call API-Football directly in v2.
Deno.serve(withSupabase({auth:"user"},async(req: Request,ctx)=>{
 if(req.method!=="POST") return Response.json({error:"POST required"},{status:405});
 const admin=ctx.supabaseAdmin;
 const apiKey=Deno.env.get("FOOTBALL_API_KEY");
 if(!apiKey) return Response.json({error:"Sports provider not configured",source:"UNAVAILABLE"},{status:503});
 try {
  const body=await req.json();
  const allowed:Record<string,string[]>={fixtures:["date","timezone","id","league","season"],leagues:["id","season","search"],teams:["id","league","season","search"],"fixtures/events":["fixture"],"fixtures/statistics":["fixture"],odds:["fixture","date","league","season"]};
  if(!allowed[body.endpoint]) return Response.json({error:"Endpoint not allowed"},{status:400});
  const query=new URLSearchParams();
  for(const key of allowed[body.endpoint]) if(body.params?.[key]!==undefined) query.set(key,String(body.params[key]).slice(0,100));
  query.sort();const cacheKey=body.endpoint+"?"+query;
  async function persist(data: {response: unknown[]},asof: string) {
   const rpc=body.endpoint==="fixtures"?"ingest_football":body.endpoint==="odds"?"ingest_football_odds":null;
   if(rpc) {const {error}=await admin.rpc(rpc,{p_rows:data.response,...(rpc==="ingest_football"?{p_asof:asof}:{})});if(error) throw error;}
  }
  const cached=await admin.rpc("sports_cache_read",{p_key:cacheKey});
  if(cached.error) throw cached.error;
  if(cached.data && Date.now()-Date.parse(cached.data.updated_at)<15*60*1000) {await persist(cached.data.payload,cached.data.updated_at);return Response.json({source:"API_FOOTBALL",stale:false,updatedAt:cached.data.updated_at,data:cached.data.payload});}
  const budget=await admin.rpc("reserve_provider_request",{p_day:new Date().toISOString().slice(0,10)});
  if(budget.error) throw budget.error;
  if(!budget.data) {
   if(cached.data) return Response.json({source:"API_FOOTBALL",stale:true,updatedAt:cached.data.updated_at,data:cached.data.payload});
   return Response.json({error:"Daily sports quota reached"},{status:429});
  }
  const response=await fetch("https://v3.football.api-sports.io/"+cacheKey,{headers:{"x-apisports-key":apiKey},signal:AbortSignal.timeout(20000)});
  const data=await response.json();
  if(!response.ok || Object.keys(data.errors ?? {}).length>0 || !Array.isArray(data.response)) {
   if(cached.data) return Response.json({source:"API_FOOTBALL",stale:true,updatedAt:cached.data.updated_at,data:cached.data.payload});
   return Response.json({error:"No coverage or provider unavailable",source:"UNAVAILABLE"},{status:502});
  }
  const save=await admin.rpc("sports_cache_write",{p_key:cacheKey,p_payload:data});if(save.error) throw save.error;
  await persist(data,new Date().toISOString());
  return Response.json({source:"API_FOOTBALL",stale:false,updatedAt:new Date().toISOString(),data});
 } catch {return Response.json({error:"Sports query unavailable"},{status:502});}
}));
