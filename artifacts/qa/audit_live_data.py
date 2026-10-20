"""Read-only source audit. Never prints or writes account details or credentials."""
from pathlib import Path
import urllib.request,urllib.parse,json,datetime,subprocess
from zoneinfo import ZoneInfo

root=Path(__file__).resolve().parents[2]
out=root/"artifacts/qa/data-audit"
out.mkdir(exist_ok=True)
key=next(line.split("=",1)[1].strip() for line in (root/"local.properties").read_text(encoding="utf-8-sig").splitlines() if line.startswith("FOOTBALL_API_KEY="))
adb=str(Path.home()/"AppData/Local/Android/Sdk/platform-tools/adb.exe")
def device(*args):return subprocess.check_output([adb,"-s","emulator-5554",*args]).decode(errors="replace").strip()
zone=device("shell","getprop","persist.sys.timezone")
now=datetime.datetime.now(datetime.timezone.utc)
today=now.astimezone(ZoneInfo(zone)).date()
report={"checked_utc":now.isoformat(),"watch_timezone":zone,"watch_clock":device("shell","date"),"local_date":str(today),"services":{}}
def fetch(host,path,auth=True):
 headers={"User-Agent":"MatchControl/1.0.1 validation"}
 if auth:headers["x-apisports-key"]=key
 try:
  with urllib.request.urlopen(urllib.request.Request("https://"+host+"/"+path,headers=headers),timeout=25) as r:
   return json.load(r)
 except Exception as e:return {"audit_error":type(e).__name__}
def summary(row,sport):
 if sport=="football":return {"id":row.get("fixture",{}).get("id"),"date":row.get("fixture",{}).get("date"),"status":row.get("fixture",{}).get("status"),"home":row.get("teams",{}).get("home",{}).get("name"),"away":row.get("teams",{}).get("away",{}).get("name"),"score":row.get("goals"),"competition":row.get("league",{}).get("name")}
 return {k:row.get(k) for k in ["id","date","status","scores"]}|{"home":row.get("teams",{}).get("home",{}).get("name"),"away":row.get("teams",{}).get("away",{}).get("name")}
for sport,host in [("football","v3.football.api-sports.io"),("basketball","v1.basketball.api-sports.io"),("baseball","v1.baseball.api-sports.io"),("formula1_subscription","v1.formula-1.api-sports.io")]:
 raw=fetch(host,"status")
 status=raw.get("response",{})
 entry={"status_errors":raw.get("errors",raw.get("audit_error")),"subscription":status.get("subscription"),"requests":status.get("requests")}
 report["services"][sport]=entry
 print(json.dumps({sport:entry},ensure_ascii=True),flush=True)
 if sport=="formula1_subscription":continue
 # Two dates suffice to inspect today's fixtures and published completed results.
 remaining=(status.get("requests",{}).get("limit_day",100)-status.get("requests",{}).get("current",100))
 if remaining<8:entry["skipped"]="Preserving remaining quota";continue
 entry["dates"]={}
 for date in [today,today-datetime.timedelta(days=1)]:
  path=("fixtures?"+urllib.parse.urlencode({"date":str(date),"timezone":zone})) if sport=="football" else "games?date="+str(date)
  raw=fetch(host,path)
  rows=raw.get("response",[])
  safe={"errors":raw.get("errors",raw.get("audit_error")),"count":len(rows),"samples":[summary(r,sport) for r in rows[:3]]}
  entry["dates"][str(date)]=safe
  (out/(sport+"-"+str(date)+".json")).write_text(json.dumps(raw,ensure_ascii=False,indent=2),encoding="utf-8")
  print(json.dumps({sport:str(date),"data":safe},ensure_ascii=True),flush=True)
  if sport=="football" and date==today:
   names=device("shell","run-as","com.matchcontrol","ls","files/api-cache").splitlines()
   cached=[]
   zone_hash=0
   for char in zone:zone_hash=(31*zone_hash+ord(char))&0xffffffff
   if zone_hash>=2**31:zone_hash-=2**32
   for name in names:
    if name=="day-"+str(today)+"-"+str(zone_hash)+".json":
     data=json.loads(device("exec-out","run-as","com.matchcontrol","cat","files/api-cache/"+name))
     cached.extend(data.get("response",[]))
   current={r.get("fixture",{}).get("id"):r for r in rows}
   entry["cached_comparison"]={"cached_count":len(cached),"same_fixture_ids":all(r.get("fixture",{}).get("id") in current for r in cached),"samples":[{"cached":summary(r,sport),"fresh":summary(current.get(r.get("fixture",{}).get("id"),{}),sport)} for r in cached[:3]]}
f1={}
for name,path in [("calendar",str(today.year)+".json?limit=100"),("latest",str(today.year)+"/last/results.json?limit=100")]:
 raw=fetch("api.jolpi.ca","ergast/f1/"+path,False)
 (out/("f1-"+name+".json")).write_text(json.dumps(raw,ensure_ascii=False,indent=2),encoding="utf-8")
 races=raw.get("MRData",{}).get("RaceTable",{}).get("Races",[])
 f1[name]={"count":len(races),"samples":[{"name":r.get("raceName"),"date":r.get("date"),"time":r.get("time"),"round":r.get("round"),"podium":[{"position":p.get("position"),"driver":p.get("Driver",{}).get("familyName"),"points":p.get("points")} for p in r.get("Results",[])[:3]]} for r in races if name=="latest" or r.get("date","")>=str(today)][:2]}
report["jolpica"]=f1
(out/"report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
print(json.dumps({"jolpica":f1},ensure_ascii=True),flush=True)
