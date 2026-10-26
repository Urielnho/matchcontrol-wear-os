"""Package both development apps and clean sources; reject embedded private keys."""
from pathlib import Path
import hashlib, json, shutil, zipfile, xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
out = root / "entrega/v2"
out.mkdir(parents=True, exist_ok=True)
keys = []
local = root / "local.properties"
if local.exists():
    for line in local.read_text(encoding="utf-8").splitlines():
        if line.startswith("FOOTBALL_API_KEY=") and line.split("=",1)[1].strip():
            keys.append(line.split("=",1)[1].strip().encode())

def check(data, name):
    if any(key in data for key in keys):
        raise RuntimeError("Private provider credential found: " + name)

apks = {"MatchControl-Mobile-2.0.apk": root/"mobile/build/outputs/apk/debug/mobile-debug.apk",
        "MatchControl-Wear-2.0.apk": root/"app/build/outputs/apk/debug/wear-debug.apk"}
for name, apk in apks.items():
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.namelist():
            check(archive.read(entry), entry)
    shutil.copy2(apk, out/name)

members = set()
for directory in ["app/src", "mobile/src", "core/src", "gradle", "docs", ".run", "supabase/functions", "supabase/migrations"]:
    members.update(p for p in (root/directory).rglob("*") if p.is_file() and "__pycache__" not in p.parts)
for directory in ["backend-tests"]:
    members.update(p for p in (root/directory).iterdir() if p.is_file() and p.suffix in {".json", ".mjs", ".js", ".py"})
for name in [".gitignore", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat", "app/build.gradle.kts", "mobile/build.gradle.kts", "core/build.gradle.kts", "README.md", "VALIDACION.md", "REDISENO.md", "local.properties.example", "supabase/config.toml", "supabase/.env.example", "ejecutar-circular.ps1", "preparar-entrega.py"]:
    members.add(root/name)
with zipfile.ZipFile(out/"Codigo-MatchControl-2.0.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(members):
        data=path.read_bytes(); check(data, str(path.relative_to(root)))
        archive.writestr(path.relative_to(root).as_posix(), data)
for name in ["README.md", "docs/ESTADO-2.0.md", "docs/GUIA-PRESENTACION.md"]:
    shutil.copy2(root/name, out/Path(name).name)
captures=out/"capturas"; captures.mkdir(exist_ok=True)
for path in (root/"artifacts/qa/v2").glob("premium-*.png"):
    shutil.copy2(path, captures/path.name)
report={"development_apks": True, "private_provider_key_absent": True}
for module in ["core", "app"]:
    suites=[ET.parse(p).getroot() for p in (root/module/"build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
    report[module]={key:sum(int(s.get(key,0)) for s in suites) for key in ["tests","failures","errors","skipped"]}
for module in ["mobile", "app"]:
    lint=ET.parse(root/module/"build/reports/lint-results-debug.xml").getroot()
    report[module+"_lint"]={severity:sum(i.get("severity")==severity for i in lint) for severity in ["Error","Warning"]}
report["sha256"]={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.iterdir() if p.suffix in {".apk", ".zip"}}
(out/"COMPROBACION.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
print(json.dumps(report,indent=2))
