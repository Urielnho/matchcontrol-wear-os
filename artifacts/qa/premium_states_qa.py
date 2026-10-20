from premium_ui_qa import *
try:
 adb("shell","cmd","connectivity","airplane-mode","enable")
 adb("shell","svc","wifi","disable");adb("shell","svc","data","disable")
 launch();top();tap("Configuración");seek("Demostración activada");tap("Demostración activada")
 time.sleep(3);back();top()
 for _ in range(5):
  root=dump();texts=[n.get("text","") for n in root.iter("node")]
  if "Cargando partidos" not in texts:break
  time.sleep(2)
 assert not any("Aurora" in t for t in texts),"DEMO data leaked into API mode"
 assert "Sin internet" in texts,"Device did not disconnect"
 shot("premium-round-offline-api-error")
 seek("Configuración");tap("Configuración");seek("Datos reales · API");tap("Datos reales · API");back();top()
 seek("Aurora FC contra",True)
 assert find("Aurora FC contra",True),"DEMO did not recover"
 shot("premium-round-demo-recovered")
 # Native swipe from the left edge returns from the detail.
 tap("Aurora FC contra",True);top()
 w,h=size();adb("shell","input","swipe",3,h//2,int(w*.85),h//2,500);time.sleep(1);top()
 assert find("MatchControl"),"Swipe-back failed"
 print("PASS: offline/API error, source isolation, DEMO recovery, native back gesture",flush=True)
finally:
 adb("shell","cmd","connectivity","airplane-mode","disable")
 adb("shell","svc","wifi","enable");adb("shell","svc","data","enable")
