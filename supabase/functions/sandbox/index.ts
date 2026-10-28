import { withSupabase } from "npm:@supabase/server@1.9.2";

// No results or odds supplied by the caller are trusted. Only IDs/expected odds.
Deno.serve(withSupabase({auth:"user"},async (req: Request,ctx) => {
  if (req.method !== "POST") return Response.json({error:"POST required"},{status:405});
  const admin=ctx.supabaseAdmin;
  try {
    const body=await req.json();
    const uid=ctx.jwtClaims?.sub;
    if(!uid) return Response.json({error:"Invalid session"},{status:401});
    const ensure=await admin.rpc("ensure_demo_user",{p_user:uid});
    if(ensure.error) throw ensure.error;
    const reconcile=await admin.rpc("reconcile_user_bets",{p_user:uid});if(reconcile.error) throw reconcile.error;
    if(body.action==="place") {
      if(!Number.isSafeInteger(body.stake) || body.stake<100 || !body.requestId || typeof body.outcomes!=="object") throw new Error("Invalid bet");
      const cents=BigInt(body.stake);
      const decimalStake=`${cents/100n}.${(cents%100n).toString().padStart(2,"0")}`;
      const {data,error}=await admin.rpc("place_demo_bet",{p_user:uid,p_request:body.requestId,p_stake:decimalStake,p_selections:body.outcomes});
      if(error) throw error;
      const snapshot=await ctx.supabase.rpc("sandbox_snapshot",{p_user:uid});
      if(snapshot.error) throw snapshot.error;
      return Response.json({betId:data,snapshot:snapshot.data});
    }
    if(body.action==="reset") {
      const {error}=await admin.rpc("reset_demo_wallet",{p_user:uid,p_request:body.requestId});if(error) throw error;
    } else if(body.action==="favorite") {
      const tables:Record<string,string>={team:"favorite_teams",league:"favorite_leagues",match:"favorite_matches"};
      const columns:Record<string,string>={team:"team_id",league:"league_id",match:"match_id"};
      if(!tables[body.kind] || typeof body.target!=="string" || body.target.length<1 || body.target.length>100 || typeof body.enabled!=="boolean") throw new Error("Invalid favorite");
      const table=tables[body.kind], column=columns[body.kind];
      const result=body.enabled?await ctx.supabase.from(table).upsert({user_id:uid,[column]:body.target},{onConflict:`user_id,${column}`,ignoreDuplicates:true}):await ctx.supabase.from(table).delete().eq("user_id",uid).eq(column,body.target);
      if(result.error) throw result.error;
    } else if(body.action==="notifications") {
      if(typeof body.enabled!=="boolean") throw new Error("Invalid preference");
      const {error}=await ctx.supabase.from("notification_preferences").update({enabled:body.enabled}).eq("user_id",uid);if(error) throw error;
    } else if(body.action==="demoRound") {
      const {error}=await admin.rpc("create_multisport_demo",{p_user:uid,p_request:body.requestId});if(error) throw error;
    } else if(body.action==="demoResult") {
      const {error}=await admin.rpc("finish_user_demo",{p_user:uid,p_match:body.matchId,p_home:body.home??null,p_away:body.away??null,p_order:body.order??null,p_void:body.void===true});if(error) throw error;
    } else if(body.action==="mode") {
      if(!["DEMO","API_FOOTBALL"].includes(body.source)) throw new Error("Invalid mode");
      const {error}=await ctx.supabase.from("profiles").update({data_mode:body.source}).eq("id",uid);if(error) throw error;
      if(body.source==="DEMO") {
        const existing=await ctx.supabase.from("matches").select("id").eq("demo_owner",uid).eq("status","SCHEDULED").gt("start_time",new Date().toISOString()).limit(1);
        if(existing.error) throw existing.error;
        if(!existing.data?.length) {const created=await admin.rpc("create_multisport_demo",{p_user:uid,p_request:crypto.randomUUID()});if(created.error) throw created.error;}
      }
    } else if(body.action!=="snapshot") throw new Error("Action not allowed");
    const {data,error}=await ctx.supabase.rpc("sandbox_snapshot",{p_user:uid});if(error) throw error;
    return Response.json(data);
  } catch {
    // Deliberately omit raw SQL/errors/account information from public responses.
    return Response.json({error:"Operation rejected. Refresh the balance and markets before retrying."},{status:409});
  }
}));
