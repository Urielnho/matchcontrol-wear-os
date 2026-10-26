"""Manual-style Android UI checks; never outputs credentials or access tokens."""
import pathlib,sys,time,json,subprocess
sys.path.insert(0,str(pathlib.Path(__file__).parents[1]/"v1-real"))
import watch_qa as q
q.SERIAL="emulator-5556";q.OUT=pathlib.Path(__file__).parent
def mobile_find(label,contains=False):
 for n in q.dump().iter("node"):
  if any(label in n.get(k,"") if contains else label==n.get(k,"") for k in ("text","content-desc")):
   x1,y1,x2,y2=q.bounds(n)
   if x2>x1 and y2-y1>=16:return (x1+x2)//2,(y1+y2)//2
 return None
q.find=mobile_find
def launch():
 q.adb("shell","am","force-stop","com.matchcontrol")
 q.adb("shell","am","start","-W","-n","com.matchcontrol/com.matchcontrol.mobile.MainActivity");time.sleep(2)
def card():
 for _ in range(8):
  for n in q.dump().iter("node"):
   if " contra " in n.get("content-desc",""):
    x1,y1,x2,y2=q.bounds(n)
    if 100<(y1+y2)//2<q.size()[1]*.8:
     q.adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);time.sleep(1);return
  q.swipe()
 raise RuntimeError("Fixture card not visible")
def clear_type(x,y,text):
 q.adb("shell","input","tap",x,y);q.adb("shell","input","keyevent",123)
 q.adb("shell","input","keyevent","--longpress",67)
 q.adb("shell","input","text",text)
if sys.argv[1:]==["overview"]:
 launch();q.shot("premium-home");q.tap("Partidos");q.shot("premium-matches");q.tap("Apuestas");q.shot("premium-bets");q.tap("Favoritos");q.shot("premium-favorites");q.tap("Perfil");q.shot("premium-profile")
elif sys.argv[1:]==["bet"]:
 launch();q.tap("Partidos");q.tap("Próximos");card();q.shot("premium-detail")
 q.seek("Ganador · 1X2");q.seek("Elegir AUR, cuota 1.85, Ganador · 1X2");q.tap("Elegir AUR, cuota 1.85, Ganador · 1X2")
 q.tap("Abrir boleto");q.shot("premium-slip");q.seek("Confirmar boleto DEMO");q.tap("Confirmar boleto DEMO");q.tap("Confirmar");q.shot("premium-confirmed")
 launch();q.tap("Apuestas");q.shot("premium-persistent-bet")
elif sys.argv[1:]==["login"]:
 launch();q.tap("Perfil")
 qa=json.loads((pathlib.Path(__file__).parents[3]/".tool-cache/live-qa.json").read_text())
 nodes=[n for n in q.dump().iter("node") if n.get("class")=="android.widget.EditText"]
 if len(nodes)!=2:raise RuntimeError("Login fields unavailable")
 for n,text in zip(nodes,[qa["email"],qa["password"]]):
  x1,y1,x2,y2=q.bounds(n);q.adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);q.adb("shell","input","text",text);time.sleep(.3)
 q.adb("shell","input","keyevent",4);q.tap("Entrar");time.sleep(5);q.shot("premium-signed-profile")
 q.tap("Inicio");q.shot("premium-real-home")

elif sys.argv[1:]==["offline"]:
 try:
  q.adb("shell","cmd","connectivity","airplane-mode","enable")
  q.adb("shell","svc","wifi","disable");q.adb("shell","svc","data","disable");time.sleep(3)
  launch();q.shot("premium-offline")
  nodes=list(q.dump().iter("node"))
  labels=" ".join(n.get("text","")+n.get("content-desc","") for n in nodes)
  if "Sin conexión" not in labels:raise RuntimeError("Offline label missing")
  if "Deportivo Capiata" not in labels:raise RuntimeError("Real fixture cache missing")
  print("PASS offline cache and restored session",flush=True)
 finally:
  q.adb("shell","cmd","connectivity","airplane-mode","disable")
  q.adb("shell","svc","wifi","enable");q.adb("shell","svc","data","enable")
 time.sleep(4);launch();q.shot("premium-reconnected")
 nodes=list(q.dump().iter("node"));labels=" ".join(n.get("text","") for n in nodes)
 if "Sin conexión" in labels:raise RuntimeError("Connectivity did not recover")
 print("PASS connection restored",flush=True)

elif sys.argv[1:]==["logout"]:
 launch();q.tap("Perfil");q.tap("Cerrar sesi\u00f3n");time.sleep(2)
 if not q.find("Entrar"):raise RuntimeError("Sign out did not restore login")
 print("PASS signed out QA account",flush=True)
 q.tap("Inicio")
