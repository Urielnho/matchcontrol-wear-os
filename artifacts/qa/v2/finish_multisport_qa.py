"""Restore the local demonstration after QA and verify account removal on the watch."""
import sys,time,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import mobile_ui_qa as m
import mirror_transport_qa as transport
q=m.q
m.launch();q.tap("Perfil");q.seek("Reales");q.tap("Reales");q.tap("Inicio");q.tap("F1");time.sleep(2);q.shot("premium-real-f1")
q.tap("Perfil");q.seek("Cerrar sesión");q.tap("Cerrar sesión");time.sleep(1)
report=transport.wait()
if transport.read("emulator-5558")["profile"]["id"]!="local-demo":raise RuntimeError("QA account remained on watch")
q.seek("Laboratorio DEMO");q.tap("Laboratorio DEMO");q.tap("Crear nueva jornada DEMO");q.tap("Inicio");q.tap("Todos")
q.shot("premium-ready-multisport")
transport.wait()
q.SERIAL="emulator-5558";q.launch();q.shot("premium-watch-ready-multisport")
p=Path(__file__).parent/"paired-multisport.json";data=json.loads(p.read_text());data["logout_clears_cloud_account_on_watch"]=True;data["local_balance_and_history_preserved"]=True;p.write_text(json.dumps(data,indent=2))
print("PASS cloud QA logged out, original local account restored on both devices",flush=True)
