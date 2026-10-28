import assert from "node:assert/strict";
import {scoreRequest} from "../supabase/functions/_shared/score-request.ts";
import {loadScores,ScoreUnavailable} from "../supabase/functions/_shared/scores.ts";
const now=Date.parse("2026-10-28T22:00:00Z");
const base={action:"events",sport:"FOOTBALL",date:"2026-10-28",timezone:"America/Phoenix"};
Deno.test("feed accepts all four canonical sports and bounded dates",()=>{
 for(const sport of ["FOOTBALL","BASKETBALL","BASEBALL","FORMULA1"]) assert.equal(scoreRequest({...base,sport},now).sport,sport);
 for(const date of ["2026-10-21","2026-11-04"]) assert.equal(scoreRequest({...base,date},now).date,date);
});
Deno.test("feed rejects unsupported ranges, invalid dates, actions and timezones",()=>{
 for(const body of [{...base,date:"2026-10-20"},{...base,date:"2026-11-05"},{...base,date:"2026-02-30"},{...base,action:"demoResult"},{...base,timezone:"Not/AZone"},{...base,sport:"OTHER"}]) assert.throws(()=>scoreRequest(body,now));
});
Deno.test("fictional IDs cannot request provider odds",()=>{
 for(const id of ["910001","demo:football:1","basket:fake","https://other.test"]) assert.throws(()=>scoreRequest({...base,action:"odds",id},now));
 assert.throws(()=>scoreRequest({...base,action:"odds"},now));
});
Deno.test("unknown canonical event is rejected before budget or upstream request",async()=>{
 let calls=0;
 const chain:any={select(){return this},eq(){return this},is(){return this},maybeSingle(){return Promise.resolve({data:null,error:null})}};
 const admin={from(){return chain},rpc(){calls++;throw Error("Unexpected provider/cache request")}};
 await assert.rejects(()=>loadScores(admin,scoreRequest({...base,action:"odds",id:"api:987654321"},now)),ScoreUnavailable);
 assert.equal(calls,0);
});
Deno.test("out-of-window known event is rejected before provider request",async()=>{
 let calls=0;
 const chain:any={select(){return this},eq(){return this},is(){return this},maybeSingle(){return Promise.resolve({data:{id:"api:1",start_time:"2000-01-01T00:00:00Z"},error:null})}};
 const admin={from(){return chain},rpc(){calls++;throw Error("Unexpected provider request")}};
 await assert.rejects(()=>loadScores(admin,scoreRequest({...base,action:"details",id:"api:1"},now)),ScoreUnavailable);
 assert.equal(calls,0);
});

Deno.test("known current-season F1 races remain available beyond eight days without invented odds",async()=>{
 const chain:any={select(){return this},eq(){return this},is(){return this},maybeSingle(){return Promise.resolve({data:{id:"f1:2026:23",start_time:"2026-12-06T13:00:00Z"},error:null})}};
 const admin={from(){return chain},rpc(name:string){assert.equal(name,"public_scores_snapshot");return Promise.resolve({data:{fixtures:[{id:"f1:2026:23",source:"API_FOOTBALL"}],outcomes:[]},error:null})}};
 const s=await loadScores(admin,scoreRequest({...base,action:"odds",sport:"FORMULA1",id:"f1:2026:23"},now));
 assert.equal(s.fixtures[0].id,"f1:2026:23");assert.equal(s.outcomes.length,0);
});
