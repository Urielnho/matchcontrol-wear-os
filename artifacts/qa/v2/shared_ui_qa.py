"""UI checks only; the two emulators are not claimed to be paired."""
import sys,time,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import mobile_ui_qa as m
q=m.q
if sys.argv[1:]==["phone"]:
 m.launch();q.tap("Favoritos");q.tap("Elegir equipos y ligas")
 root=q.dump()
 switches=[n for n in root.iter("node") if n.get("checkable")=="true" and n.get("checked")=="false"]
 if not switches:raise RuntimeError("No unchecked favorite available")
 switch=switches[0];x1,y1,x2,y2=q.bounds(switch);cy=(y1+y2)//2
 names=[n for n in root.iter("node") if n.get("text") and abs(sum(q.bounds(n)[1::2])//2-cy)<60]
 if not names:raise RuntimeError("Favorite team label missing")
 team=names[0].get("text")
 q.adb("shell","input","tap",(x1+x2)//2,cy);time.sleep(1);q.tap("Ver favoritos")
 if not q.find(team):raise RuntimeError("Favorite did not appear immediately")
 q.shot("premium-shared-favorite")
 m.launch();q.tap("Favoritos")
 if not q.find(team):raise RuntimeError("Favorite did not survive restart")
 q.tap("Perfil");q.seek("El reloj usa esta misma cuenta",True);q.shot("premium-shared-account")
 (Path(__file__).parent/"shared-account-ui.json").write_text(json.dumps({"favorite_immediate_on_phone":True,"favorite_survives_restart":True,"paired_transport_tested":False},indent=2))
 print("PASS phone favorite and shared-account instructions",flush=True)
elif sys.argv[1:]==["watch"]:
 q.SERIAL="emulator-5558";q.launch()
 if not q.find("Una sola cuenta"):raise RuntimeError("Unlinked watch should not create an account")
 q.shot("premium-watch-shared-account")
 q.seek("Créditos y boletos");q.tap("Créditos y boletos")
 if not q.find("Vincula tu móvil"):raise RuntimeError("Unlinked wallet should remain hidden")
 q.shot("premium-watch-unlinked-wallet")
 print("PASS watch has no independent account or wallet",flush=True)
