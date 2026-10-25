# Pleos_Connect 에뮬레이터 실행 + 앱 빌드/설치/실행 + Virtual Console 열기
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$emu = Join-Path $sdk 'emulator\emulator.exe'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$avd = 'Pleos_Connect'
$pkg = 'com.carmagotchi.app'

function Fail($m) { Write-Host "[오류] $m" -ForegroundColor Red; exit 1 }

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Fail 'JAVA_HOME이 없거나 잘못됐습니다. JDK 17 경로를 JAVA_HOME 환경변수로 지정한 뒤 다시 실행하세요.'
}
if (-not (Test-Path $emu)) { Fail "에뮬레이터를 찾을 수 없습니다: $emu (ANDROID_HOME 확인)" }
if (-not (Test-Path $adb)) { Fail "adb를 찾을 수 없습니다: $adb" }

# 1) 가속 확인
$acc = & $emu -accel-check 2>&1 | Out-String
if ($acc -notmatch 'is installed and usable') {
    Write-Host $acc
    Fail '하드웨어 가속을 쓸 수 없습니다. 재부팅이 필요합니다. (Windows 기능 변경 후 처음 한 번) 재부팅 뒤 이 스크립트를 다시 실행하세요.'
}
Write-Host '가속 확인 OK'

# 2) 에뮬레이터 시작
& $adb start-server | Out-Null
$running = (& $adb devices) -match '^emulator-\d+\s+device'
if ($running) {
    Write-Host '에뮬레이터가 이미 실행 중입니다. 빌드/설치만 진행합니다.'
} else {
    if (-not ((& $emu -list-avds) -contains $avd)) { Fail "AVD '$avd'가 없습니다. docs\SETUP.md 6~7장을 참고해 만드세요." }
    Write-Host "에뮬레이터 시작 중 (콜드 부팅, 몇 분 걸릴 수 있음)..."
    Start-Process -FilePath $emu -ArgumentList @('-avd', $avd, '-no-snapshot-load', '-no-snapshot-save')
    & $adb wait-for-device
    $deadline = (Get-Date).AddMinutes(8)
    while ($true) {
        $b = (& $adb shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if ($b -eq '1') { break }
        if ((Get-Date) -gt $deadline) { Fail '부팅 대기 시간(8분)이 지났습니다. 에뮬레이터 창을 확인하세요.' }
        Start-Sleep -Seconds 3
    }
    Write-Host '부팅 완료'
}

# 시간대를 한국으로 맞춘다(동행 소리의 밤 22~06시 규칙이 기기 현지 시각을 쓴다)
# 자동 시간대를 끈다. 켜 두면 에뮬레이터 기본 위치(미국) 기준으로 LA 시간대로 되돌아간다
& $adb shell settings put global auto_time_zone 0 | Out-Null
& $adb shell cmd alarm set-timezone Asia/Seoul | Out-Null

# 3) 빌드
Write-Host 'APK 빌드 중...'
Push-Location $root
try {
    & (Join-Path $root 'gradlew.bat') assembleDebug
    if ($LASTEXITCODE -ne 0) { Fail '빌드 실패. 위 로그를 확인하세요.' }
} finally { Pop-Location }
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { Fail "APK가 없습니다: $apk" }

# 4) 설치·실행·포워딩
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { Fail 'APK 설치 실패.' }
& $adb shell am start -n "$pkg/.MainActivity" | Out-Null
& $adb forward tcp:8088 tcp:8088 | Out-Null
Start-Process 'http://127.0.0.1:8088/'

Write-Host ''
Write-Host '완료. 다음 단계:' -ForegroundColor Green
Write-Host ' - 열린 브라우저의 Virtual Console에서 기어(P/R/N/D), 브레이크, 와이퍼 등을 바꿔 보세요.'
Write-Host ' - 에뮬레이터를 껐다 켜면 이 스크립트를 다시 실행하세요(포트 포워딩이 초기화됩니다).'
Write-Host ' - 단위 테스트는 tools\run-tests.bat'
