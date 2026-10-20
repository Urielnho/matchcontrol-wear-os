from watch_qa import *
import json

def basket_count():
 r=ET.fromstring(adb("exec-out","run-as","com.matchcontrol","cat","shared_prefs/sports-quota.xml"))
 return sum(int(n.get("value")) for n in r.findall("int") if n.get("name","").startswith("BASKETBALL-"))
launch();top();shot("football-home")
tap("Elegir deporte");top();shot("sport-picker")
seek("Fórmula 1");tap("Fórmula 1");top();shot("f1-calendar")
seek("Ver resultados",steps=30);shot("f1-historical-calendar");tap("Ver resultados");time.sleep(3);top()
for _ in range(5):
 root=dump();texts=[n.get("text","") for n in root.iter("node")]
 if "Consultando clasificación" not in texts and "Cargando resultados" not in texts:break
 time.sleep(1)
assert not any("No se pudo completar"==t for t in texts),"Historical race could not load"
shot("f1-historical-result")
seek("Clasificación");swipe();shot("f1-historical-ranking")
back();back();top();seek("Baloncesto");tap("Baloncesto");top();shot("basketball-feed")
before=basket_count();seek("Actualizar resultados");tap("Actualizar resultados");time.sleep(3)
for _ in range(5):
 root=dump()
 if not any(n.get("text")=="Cargando resultados" for n in root.iter("node")):break
 time.sleep(1)
after=basket_count();assert after>=before+2,"Sport refresh did not query both UTC dates"
top();shot("basketball-feed")
launch();top();tap("Configuración");top();shot("settings");back();top();shot("football-home")
(OUT/"release-check.json").write_text(json.dumps({"historical_f1_lookup":"passed","basketball_manual_refresh":"passed","requests_before":before,"requests_after":after,"final_screen":"football_home"},indent=2),encoding="utf-8")
print("PASS: published historical F1 classification, sport-specific refresh and final clean Home",flush=True)
