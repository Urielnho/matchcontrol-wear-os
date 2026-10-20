from premium_ui_qa import *
launch();top();seek("Aurora FC contra",True);tap("Aurora FC contra",True);top();shot("premium-round-191-details")
seek("Simular Gol");tap("Simular Gol");top(8)
assert any(n.get("text")=="2 : 0" for n in dump().iter("node")),"Score did not update"
shot("premium-round-191-goal")
seek("Posesión");swipe();shot("premium-round-191-statistics")
# Reach the native edge action at the end, then invoke it inside the circular surface.
for _ in range(7):swipe()
root=dump();nodes=[n for n in root.iter("node") if n.get("text")=="Actualizar"]
assert nodes,"Edge update action not reachable"
shot("premium-round-191-edge-button")
x1,y1,x2,y2=bounds(nodes[0]);adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2);time.sleep(1)
assert b"com.matchcontrol" in adb("shell","dumpsys","activity","activities"),"App closed unexpectedly"
print("PASS: small circular details, score update, statistics and edge action",flush=True)
