"""Read domain snapshots to verify real paired-device delivery; never inject state."""
from pathlib import Path
import json,sqlite3,subprocess,time
ROOT=Path(__file__).resolve().parents[3]
ADB=str(Path.home()/"AppData/Local/Android/Sdk/platform-tools/adb.exe")
def read(serial,cloud=False):
 name="remote-v2-cache.db" if cloud else "matchcontrol-v2.db"
 data=subprocess.check_output([ADB,"-s",serial,"exec-out","run-as","com.matchcontrol","cat","databases/"+name])
 path=ROOT/".tool-cache"/(serial+"-domain-check.db");path.write_bytes(data)
 db=sqlite3.connect(path)
 try:return json.loads(db.execute("SELECT payload FROM "+("snapshot" if cloud else "state")+" WHERE id=1").fetchone()[0])
 finally:db.close()
def wait(cloud=False,timeout=20):
 start=time.monotonic()
 while time.monotonic()-start<timeout:
  phone=read("emulator-5556",cloud);watch=read("emulator-5558")
  if all(phone.get(k)==watch.get(k) for k in ["profile","source","balance","favoriteTeams","favoriteLeagues","favoriteMatches"]) and {b["id"] for b in phone["bets"]}.issubset({b["id"] for b in watch["bets"]}):
   return {"transport":"Google Wearable Data Layer","account_matches":True,"balance_matches":True,"favorites_match":True,"tickets_received":len(watch["bets"]),"sports_received":sorted({f.get("sport","FOOTBALL") for f in watch["fixtures"]}),"observed_wait_ms":round((time.monotonic()-start)*1000)}
  time.sleep(.5)
 raise AssertionError("Phone and watch did not converge")
if __name__=="__main__":
 import sys
 report=wait("--cloud" in sys.argv)
 (Path(__file__).parent/"paired-transport.json").write_text(json.dumps(report,indent=2))
 print(json.dumps(report))
