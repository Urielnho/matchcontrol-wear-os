import {loadFootball} from "./football.ts";
import {loadSport,type Sport} from "./sports.ts";
import type {ScoreRequest} from "./score-request.ts";
export class ScoreUnavailable extends Error {}
export async function loadScores(admin:any,body:ScoreRequest) {
  if(body.id) {
   const known=await admin.from("matches").select("id,start_time").eq("id",body.id).eq("sport",body.sport).eq("source","API_FOOTBALL").is("demo_owner",null).maybeSingle();
   if(known.error) throw known.error;
   if(!known.data || (body.sport==="FORMULA1"?body.id.split(":")[1]!==body.date.slice(0,4):Math.abs(Date.parse(known.data.start_time)-Date.now())>8*86400000)) throw new ScoreUnavailable();
  }
  let stale=false;
  try {
   if(body.sport==="FOOTBALL") {
    const result=await loadFootball(admin,{endpoint:body.action==="odds"?"odds":"fixtures",params:body.id?(body.action==="odds"?{fixture:body.id.slice(4)}:{id:body.id.slice(4)}):{date:body.date,timezone:body.timezone}});stale=result.stale;
   } else {const result=await loadSport(admin,body.sport as Sport,{...body,action:body.action==="odds"?"odds":"events"});stale=result.stale;}
  } catch {stale=true;}
  const snapshot=await admin.rpc("public_scores_snapshot",{p_sport:body.sport,p_date:body.date,p_timezone:body.timezone,p_id:body.id??null});if(snapshot.error) throw snapshot.error;
 return {...snapshot.data,stale};
}
