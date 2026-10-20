param([switch]$SinCompilar, [string]$Avd = 'MatchControl_Round_QA')
$ErrorActionPreference = 'Stop'
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
$emulatorPath = Join-Path $sdkPath 'emulator\emulator.exe'
$serial = 'emulator-5558'
if (!(Test-Path -LiteralPath $adbPath) -or !(Test-Path -LiteralPath $emulatorPath)) { throw 'Instala Android SDK y Android Emulator desde Android Studio.' }
if (!((& $emulatorPath -list-avds) -contains $Avd)) { throw "No existe el reloj $Avd. Créalo desde Device Manager o indica -Avd con el nombre de tu reloj circular." }
Push-Location $PSScriptRoot
try {
 if (!$SinCompilar) {
  if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
  & .\gradlew.bat assembleDebug --console=plain
  if ($LASTEXITCODE -ne 0) { throw 'La compilación falló. Revisa el mensaje de Gradle.' }
 }
 $apkPath = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
 if (!(Test-Path -LiteralPath $apkPath)) { throw 'Falta el APK. Ejecuta el script sin -SinCompilar.' }
 $devices = & $adbPath devices
 if (!($devices -match '^emulator-5558\s+device')) {
  Start-Process -FilePath $emulatorPath -ArgumentList "-avd $Avd -port 5558 -no-boot-anim -gpu software -feature -Vulkan" -WindowStyle Normal
 }
 Write-Host 'Esperando a que el reloj termine de iniciar...'
 $limit = (Get-Date).AddMinutes(3)
 do {
  try { $boot = & $adbPath -s $serial shell getprop sys.boot_completed 2>$null } catch { $boot = '' }
  if ("$boot".Trim() -eq '1') { break }
  if ((Get-Date) -gt $limit) { throw 'El reloj tardó demasiado en iniciar. Revisa Device Manager.' }
  Start-Sleep -Seconds 2
 } while ($true)
 $name = & $adbPath -s $serial emu avd name
 if (!($name -contains $Avd)) { throw 'El puerto 5558 pertenece a otro emulador. Ciérralo antes de continuar.' }
 & $adbPath -s $serial install -r $apkPath
 if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar el APK.' }
 & $adbPath -s $serial shell am start -W -n com.matchcontrol/.MainActivity
 if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir MatchControl.' }
 Write-Host 'MatchControl está abierto en el reloj circular.'
} finally { Pop-Location }
