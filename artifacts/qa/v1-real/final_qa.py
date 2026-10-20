from watch_qa import *
import json

def assert_clean():
 root=dump();texts=[n.get("text","") for n in root.iter("node")]
 assert not any(t=="API" or "DEMO" in t or "Simular" in t or "Demostración" in t for t in texts)
 return root

def select_game():
 for _ in range(6):
  for n in dump().iter("node"):
   if " vs " in n.get("content-desc",""):
    x1,y1,x2,y2=bounds(n)
    if y2-y1>20 and 45<(y1+y2)//2<330:
     adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);time.sleep(1);return
  swipe()
 raise RuntimeError("No game visible")

def quota():
 root=ET.fromstring(adb("exec-out","run-as","com.matchcontrol","cat","shared_prefs/api-quota.xml"))
 return int(root.find("int[@name='count']").get("value"))

launch();top();assert_clean();shot("football-home")
tap("Configuración");top();assert_clean();shot("settings");back();top()
tap("Elegir deporte");top();shot("sport-picker")
seek("Fórmula 1");tap("Fórmula 1");top();assert_clean();shot("f1-calendar")
seek("Último resultado");swipe();shot("f1-podium")
seek("Singapore Grand Prix",True);tap("Singapore Grand Prix",True);top();assert_clean();shot("f1-result")
seek("Max Verstappen",True);shot("f1-ranking")
back();back();top();seek("Baloncesto");tap("Baloncesto");top();assert_clean();shot("basketball-feed")
seek("Día anterior");tap("Día anterior");top();shot("basketball-results")
select_game();top();assert_clean();shot("basketball-detail");seek("Por cuartos");swipe();shot("basketball-quarters");swipe();shot("basketball-periods-end")
back();back();top();seek("Béisbol");tap("Béisbol");top();assert_clean();shot("baseball-feed")
seek("Día anterior");tap("Día anterior");top();shot("baseball-results")
select_game();top();assert_clean();shot("baseball-detail");seek("Por entradas");swipe();shot("baseball-innings");seek("Hits");shot("baseball-statistics")
back();back();top();seek("Fútbol");tap("Fútbol");top()
before=quota()
for _ in range(8):
 root=dump();buttons=[n for n in root.iter("node") if n.get("text")=="Actualizar" and bounds(n)[3]-bounds(n)[1]>15]
 if buttons:break
 swipe()
assert buttons,"Refresh is unreachable"
x1,y1,x2,y2=bounds(buttons[0]);adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);time.sleep(3)
after=quota();assert after>before,"Manual update did not contact the service"
top();assert_clean();shot("football-home")
(OUT/"runtime-results.json").write_text(json.dumps({"real_only_ui":"passed","sports":["football","formula1","basketball","baseball"],"quarter_and_inning_details":"passed","manual_network_refresh":"passed","requests_before":before,"requests_after":after},indent=2),encoding="utf-8")
print("PASS: four real sports, clean UI, sport-specific results and explicit network update",flush=True)
