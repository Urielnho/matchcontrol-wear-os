"""Verify a two-match parlay through the installed Android interface."""
import sys,time,re,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import mobile_ui_qa as m
q=m.q
m.launch();q.tap("Perfil");q.seek("Laboratorio DEMO");q.tap("Laboratorio DEMO");q.tap("Crear nueva jornada DEMO");q.tap("Partidos");q.tap("Pr\u00f3ximos")
chosen=[]
for index in range(2):
 found=False
 for _ in range(10):
  for node in q.dump().iter("node"):
   label=node.get("content-desc","")
   if " contra " in label and label not in chosen:
    x1,y1,x2,y2=q.bounds(node)
    if 100<(y1+y2)//2<q.size()[1]*.85:
     q.adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);chosen.append(label);found=True;break
  if found:break
  q.swipe()
 if not found:raise RuntimeError("A different upcoming match was not found")
 print("Selecting leg "+str(index+1),flush=True)
 q.seek("Elegir ",True);q.tap("Elegir ",True);q.tap("Abrir boleto")
 if index==0:
  q.seek("Agregar otro partido",True);q.tap("Agregar otro partido",True);q.tap("Pr\u00f3ximos")
q.top(4)
if not q.find("Parlay ",True):raise RuntimeError("Parlay heading missing")
q.shot("premium-parlay")
q.seek("Confirmar boleto DEMO");q.tap("Confirmar boleto DEMO");q.tap("Confirmar")
time.sleep(2);q.shot("premium-parlay-confirmed")
m.launch();q.tap("Apuestas")
q.shot("premium-parlay-persistent")
labels=" ".join(n.get("text","") for n in q.dump().iter("node"))
if "2 selecciones" not in labels:raise RuntimeError("Two-leg ticket missing after restart")
(Path(__file__).parent/"parlay-check.json").write_text(json.dumps({"different_matches":len(set(chosen)),"confirmed":True,"survives_restart":True},indent=2))
print("PASS two-match parlay, confirmation and restart persistence",flush=True)
