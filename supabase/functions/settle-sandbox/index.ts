import { withSupabase } from "npm:@supabase/server@1.9.2";
import {loadSport,type Sport} from "../_shared/sports.ts";

// Private worker: this credential is never present in Android artifacts.
Deno.serve(withSupabase({auth:"secret"},async(req: Request,ctx)=>{
 if(req.method!=="POST") return Response.json({error:"POST required"},{status:405});
 const client=ctx.supabaseAdmin;
 try {
  const body=await req.json();
  if(body.installScheduler===true) {
   const {error}=await client.rpc("store_refresh_key",{p_key:req.headers.get("apikey"),p_url:Deno.env.get("SUPABASE_URL")});if(error) throw error;
   return Response.json({configured:true});
  }
  if(body.refresh===true) {
   const dates=await client.rpc("pending_football_dates");if(dates.error) throw dates.error;
   for(const date of dates.data ?? []) {
    const params=new URLSearchParams({date,timezone:"America/Phoenix"});params.sort();const cacheKey="fixtures?"+params;
    const cached=await client.rpc("sports_cache_read",{p_key:cacheKey});if(cached.error) throw cached.error;
    let payload=cached.data?.payload;let updated=cached.data?.updated_at;
    if(!payload || Date.now()-Date.parse(updated)>15*60*1000) {
     const key=Deno.env.get("FOOTBALL_API_KEY");if(!key) throw new Error("Provider unavailable");
     const budget=await client.rpc("reserve_provider_request",{p_day:new Date().toISOString().slice(0,10)});if(budget.error) throw budget.error;
     if(!budget.data) continue;
     const response=await fetch("https://v3.football.api-sports.io/"+cacheKey,{headers:{"x-apisports-key":key},signal:AbortSignal.timeout(20000)});
     const data=await response.json();if(!response.ok || Object.keys(data.errors ?? {}).length>0 || !Array.isArray(data.response)) continue;
     payload=data;updated=new Date().toISOString();const save=await client.rpc("sports_cache_write",{p_key:cacheKey,p_payload:data});if(save.error) throw save.error;
    }
    const ingest=await client.rpc("ingest_football",{p_rows:payload.response,p_asof:updated});if(ingest.error) throw ingest.error;
   }
  }
  if(body.matchId) {
   const {error}=await client.rpc("finish_sandbox_fixture",{p_match:body.matchId,p_home:body.home,p_away:body.away,p_void:body.void===true});if(error) throw error;
  }
  // Re-runnable reconciliation: a worker interruption never loses a settlement.
  const pending=await client.from("bets").select("id").eq("status","PENDING").limit(200);
  if(pending.error) throw pending.error;
  if(body.refresh===true && pending.data?.length) {
   const selections=await client.from("bet_selections").select("matches!inner(id,sport,start_time,source)").in("bet_id",pending.data.map(b=>b.id)).eq("matches.source","API_FOOTBALL").neq("matches.sport","FOOTBALL");
   if(selections.error) throw selections.error;
   const seen=new Set<string>();
   for(const selection of selections.data??[]) {
    const event=selection.matches as unknown as {id:string;sport:Sport;start_time:string};
    if(!event || seen.has(event.id) || Date.parse(event.start_time)>Date.now() || seen.size>=10) continue;
    seen.add(event.id);await loadSport(client,event.sport,{id:event.id,action:"events"});
   }
  }
  for(const bet of pending.data ?? []) {const {error}=await client.rpc("settle_demo_bet",{p_bet:bet.id});if(error) throw error;}
  return Response.json({processed:pending.data?.length ?? 0});
 } catch {return Response.json({error:"Worker operation rejected"},{status:409});}
}));
