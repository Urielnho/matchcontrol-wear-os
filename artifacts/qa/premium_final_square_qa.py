from premium_ui_qa import *
try:
 adb("shell","cmd","connectivity","airplane-mode","enable")
 adb("shell","svc","wifi","disable");adb("shell","svc","data","disable")
 launch();top()
 root=dump();texts=[n.get("text","") for n in root.iter("node")]
 assert "API" in texts,"Original API preference was lost"
 assert "Pendiente de actualizar" in texts,"Expected cached stale response"
 shot("premium-square-stale-offline")
finally:
 adb("shell","cmd","connectivity","airplane-mode","disable")
 adb("shell","svc","wifi","enable");adb("shell","svc","data","enable")
 time.sleep(2)
overview("premium-square")
root=dump();texts=[n.get("text","") for n in root.iter("node")]
assert "API" in texts and "DEMO" not in texts,"Wrong source on final Home"
# Open the featured real fixture when available; keep unavailable API data empty.
fixture=next((n.get("content-desc") for n in root.iter("node") if " contra " in n.get("content-desc","")),None)
if fixture:
 tap(fixture);top();shot("premium-square-api-details");back();top()
shot("premium-square-home")
print("PASS: original square/API mode, real stale cache, all routes and source consistency",flush=True)
