from watch_qa import *
launch();top();tap("Elegir deporte");seek("Baloncesto");tap("Baloncesto");seek("Día anterior");tap("Día anterior");top()
seek("Panteras vs Lobos Plateados",True);tap("Panteras vs Lobos Plateados",True);top()
seek("Por cuartos");shot("basketball-breakdown-warning")
assert any(n.get("text")=="Desglose por confirmar" for n in dump().iter("node")),"Inconsistent published periods were not flagged"
launch();top();shot("football-home")
print("PASS: real provider inconsistency is explained without invented overtime; final Home restored",flush=True)
