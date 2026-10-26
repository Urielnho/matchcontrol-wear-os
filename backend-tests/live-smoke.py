"""Private backend smoke test. Secrets stay in process memory, never in output/APKs."""
import json,subprocess,urllib.request,urllib.error,uuid,secrets,datetime,pathlib,sys
from zoneinfo import ZoneInfo
root=pathlib.Path(__file__).resolve().parents[1]
project="vxgvhptsxjjixhaldvor";url="https://"+project+".supabase.co"
cli=str(pathlib.Path.home()/"AppData/Roaming/npm/supabase.cmd")
result=subprocess.run([cli,"projects","api-keys","--project-ref",project,"--reveal","--output","json"],capture_output=True,text=True)
if result.returncode:raise RuntimeError("Cannot obtain backend test credentials from the authenticated CLI")
raw=json.loads(result.stdout);keys=raw if isinstance(raw,list) else raw.get("keys",raw.get("api_keys",[]))
public=next(x["api_key"] for x in keys if x.get("type")=="publishable" and not x.get("disabled"))
secret=next((x["api_key"] for x in keys if x.get("type")=="secret" and not x.get("disabled")),None)
if not secret:raise RuntimeError("No server secret available for backend-only tests")
def http(path,body=None,token=None,admin=False,method=None):
 headers={"apikey":secret if admin else public,"Content-Type":"application/json"}
 if token:headers["Authorization"]="Bearer "+token
 elif admin:headers["Authorization"]="Bearer "+secret
 request=urllib.request.Request(url+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
 try:
  with urllib.request.urlopen(request,timeout=35) as r:
   payload=r.read();return r.status,json.loads(payload) if payload else {}
 except urllib.error.HTTPError as e:
  try:data=json.load(e)
  except Exception:data={"error":"HTTP error"}
  return e.code,data
cache=root/".tool-cache/live-qa.json"
if cache.exists():qa=json.loads(cache.read_text())
else:
 email="matchcontrol-qa-"+uuid.uuid4().hex[:12]+"@example.invalid";password=secrets.token_urlsafe(24)
 code,user=http("/auth/v1/admin/users",{"email":email,"password":password,"email_confirm":True},admin=True)
 if code!=200:raise RuntimeError("QA user creation rejected: "+str(code))
 qa={"email":email,"password":password,"user_id":user["id"]};cache.parent.mkdir(exist_ok=True);cache.write_text(json.dumps(qa))
code,session=http("/auth/v1/token?grant_type=password",{"email":qa["email"],"password":qa["password"]})
if code!=200:raise RuntimeError("QA sign-in rejected: "+str(code))
token=session["access_token"]
report={"sign_in":code}
code,data=http("/functions/v1/sandbox",{"action":"snapshot"},token)
report["snapshot"]={"status":code,"balance":data.get("balance"),"fixture_count":len(data.get("fixtures",[])),"error":data.get("error",data.get("message"))}
print(json.dumps(report),flush=True)
if code!=200:raise SystemExit(1)
today=datetime.datetime.now(ZoneInfo("America/Phoenix")).date().isoformat()
code,data=http("/functions/v1/football",{"endpoint":"fixtures","params":{"date":today,"timezone":"America/Phoenix"}},token)
report["football"]={"status":code,"count":len(data.get("data",{}).get("response",[])),"stale":data.get("stale"),"error":data.get("error",data.get("message"))}
code,state=http("/functions/v1/sandbox",{"action":"snapshot"},token)
report["normalized"]={"status":code,"count":len(state.get("fixtures",[])),"source":state.get("source")}
for name,body in [("unauthenticated",None),("invalid_token","invalid-token")]:
 status,_=http("/functions/v1/sandbox",{"action":"snapshot"},body);report[name]=status
dest=root/"artifacts/qa/v2";dest.mkdir(parents=True,exist_ok=True)
(dest/"live-backend.json").write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2),flush=True)
if "--scheduler" in sys.argv:
 status,_=http("/functions/v1/settle-sandbox",{"installScheduler":True},admin=True)
 if status!=200:raise RuntimeError("Scheduler credential setup rejected: "+str(status))
 status,result=http("/functions/v1/settle-sandbox",{"refresh":True},admin=True)
 if status!=200:raise RuntimeError("Scheduled worker refresh rejected: "+str(status))
 (dest/"scheduler-smoke.json").write_text(json.dumps({"install_status":200,"refresh_status":status,"processed":result.get("processed"),"secret_exposed":False},indent=2))
 print("PASS private scheduled refresh",flush=True)
if "--financial" in sys.argv:
 checks=[]
 def check(name,condition):
  if not condition:raise AssertionError(name)
  checks.append(name);print("PASS "+name,flush=True)
 code,_=http("/functions/v1/sandbox",{"action":"mode","source":"DEMO"},token);check("switches to identified sandbox",code==200)
 if "fixtures" not in qa:
  qa["fixtures"]=[];qa["outcomes"]=[]
  for i in range(3):
   ident="qa:"+uuid.uuid4().hex;market=str(uuid.uuid4());outcome=str(uuid.uuid4())
   match={"id":ident,"home_team":{"id":"qa-home","name":"Aurora QA","code":"AUR"},"away_team":{"id":"qa-away","name":"Horizonte QA","code":"HOR"},"league_id":"qa","league_name":"Laboratorio DEMO","status":"SCHEDULED","source":"DEMO","start_time":(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=1)).isoformat()}
   code,_=http("/rest/v1/matches",match,admin=True);check("creates identified QA event "+str(i),code==201)
   code,_=http("/rest/v1/markets",{"id":market,"match_id":ident,"market_type":"WINNER"},admin=True);check("creates QA market "+str(i),code==201)
   code,_=http("/rest/v1/market_outcomes",{"id":outcome,"market_id":market,"outcome_key":"1","label":"Local","decimal_odds":"1.8500"},admin=True);check("creates fictitious QA odds "+str(i),code==201)
   qa["fixtures"].append(ident);qa["outcomes"].append(outcome)
  qa["requests"]=[str(uuid.uuid4()) for _ in range(3)];cache.write_text(json.dumps(qa))
 for i,selection in enumerate([[0],[0,1],[2]]):
  body={"action":"place","requestId":qa["requests"][i],"stake":10000,"outcomes":{qa["outcomes"][n]:18500 for n in selection}}
  code,bet=http("/functions/v1/sandbox",body,token);check("confirms cloud ticket "+str(i),code==200)
  before=bet["snapshot"]["balance"]
  code,repeated=http("/functions/v1/sandbox",body,token);check("cloud retry does not debit twice "+str(i),code==200 and repeated["snapshot"]["balance"]==before and bet["betId"]==repeated["betId"])
 for i,(home,away,void) in enumerate([(2,1,False),(0,0,True),(0,1,False)]):
  code,data=http("/functions/v1/settle-sandbox",{"matchId":qa["fixtures"][i],"home":home,"away":away,"void":void},admin=True);check("private worker settles "+str(i),code==200)
 code,state=http("/functions/v1/sandbox",{"action":"snapshot"},token)
 states=sorted(b["status"] for b in state["bets"])
 check("simple win, combined void adjustment and loss",states==["LOST","WON","WON"])
 check("server computes exact final credit balance",state["balance"]==1007000)
 code,_=http("/functions/v1/settle-sandbox",{"matchId":qa["fixtures"][0],"home":2,"away":1},admin=True)
 code,repeat=http("/functions/v1/sandbox",{"action":"snapshot"},token);check("worker retry does not credit twice",repeat["balance"]==state["balance"])
 code,_=http("/rest/v1/wallets?user_id=eq."+qa["user_id"],{"balance":999999},token,method="PATCH");check("authenticated client cannot edit balance",code in (401,403))
 reset={"action":"reset","requestId":str(uuid.uuid4())}
 code,state=http("/functions/v1/sandbox",reset,token);check("explicit cloud reset",code==200 and state["balance"]==1000000)
 code,again=http("/functions/v1/sandbox",reset,token);check("cloud reset retry idempotent",code==200 and len(again["transactions"])==len(state["transactions"]))
 http("/functions/v1/sandbox",{"action":"mode","source":"API_FOOTBALL"},token)
 (dest/"live-financial.json").write_text(json.dumps({"passed":len(checks),"checks":checks,"secret_exposed":False},indent=2))
