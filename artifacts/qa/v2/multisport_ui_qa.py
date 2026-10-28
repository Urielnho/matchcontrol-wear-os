"""Exercise four-sport betting and actual paired Wear delivery using a QA account."""
import sys,time,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import mobile_ui_qa as m
import mirror_transport_qa as transport
q=m.q
def choose(label):
 for attempt in range(5):
  point=q.find(label)
  if point:q.adb("shell","input","tap",*point);time.sleep(2);return
  root=q.dump();anchor=next((n for n in root.iter("node") if n.get("text") in ["Todos","Fútbol","Baloncesto","Béisbol","F1"]),None)
  if anchor is None:raise RuntimeError("Sport selector missing")
  _,y1,_,y2=q.bounds(anchor);w,_=q.size();right=label in ["Todos","Fútbol"]
  q.adb("shell","input","swipe",int(w*(.2 if right else .85)),(y1+y2)//2,int(w*(.85 if right else .2)),(y1+y2)//2,350)
 raise RuntimeError("Sport chip unavailable: "+label)
def open_card():
 for _ in range(6):
  for node in q.dump().iter("node"):
   label=node.get("content-desc","")
   if " contra " in label or label.startswith("Carrera: "):
    x1,y1,x2,y2=q.bounds(node)
    if 150<(y1+y2)//2<q.size()[1]*.85:q.adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);time.sleep(1);return
  q.swipe()
 raise RuntimeError("No sport event available")
m.launch();q.tap("Perfil")
credentials=json.loads((transport.ROOT/".tool-cache/live-qa.json").read_text())
fields=[n for n in q.dump().iter("node") if n.get("class")=="android.widget.EditText"]
if len(fields)==2:
 for field,text in zip(fields,[credentials["email"],credentials["password"]]):
  x1,y1,x2,y2=q.bounds(field);q.adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);q.adb("shell","input","text",text)
 q.adb("shell","input","keyevent",4);q.tap("Entrar");time.sleep(3)
elif transport.read("emulator-5556",True)["profile"]["id"]!=credentials["user_id"]:raise RuntimeError("Unexpected account; refusing to replace it")
print("PASS QA account opened",flush=True)
q.tap("Inicio")
for name,slug in [("Baloncesto","basketball"),("Béisbol","baseball"),("F1","f1")]:
 choose(name);q.shot("premium-real-"+slug);print("PASS real "+slug+" screen",flush=True)
q.tap("Perfil");q.seek("Demostración");q.tap("Demostración");time.sleep(2)
q.tap("Partidos");q.tap("Próximos")
for index,(name,slug) in enumerate([("Fútbol","football"),("Baloncesto","basketball"),("Béisbol","baseball"),("F1","f1")]):
 choose(name);open_card();q.seek("Elegir ",True);q.tap("Elegir ",True);q.shot("premium-demo-"+slug)
 q.tap("Abrir boleto")
 if index<3:q.seek("Agregar otro partido",True);q.tap("Agregar otro partido",True);q.tap("Próximos")
 print("PASS selects "+slug,flush=True)
q.top(3);q.shot("premium-four-sport-parlay")
q.seek("Confirmar boleto DEMO");q.tap("Confirmar boleto DEMO");q.tap("Confirmar");time.sleep(1)
report=transport.wait(cloud=True)
report["mixed_sport_ticket_received"]=any(len(b["picks"])==4 and b["status"]=="PENDING" for b in transport.read("emulator-5558")["bets"])
if not report["mixed_sport_ticket_received"]:raise RuntimeError("Mixed ticket did not reach watch")
q.shot("premium-four-sport-confirmed")
q.tap("Favoritos");q.tap("Elegir equipos y ligas")
root=q.dump();switch=next(n for n in root.iter("node") if n.get("checkable")=="true" and n.get("checked")=="false")
x1,y1,x2,y2=q.bounds(switch);q.adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);time.sleep(1)
transport.wait(cloud=True)
phone=transport.read("emulator-5556",True);team_id=phone["favoriteTeams"][0]
team=next(t for f in phone["fixtures"] for t in [f["home"],f["away"]]+f.get("participants",[]) if t["id"]==team_id)["name"]
report["phone_favorite_received_on_watch"]=team_id in transport.read("emulator-5558")["favoriteTeams"]
q.SERIAL="emulator-5558";q.launch();q.seek("Favoritos");q.tap("Favoritos");q.seek(team);q.shot("premium-watch-live-favorite")
q.tap(team)
for _ in range(20):
 if team_id not in transport.read("emulator-5556",True)["favoriteTeams"]:break
 time.sleep(.5)
else:raise RuntimeError("Watch favorite did not reach phone")
report["watch_favorite_received_on_phone"]=True
q.launch();q.seek("Créditos y boletos");q.tap("Créditos y boletos");q.seek("Parlay · 4 partidos");q.tap("Parlay · 4 partidos");q.shot("premium-watch-four-sport-ticket")
q.seek("Retorno potencial",True);q.shot("premium-watch-four-sport-return")
(Path(__file__).parent/"paired-multisport.json").write_text(json.dumps(report,indent=2))
print("PASS actual paired delivery, four-sport ticket and bidirectional favorite",flush=True)
