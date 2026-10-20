from watch_qa import *

def select_card():
 for _ in range(5):
  root=dump()
  candidates=[n for n in root.iter("node") if " vs " in n.get("content-desc","")]
  for node in candidates:
   x1,y1,x2,y2=bounds(node)
   if y2-y1>30 and 45<(y1+y2)//2<330:
    adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);time.sleep(1);return
  swipe()
 raise RuntimeError("No visible game card")
launch();top();tap("Elegir deporte");seek("Baloncesto");tap("Baloncesto");top();time.sleep(2);shot("basketball-feed")
seek("Día anterior");tap("Día anterior");top();time.sleep(2);shot("basketball-results")
select_card();top();shot("basketball-detail");seek("Por cuartos");swipe();shot("basketball-quarters")
assert any("cuarto" in n.get("text","") for n in dump().iter("node")),"Quarter breakdown missing"
back();back();top();seek("Béisbol");tap("Béisbol");top();time.sleep(2);shot("baseball-feed")
seek("Día anterior");tap("Día anterior");top();time.sleep(2);shot("baseball-results")
select_card();top();shot("baseball-detail");seek("Por entradas");swipe();shot("baseball-innings")
assert any("Entrada" in n.get("text","") for n in dump().iter("node")),"Innings missing"
launch();top();shot("football-home")
print("PASS: real game cards, basketball quarter breakdown and baseball innings",flush=True)
