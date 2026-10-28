"""Hosted multi-sport integration check with a temporary QA account, no secret output."""
from pathlib import Path
import importlib.util,json,uuid
spec=importlib.util.spec_from_file_location("live_qa",Path(__file__).with_name("live-smoke.py"))
qa=importlib.util.module_from_spec(spec);spec.loader.exec_module(qa)
checks=[]
def check(label,ok):
 if not ok:raise AssertionError(label)
 checks.append(label);print("PASS "+label,flush=True)
coverage={}
for sport in ["BASKETBALL","BASEBALL","FORMULA1"]:
 status,data=qa.http("/functions/v1/sports",{"action":"events","sport":sport,"date":qa.today},qa.token)
 check("hosted real "+sport,status==200 and data.get("count",0)>0)
 coverage[sport]=data.get("count")
status,state=qa.http("/functions/v1/sandbox",{"action":"mode","source":"DEMO"},qa.token)
check("authenticated demonstration is seeded",status==200)
events={f["sport"]:f for f in state["fixtures"] if f["id"].startswith("demo:"+qa.qa["user_id"]+":")}
check("four educational sports available",len(events)==4)
markets={"FOOTBALL":"WINNER","BASKETBALL":"BASKETBALL_WINNER","BASEBALL":"BASEBALL_WINNER","FORMULA1":"RACE_WINNER"}
offers=[]
for sport,event in events.items():
 key="demo-driver-0" if sport=="FORMULA1" else "1"
 offers.append(next(o for o in state["outcomes"] if o["matchId"]==event["id"] and o["market"]==markets[sport] and o["key"]==key))
for index,selection in enumerate([[o] for o in offers]+[offers]):
 body={"action":"place","requestId":str(uuid.uuid4()),"stake":10000,"outcomes":{o["id"]:o["odds"] for o in selection}}
 status,result=qa.http("/functions/v1/sandbox",body,qa.token)
 check("places sport ticket "+str(index),status==200)
 balance=result["snapshot"]["balance"]
 status,retry=qa.http("/functions/v1/sandbox",body,qa.token)
 check("retry keeps single debit "+str(index),status==200 and retry["betId"]==result["betId"] and retry["snapshot"]["balance"]==balance)
for sport,event in events.items():
 body={"action":"demoResult","matchId":event["id"]}
 if sport=="FORMULA1":body["order"]=[d["id"] for d in event["participants"]]
 else:body.update({"home":112 if sport=="BASKETBALL" else 2,"away":105 if sport=="BASKETBALL" else 1})
 status,state=qa.http("/functions/v1/sandbox",body,qa.token)
 check("settles "+sport,status==200)
check("all simple and mixed tickets win",len(state["bets"])==5 and all(b["status"]=="WON" for b in state["bets"]))
check("exact wallet across four sports",state["balance"]==1166795)
combo=next(b for b in state["bets"] if len(b["picks"])==4)
check("mixed parlay exact return",combo["returned"]==139295)
# Leave fresh future events for the Android UI check, then restore real mode.
qa.http("/functions/v1/sandbox",{"action":"demoRound","requestId":str(uuid.uuid4())},qa.token)
qa.http("/functions/v1/sandbox",{"action":"mode","source":"API_FOOTBALL"},qa.token)
report={"passed":len(checks),"checks":checks,"real_coverage":coverage,"secret_exposed":False}
(qa.dest/"multisport-live.json").write_text(json.dumps(report,indent=2))
print(json.dumps({"passed":len(checks),"coverage":coverage}),flush=True)
