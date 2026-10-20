from premium_ui_qa import *
launch();top();shot("premium-round-191-home")
seek("Próximos");tap("Próximos");top();shot("premium-round-191-upcoming")
back();top();seek("Aurora FC contra",True);tap("Aurora FC contra",True);top();shot("premium-round-191-details")
for _ in range(20):
 root=dump()
 if any(n.get("text")=="Actualizar" and bounds(n)[3]-bounds(n)[1]>20 for n in root.iter("node")):break
 swipe()
root=dump();labels=[n for n in root.iter("node") if n.get("text")=="Actualizar"]
assert labels,"Edge action is missing"
x1,y1,x2,y2=bounds(labels[0]);assert y2-y1<55,"Edge label wraps"
shot("premium-round-191-edge-button")
adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2)
adb("shell","wm","density","reset")
launch();top();shot("premium-round-227-home")
seek("Próximos");tap("Próximos");top();shot("premium-round-227-upcoming")
back();top();seek("Aurora FC contra",True);tap("Aurora FC contra",True);top();shot("premium-round-227-details")
print("PASS: final circular 191/227dp layouts and single-line edge action",flush=True)
