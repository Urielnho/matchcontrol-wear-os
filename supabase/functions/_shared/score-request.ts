export type ScoreSport="FOOTBALL"|"BASKETBALL"|"BASEBALL"|"FORMULA1";
export type ScoreRequest={action:"events"|"details"|"odds";sport:ScoreSport;date:string;timezone:string;id?:string};
export function scoreRequest(raw:Record<string,unknown>,now=Date.now()):ScoreRequest {
 if(!["events","details","odds"].includes(String(raw.action)) || !["FOOTBALL","BASKETBALL","BASEBALL","FORMULA1"].includes(String(raw.sport))) throw Error("Unsupported query");
 const date=String(raw.date??new Date(now).toISOString().slice(0,10));const parsed=Date.parse(date+"T00:00:00Z");
 if(!/^\d{4}-\d{2}-\d{2}$/.test(date) || !Number.isFinite(parsed) || new Date(parsed).toISOString().slice(0,10)!==date || Math.abs(parsed-Date.parse(new Date(now).toISOString().slice(0,10)+"T00:00:00Z"))>7*86400000) throw Error("Unsupported date");
 const timezone=String(raw.timezone??"UTC");if(timezone.length>64) throw Error("Unsupported timezone");new Intl.DateTimeFormat("en",{timeZone:timezone});
 const sport=raw.sport as ScoreSport;const action=raw.action as ScoreRequest["action"];
 const id=raw.id===undefined?undefined:String(raw.id);
 const pattern=sport==="FOOTBALL"?/^api:\d+$/:sport==="BASKETBALL"?/^basket:\d+$/:sport==="BASEBALL"?/^base:\d+$/:/^f1:20\d{2}:\d{1,2}$/;
 if(id!==undefined && (id.length>64 || !pattern.test(id))) throw Error("Unsupported event");
 if(action!=="events" && !id) throw Error("Event required");
 return {action,sport,date,timezone,...(id?{id}:{})};
}
