"""Package the local classroom delivery without private project configuration."""
from pathlib import Path
import hashlib,json,shutil,zipfile,xml.etree.ElementTree as ET

root=Path(__file__).resolve().parent
out=root/"entrega"
out.mkdir(exist_ok=True)
apk=root/"app/build/outputs/apk/debug/app-debug.apk"
shutil.copy2(apk,out/"MatchControl-1.0.1.apk")
for name in ["README.md","VALIDACION.md"]:
 shutil.copy2(root/name,out/name)
shutil.copy2(root/"docs/GUIA-PRESENTACION.md",out/"GUIA-PRESENTACION.md")
qa=root/"artifacts/qa/v1.0.1"
captures=out/"capturas"
captures.mkdir(exist_ok=True)
for name in ["01-inicio","02-accesos","07-equipo-seguido","08-favorito-persistente","09-permiso","10-avisos-rechazados","11-avisos-activados","12-sin-internet","13-conexion-restaurada","15-deportes","16-formula1"]:
 latest=root/"artifacts/qa/minimal"/(name+".png")
 shutil.copy2(latest if latest.exists() else qa/(name+".png"),captures/(name+".png"))
shutil.copy2(qa/"recorrido.mp4",out/"Recorrido-MatchControl.mp4")
members=[]
for directory in ["app/src","gradle","docs",".run"]:
 members.extend(p for p in (root/directory).rglob("*") if p.is_file())
for name in [".gitignore","build.gradle.kts","settings.gradle.kts","gradle.properties","gradlew","gradlew.bat","app/build.gradle.kts","README.md","VALIDACION.md","REDISENO.md","ejecutar-circular.ps1","preparar-entrega.py"]:
 members.append(root/name)
# Verify the archive never embeds the configured private credential.
key=next((line.split("=",1)[1].strip() for line in (root/"local.properties").read_text(encoding="utf-8").splitlines() if line.startswith("FOOTBALL_API_KEY=")),"")
with zipfile.ZipFile(out/"Codigo-MatchControl.zip","w",zipfile.ZIP_DEFLATED) as archive:
 for file in sorted(members):
  data=file.read_bytes()
  if key and key.encode() in data: raise RuntimeError("Private configuration found in a source file; packaging stopped.")
  archive.writestr(file.relative_to(root).as_posix(),data)
tests=[ET.parse(p).getroot() for p in (root/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
summary={k:sum(int(r.get(k,0)) for r in tests) for k in ["tests","failures","errors","skipped"]}
lint=ET.parse(root/"app/build/reports/lint-results-debug.xml").getroot()
summary["lint_errors"]=sum(x.get("severity")=="Error" for x in lint)
summary["lint_warnings"]=sum(x.get("severity")=="Warning" for x in lint)
summary["apk_sha256"]=hashlib.sha256(apk.read_bytes()).hexdigest()
summary["source_excludes_private_configuration"]=True
summary["files"]={p.name:p.stat().st_size for p in out.iterdir() if p.is_file() and p.name!="COMPROBACION.json"}
(out/"COMPROBACION.json").write_text(json.dumps(summary,indent=2),encoding="utf-8")
print(json.dumps(summary,indent=2))
