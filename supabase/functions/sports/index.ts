import {withSupabase} from "npm:@supabase/server@1.9.2";
import {loadSport,type Sport} from "../_shared/sports.ts";
Deno.serve(withSupabase({auth:"user"},async(req:Request,ctx)=>{
 if(req.method!=="POST") return Response.json({error:"POST required"},{status:405});
 try {
  const body=await req.json();if(!["BASKETBALL","BASEBALL","FORMULA1"].includes(body.sport) || !["events","odds"].includes(body.action)) return Response.json({error:"Invalid sport"},{status:400});
  return Response.json({source:"API_FOOTBALL",...await loadSport(ctx.supabaseAdmin,body.sport as Sport,body)});
 } catch {return Response.json({error:"Results unavailable"},{status:502});}
}));
