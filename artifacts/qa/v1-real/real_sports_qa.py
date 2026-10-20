from watch_qa import *

def clean(root):
 texts=[n.get("text","") for n in root.iter("node")]
 assert not any(t=="API" or "DEMO" in t or "Simular" in t or "Demostración" in t for t in texts),"Technical or synthetic controls remain"
 return texts
launch();top();clean(dump());shot("football-home")
tap("Configuración");top();clean(dump());shot("settings");back();top()
tap("Elegir deporte");top();shot("sport-picker")
seek("Fórmula 1");tap("Fórmula 1");top();time.sleep(3);clean(dump());shot("f1-calendar")
seek("Último resultado");swipe();shot("f1-podium")
seek("Singapore Grand Prix",True);tap("Singapore Grand Prix",True);top();clean(dump());shot("f1-result")
seek("Max Verstappen",True);shot("f1-ranking")
back();back();top()
seek("Baloncesto");tap("Baloncesto");top();time.sleep(3);clean(dump());shot("basketball-feed")
# Yesterday's actual completed games expose the full quarter breakdown.
seek("Día anterior");tap("Día anterior");top();time.sleep(2)
seek("Resultados");swipe();shot("basketball-results")
root=dump();cards=[n for n in root.iter("node") if n.get("clickable")=="true" and n.get("class")=="android.view.View" and bounds(n)[3]-bounds(n)[1]>100]
# Select a visible score card, excluding the scrolling container and toolbar.
w,h=size()
card=next((n for n in cards if bounds(n)[1]>45 and bounds(n)[3]<h*.98),None)
if card:
 x1,y1,x2,y2=bounds(card);adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);time.sleep(2);top();shot("basketball-detail");seek("Por cuartos");swipe();shot("basketball-quarters");back()
back();top();seek("Béisbol");tap("Béisbol");top();time.sleep(3);clean(dump());shot("baseball-feed")
seek("Día anterior");tap("Día anterior");top();time.sleep(2);seek("Resultados");swipe();shot("baseball-results")
print("PASS: real-only football, F1, basketball and baseball navigation",flush=True)
