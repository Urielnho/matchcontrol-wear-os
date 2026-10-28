param(
 [switch]$SinCompilar,
 [switch]$SinVentana,
 [string]$MovilAvd = 'MatchControl_Mobile_Play',
 [string]$RelojAvd = 'MatchControl_Round_QA',
 [string]$ZonaHoraria = 'America/Phoenix',
 [ValidateRange(60,1800)][int]$EsperaSegundos = 480
)
$ErrorActionPreference = 'Stop'

# Only public SDK settings are read; credentials remain in local.properties.
$sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
$localFile = Join-Path $PSScriptRoot 'local.properties'
if (Test-Path -LiteralPath $localFile) {
 $sdkLine = Get-Content -LiteralPath $localFile | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
 if ($sdkLine) { $sdkCandidates += (($sdkLine -replace '^\s*sdk\.dir\s*=\s*','') -replace '\\:',':' -replace '\\\\','\') }
}
if ($env:LOCALAPPDATA) { $sdkCandidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk') }
$sdkPath = $sdkCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platform-tools\adb.exe')) -and (Test-Path -LiteralPath (Join-Path $_ 'emulator\emulator.exe')) } | Select-Object -First 1
if (!$sdkPath) { throw 'No se encontro Android SDK. Instalalo desde Android Studio o configura ANDROID_HOME.' }
$adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
$emulatorPath = Join-Path $sdkPath 'emulator\emulator.exe'

function Invoke-AdbChecked([string[]]$CommandArgs) {
 # Windows PowerShell may wrap native stderr (including ADB startup notices) as errors.
 $ErrorActionPreference = 'Continue'
 $result = & $adbPath @CommandArgs 2>&1
 if ($LASTEXITCODE -ne 0) { throw "ADB fallo ($($CommandArgs -join ' ')): $($result -join [Environment]::NewLine)" }
 return @($result | ForEach-Object { $_.ToString() })
}
function Assert-Avd([string]$Serial,[string]$Expected) {
 $name = @(Invoke-AdbChecked -CommandArgs @('-s',$Serial,'emu','avd','name'))
 if (!($name -contains $Expected)) { throw "El dispositivo $Serial no es $Expected. No se instalara nada en ese dispositivo. Revisa Device Manager." }
}
function Start-AvdIfNeeded([string]$Name,[int]$Port) {
 $serial = "emulator-$Port"
 $devices = @(Invoke-AdbChecked -CommandArgs @('devices'))
 $present = $devices | Where-Object { $_ -match ('^' + [regex]::Escape($serial) + '\s+') }
 if ($present) {
  if ($present -match '\sdevice\s*$') { Assert-Avd $serial $Name }
  return
 }
 $arguments = @('-avd', ('"' + $Name + '"'), '-port', "$Port", '-no-snapshot', '-no-boot-anim', '-gpu', 'swiftshader', '-feature', '-Vulkan', '-timezone', $ZonaHoraria)
 if ($SinVentana) { $arguments += @('-no-window','-no-audio') }
 $windowStyle = if ($SinVentana) { 'Hidden' } else { 'Normal' }
 Start-Process -FilePath $emulatorPath -ArgumentList $arguments -WindowStyle $windowStyle | Out-Null
}
function Wait-Avd([string]$Serial,[string]$Name) {
 Write-Host "Esperando a $Name..."
 $limit = (Get-Date).AddSeconds($EsperaSegundos)
 do {
  $boot = ''; $bootExit = 1
  try { $boot = & $adbPath -s $Serial shell getprop sys.boot_completed 2>$null; $bootExit = $LASTEXITCODE } catch { }
  if ($bootExit -eq 0 -and "$boot".Trim() -eq '1') { Assert-Avd $Serial $Name; return }
  if ((Get-Date) -gt $limit) { throw "$Name no termino de iniciar. Revisa Device Manager; no se borro ningun dato." }
  Start-Sleep -Seconds 2
 } while ($true)
}

Push-Location $PSScriptRoot
try {
 $available = & $emulatorPath -list-avds
 if ($LASTEXITCODE -ne 0) { throw 'No se pudieron consultar los emuladores instalados.' }
 foreach ($name in @($MovilAvd,$RelojAvd)) {
  if (!($available -contains $name)) { throw "No existe el emulador $name. Crealo en Device Manager o indica -MovilAvd / -RelojAvd." }
 }
 if (!$SinCompilar) {
  $javaCandidates = @($env:JAVA_HOME,$env:STUDIO_JDK)
  if ($env:ProgramFiles) { $javaCandidates += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr') }
  $jdkPath = $javaCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe')) } | Select-Object -First 1
  if (!$jdkPath) { throw 'No se encontro Java. Configura JAVA_HOME con el JDK integrado de Android Studio.' }
  $env:JAVA_HOME = $jdkPath
  & .\gradlew.bat :mobile:assembleDebug :wear:assembleDebug --console=plain
  if ($LASTEXITCODE -ne 0) { throw 'La compilacion fallo. Revisa el mensaje de Gradle.' }
 }
 $mobileApk = Join-Path $PSScriptRoot 'mobile\build\outputs\apk\debug\mobile-debug.apk'
 $wearApk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\wear-debug.apk'
 foreach ($apk in @($mobileApk,$wearApk)) { if (!(Test-Path -LiteralPath $apk)) { throw "Falta $apk. Ejecuta el script sin -SinCompilar." } }
 Invoke-AdbChecked -CommandArgs @('start-server') | Out-Host
 Start-AvdIfNeeded $MovilAvd 5556
 Start-AvdIfNeeded $RelojAvd 5558
 Wait-Avd 'emulator-5556' $MovilAvd
 Wait-Avd 'emulator-5558' $RelojAvd
 # Recheck both identities immediately before installing. Never uninstall or clear app data.
 Assert-Avd 'emulator-5556' $MovilAvd
 Assert-Avd 'emulator-5558' $RelojAvd
 foreach ($serial in @('emulator-5556','emulator-5558')) {
  Invoke-AdbChecked -CommandArgs @('-s',$serial,'shell','settings','put','global','auto_time_zone','0') | Out-Host
  Invoke-AdbChecked -CommandArgs @('-s',$serial,'shell','cmd','alarm','set-timezone',$ZonaHoraria) | Out-Host
 }
 Invoke-AdbChecked -CommandArgs @('-s','emulator-5556','install','-r',$mobileApk) | Out-Host
 Invoke-AdbChecked -CommandArgs @('-s','emulator-5558','install','-r',$wearApk) | Out-Host
 $mobileLaunch = @(Invoke-AdbChecked -CommandArgs @('-s','emulator-5556','shell','am','start','-W','-n','com.matchcontrol/com.matchcontrol.mobile.MainActivity'))
 $watchLaunch = @(Invoke-AdbChecked -CommandArgs @('-s','emulator-5558','shell','am','start','-W','-n','com.matchcontrol/.MainActivity'))
 if (($mobileLaunch + $watchLaunch) -match '^Error') { throw "Android no pudo abrir una actividad. $($mobileLaunch + $watchLaunch -join [Environment]::NewLine)" }
 $mobileLaunch + $watchLaunch | Out-Host
 Write-Host 'MatchControl abierto en movil y reloj. Se conservaron cuenta, saldo e historial.'
 Write-Host 'Primer enlace: Android Studio > Device Manager > Pair Wearable. El script no cambia la vinculacion.'
 if ($SinVentana) { Write-Host 'Abre Android Studio > Running Devices para ver los emuladores sin ventana.' }
} finally { Pop-Location }
