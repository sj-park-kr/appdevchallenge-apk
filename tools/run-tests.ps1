# 단위 테스트 실행 후 HTML 보고서 열기
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Write-Host '[오류] JAVA_HOME이 없거나 잘못됐습니다. JDK 17 경로를 JAVA_HOME 환경변수로 지정하세요.' -ForegroundColor Red
    exit 1
}
Push-Location $root
try { & (Join-Path $root 'gradlew.bat') test; $rc = $LASTEXITCODE } finally { Pop-Location }
if ($rc -eq 0) { Write-Host '[성공] 모든 테스트 통과.' -ForegroundColor Green }
else { Write-Host '[실패] 테스트가 실패했습니다. 보고서를 확인하세요.' -ForegroundColor Red }
$rep = Join-Path $root 'app\build\reports\tests\testDebugUnitTest\index.html'
if (Test-Path $rep) { Start-Process $rep }
exit $rc
