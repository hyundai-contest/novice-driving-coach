<#
  초보운전 코치 시연 스크립트
  Pleos Virtual Console(HTTP)과 에뮬레이터 GPS(adb emu geo fix)로 차량 신호를 주입합니다.
  신호는 실제 Vehicle SDK 경로로 앱에 들어갑니다.

  사용법:  powershell -ExecutionPolicy Bypass -File demo\demo.ps1        (단계마다 Enter)
           powershell -ExecutionPolicy Bypass -File demo\demo.ps1 -Auto  (기다리지 않음)
#>
param([switch]$Auto)
$ErrorActionPreference = 'Stop'

$Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$Console = 'http://127.0.0.1:8088'

# 시연 장소 (앱의 Places.DEMO와 같은 좌표)
$Home_    = @(37.5712, 126.9690)
$CityHall = @(37.5658, 126.9772)
$Office   = @(37.5745, 126.9840)

# 하트비트가 잠깐 늦으면 콘솔이 "emulator_inactive"로 거부합니다. 세션을 갱신하고 몇 번 다시 시도합니다.
function Put([string]$path) {
    for ($try = 1; ; $try++) {
        try { Invoke-RestMethod -Method Put "$Console/vehicle/$path" | Out-Null; return }
        catch {
            if ($try -ge 5) { throw }
            try { Invoke-RestMethod -Method Post "$Console/browser/session?client=demo-script" | Out-Null } catch {}
            Start-Sleep -Milliseconds 500
        }
    }
}

# 콘솔은 속도와 조향각을 한 요청으로 받으므로, 둘 다 기억해 두고 함께 보냅니다.
$script:Kph = 0
$script:Deg = 0
function Controls { Put "controls?speed=$($script:Kph)&accelerator=0&brake=0&steering=$($script:Deg)" }

# 위치는 에뮬레이터 GPS로, 속도는 콘솔의 차량 속도로 넣습니다(앱은 차량 속도를 씁니다).
function Geo([double]$lat, [double]$lng, [double]$kph) {
    $knots = [math]::Round($kph / 1.852, 1)
    & $Adb emu geo fix $lng $lat 30 8 $knots | Out-Null
    $script:Kph = [int]$kph
    Controls
}

# 콘솔 조향각: 음수가 왼쪽입니다.
function Steer([int]$deg) { $script:Deg = $deg; Controls }

# from → to 구간을 seconds 동안 kph로 이동하며 1초마다 위치를 갱신합니다.
function Drive($from, $to, [double]$kph, [int]$seconds) {
    for ($i = 1; $i -le $seconds; $i++) {
        $f = $i / $seconds
        Geo ($from[0] + ($to[0] - $from[0]) * $f) ($from[1] + ($to[1] - $from[1]) * $f) $kph
        Start-Sleep -Seconds 1
    }
}

function Step([string]$title) {
    Write-Host ""
    Write-Host "▶ $title" -ForegroundColor Cyan
    # 이 스크립트의 하트비트가 1개입니다. 그보다 많으면 콘솔 페이지가 다시 열려 속도를 덮어쓰는 중입니다.
    try {
        if ((Invoke-RestMethod "$Console/vehicle/state").browser.clients -gt 1) {
            Write-Host '  경고: Virtual Console 탭이 열려 있어 속도가 어긋날 수 있습니다. 탭을 닫아 주세요.' -ForegroundColor Yellow
        }
    } catch {}
    if (-not $Auto) { Read-Host '  Enter를 누르면 진행합니다' | Out-Null }
}

function Reset-Car {
    Put 'turn-signal?mode=Off'
    Put 'lights?feature=high_beam&value=false'
    Steer 0
    Put 'body?door_fl=false&window_fl=0&window_fr=0&window_rl=0&window_rr=0'
    Geo $Home_[0] $Home_[1] 0
    Put 'gear?gear=Park'
}

& $Adb forward tcp:8088 tcp:8088 | Out-Null

# 콘솔 웹 페이지가 열려 있으면 페이지의 주행 시뮬레이션(가속·관성)이 스크립트가 넣은 속도를 덮어씁니다.
# 이 스크립트의 하트비트가 시작되기 전에 접속 중인 브라우저가 있으면 닫을 때까지 기다립니다.
$warned = $false
while ((Invoke-RestMethod "$Console/vehicle/state").browser.clients -gt 0) {
    if (-not $warned) { Write-Host 'Virtual Console 브라우저 탭을 닫아 주세요. 닫으면 자동으로 시작합니다.' -ForegroundColor Yellow; $warned = $true }
    Start-Sleep -Seconds 1
}
# 콘솔은 브라우저 세션이 3초 안에 갱신되지 않으면 입력을 거부하므로 하트비트를 계속 보냅니다.
$heartbeat = Start-Job -ScriptBlock {
    while ($true) {
        try { Invoke-RestMethod -Method Post 'http://127.0.0.1:8088/browser/session?client=demo-script' | Out-Null } catch {}
        Start-Sleep -Milliseconds 800
    }
}
Start-Sleep -Seconds 2

try {
    Step '준비: 집 앞, P단, 모든 창문 닫힘'
    Reset-Car
    Start-Sleep -Seconds 2

    # 차량 속도를 콘솔로 넣습니다. 앱은 주행 중 표시가 허용된 화면이라, 달리는 동안에도 실시간 코칭 화면이 보입니다.
    Step '시연 1-1  출발 → 시청 사거리 방향으로 주행  (앱: 실시간 코칭 화면으로 자동 전환)'
    Put 'gear?gear=Drive'
    Steer 0
    Drive $Home_ $CityHall 30 6

    Step '시연 1-2  시청 사거리 우회전 (깜빡이 없이, 시속 34km)  →  기록: 빠른 회전 + 깜빡이 없이 회전'
    Steer 120
    Drive $CityHall $CityHall 34 3
    Steer 0
    Drive $CityHall $CityHall 30 1

    Step '시연 1-3  이번에는 깜빡이를 먼저 켜고 좌회전  →  기록: 깜빡이 켜고 회전 (초록)'
    Put 'turn-signal?mode=BlinkLeft'
    Drive $CityHall $CityHall 25 3
    Steer -120
    Drive $CityHall $CityHall 20 3
    Steer 0
    Put 'turn-signal?mode=Off'
    Drive $CityHall $CityHall 25 1   # 빠른 회전 기준(30) 아래로 출발

    Step '시연 1-4  차선 변경 후 왼쪽 깜빡이를 켜둔 채 직진  →  10초 뒤 음성 코칭 + 기록'
    Put 'turn-signal?mode=BlinkLeft'
    Drive $CityHall $Office 40 13
    Put 'turn-signal?mode=Off'

    Step '시연 1-5  상향등을 켠 채 주행  →  30초 뒤 "상향등이 켜져 있어요"'
    Put 'lights?feature=high_beam&value=true'
    Drive $Office $Office 40 33
    Put 'lights?feature=high_beam&value=false'

    Step '시연 2-1  회사 주차장 도착, D단인 채로 운전석 문 열기  →  "기어가 P가 아니에요"'
    Geo $Office[0] $Office[1] 0
    Start-Sleep -Seconds 2
    Put 'body?door_fl=true'
    Start-Sleep -Seconds 4
    Put 'body?door_fl=false'

    Step '시연 2-2  후진 주차: 빠른 후진("후진이 빨라요") → 덜 멈춘 채 전진 → 후진 → P단  →  "주차 완료. 기어를 3번 바꿨어요…"'
    Put 'gear?gear=Reverse'
    Geo $Office[0] $Office[1] 9
    Steer -200
    Start-Sleep -Seconds 4
    Geo $Office[0] $Office[1] 4
    Start-Sleep -Seconds 1
    Put 'gear?gear=Drive'          # 차가 아직 움직이는 중에 변속
    Steer 200
    Start-Sleep -Seconds 3
    Geo $Office[0] $Office[1] 0
    Start-Sleep -Seconds 1
    Put 'gear?gear=Reverse'
    Geo $Office[0] $Office[1] 4
    Steer -200
    Start-Sleep -Seconds 3
    Steer 0
    Geo $Office[0] $Office[1] 0
    Start-Sleep -Seconds 2
    Put 'gear?gear=Park'
    Start-Sleep -Seconds 6

    Step '시연 2-3  조수석 창문을 연 채 운전석 문 열림(하차)  →  "조수석 창문이 열려 있어요"'
    Put 'body?window_fr=40'
    Start-Sleep -Seconds 2
    Put 'body?door_fl=true'
    Start-Sleep -Seconds 4

    Step '정리: 문 닫기, 창문 닫기, 집으로 위치 복귀  (앱: 주행 코칭 → 성장 기록 → 자주 놓치는 곳)'
    Put 'body?door_fl=false&window_fr=0'
    Reset-Car
} finally {
    Stop-Job $heartbeat | Out-Null
    Remove-Job $heartbeat | Out-Null
    Write-Host ""
    Write-Host '시연 스크립트 종료' -ForegroundColor Green
}
