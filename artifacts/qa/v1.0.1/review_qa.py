"""Public-UI review of the installed app, using only real loaded matches."""
import pathlib,sys,json,time
sys.path.insert(0,str(pathlib.Path(__file__).parents[1]/"v1-real"))
import watch_qa as q
q.SERIAL="emulator-5558"
q.OUT=pathlib.Path(__file__).parent

if sys.argv[1:]==["permissions"]:
 q.launch();q.tap("Configuración");q.seek("Activar avisos");q.tap("Activar avisos");q.shot("09-permiso")
 print(json.dumps([(n.get("text"),n.get("resource-id"),n.get("bounds")) for n in q.dump().iter("node") if n.get("text")],ensure_ascii=True),flush=True)
elif sys.argv[1:]==["offline"]:
 try:
  q.adb("shell","cmd","connectivity","airplane-mode","enable")
  q.adb("shell","svc","wifi","disable");q.adb("shell","svc","data","disable");time.sleep(4);q.launch()
  q.seek("Reintentar");q.tap("Reintentar");time.sleep(3);q.top(3);q.shot("12-sin-internet")
  assert any(n.get("text")=="Sin internet" for n in q.dump().iter("node"))
 finally:
  q.adb("shell","cmd","connectivity","airplane-mode","disable")
  q.adb("shell","svc","wifi","enable");q.adb("shell","svc","data","enable")
 time.sleep(4);q.launch();q.shot("13-conexion-restaurada")
 assert not any(n.get("text")=="Sin internet" for n in q.dump().iter("node"))
elif sys.argv[1:]==["video"]:
 q.launch();q.shot("01-inicio");q.swipe();q.swipe();q.shot("02-accesos")
 q.seek("Favoritos");q.tap("Favoritos");q.shot("14-favoritos")
 q.back();q.top(4);q.tap("Elegir deporte");q.shot("15-deportes")
 q.seek("Fórmula 1");q.tap("Fórmula 1");time.sleep(3);q.shot("16-formula1")
 q.back();q.back();q.top(4)
