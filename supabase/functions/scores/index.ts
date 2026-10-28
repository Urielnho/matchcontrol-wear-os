import {withSupabase} from "npm:@supabase/server@1.9.2";
import {scoreRequest} from "../_shared/score-request.ts";
import {loadScores,ScoreUnavailable} from "../_shared/scores.ts";

// Publishable-key access exposes only this read-only sports feed, never account operations.
Deno.serve(withSupabase({auth:"publishable"},async(req:Request,ctx)=>{
 if(req.method!=="POST") return Response.json({error:"POST required"},{status:405});
 let body;
 try {body=scoreRequest(await req.json());} catch {return Response.json({error:"Unsupported sports query"},{status:400});}
 try {return Response.json(await loadScores(ctx.supabaseAdmin,body),{headers:{"Cache-Control":"public, max-age=30"}});}
 catch(error) {return Response.json({error:error instanceof ScoreUnavailable?"Event unavailable":"Results unavailable"},{status:error instanceof ScoreUnavailable?404:502});}
}));
