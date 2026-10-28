import {withSupabase} from "npm:@supabase/server@1.9.2";
import {loadFootball} from "../_shared/football.ts";
Deno.serve(withSupabase({auth:"user"},async(req:Request,ctx)=>{
 if(req.method!=="POST") return Response.json({error:"POST required"},{status:405});
 try {return Response.json(await loadFootball(ctx.supabaseAdmin,await req.json()));}
 catch {return Response.json({error:"Results unavailable"},{status:502});}
}));
