"""ADB visual QA helper; operates only on public UI, never reads credentials."""
import subprocess,time,re,xml.etree.ElementTree as ET,pathlib,sys
ADB=str(pathlib.Path.home()/"AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL=sys.argv[1] if len(sys.argv)>1 else "emulator-5558"
OUT=pathlib.Path(__file__).parent
counter=0
def adb(*args): return subprocess.check_output([ADB,"-s",SERIAL,*map(str,args)])
def dump():
 global counter
 for attempt in range(3):
  counter+=1;name=f"/sdcard/premium-{counter}.xml"
  result=adb("shell","uiautomator","dump",name)
  if b"ERROR" not in result and b"dumped" in result:
   return ET.fromstring(adb("exec-out","cat",name))
  time.sleep(1)
 raise RuntimeError("UI hierarchy unavailable")
def bounds(node): return list(map(int,re.findall(r"\d+",node.attrib["bounds"])))
def find(label,contains=False):
 for n in dump().iter("node"):
  if any((label in n.get(k,"") if contains else label==n.get(k,"")) for k in ["text","content-desc"]):
   x1,y1,x2,y2=bounds(n)
   cx,cy=(x1+x2)//2,(y1+y2)//2
   w,h=size()
   if SERIAL=="emulator-5558" and ((cx-w/2)/(w/2))**2+((cy-h/2)/(h/2))**2>.9**2:continue
   if y2-y1>=16 and x2>x1 and 45<cy<h*.9:return cx,cy
 return None
def tap(label,contains=False):
 xy=find(label,contains)
 if not xy:raise RuntimeError("Not visible: "+label)
 adb("shell","input","tap",*xy);time.sleep(.7)
def size(): return list(map(int,re.findall(r"(\d+)x(\d+)",adb("shell","wm","size").decode())[0]))
def swipe(up=True):
 w,h=size();adb("shell","input","swipe",w//2,int(h*(.78 if up else .25)),w//2,int(h*(.25 if up else .78)),500);time.sleep(.5)
def top(steps=2):
 for _ in range(steps):swipe(False)
def seek(label,contains=False,steps=14):
 for _ in range(steps):
  if find(label,contains):return
  swipe()
 raise RuntimeError("Not found while scrolling: "+label)
def shot(name):
 root=dump();time.sleep(.3)
 (OUT/(name+".png")).write_bytes(adb("exec-out","screencap","-p"))
 (OUT/(name+".xml")).write_bytes(ET.tostring(root,encoding="utf-8"))
 print("Captured "+name,flush=True)
def launch():
 adb("shell","am","force-stop","com.matchcontrol");adb("shell","am","start","-W","-n","com.matchcontrol/.MainActivity");time.sleep(2)
def back():adb("shell","input","keyevent",4);time.sleep(.7)
def overview(prefix):
 launch();top();shot(prefix+"-home")
 for label,suffix in [("En vivo","live"),("Próximos","upcoming"),("Resultados","results"),("Favoritos","favorites")]:
  seek(label);tap(label);top()
  assert find("Volver") and any(n.get("text")==label for n in dump().iter("node")), "Wrong route: "+label
  shot(prefix+"-"+suffix);back();top()
 seek("Configuración");tap("Configuración");top();shot(prefix+"-settings");back();top()
if __name__=="__main__":overview(sys.argv[2] if len(sys.argv)>2 else "premium-round")
