from pathlib import Path
import sys,time,json
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

m.launch()
assert transport.read('emulator-5556')['profile']['id']=='local-demo'
q.tap('Partidos');q.tap('Próximos')
for index,name in enumerate(['Fútbol','Baloncesto','Béisbol','F1']):
 choose(name);open_card();q.seek('Elegir ',True);q.tap('Elegir ',True);q.tap('Abrir boleto')
 if index<3:q.seek('Agregar otro partido',True);q.tap('Agregar otro partido',True);q.tap('Próximos')
 print('PASS local '+name,flush=True)
q.top(3);q.shot('premium-four-sport-parlay')
q.seek('Confirmar boleto DEMO');q.tap('Confirmar boleto DEMO');q.tap('Confirmar')
report=transport.wait()
q.SERIAL='emulator-5558';q.launch();q.seek('Créditos y boletos');q.tap('Créditos y boletos');q.seek('Parlay · 4 partidos');q.tap('Parlay · 4 partidos');q.shot('premium-watch-four-sport-ticket')
q.seek('Retorno potencial',True);q.shot('premium-watch-four-sport-return')
(Path(__file__).parent/'local-four-sport-parlay.json').write_text(json.dumps(report,indent=2))
print('PASS ready local four-sport ticket with exact displayed odds',flush=True)
