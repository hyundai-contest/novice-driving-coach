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

function Geo([double]$lat, [double]$lng, [double]$kph) {
    $knots = [math]::Round($kph / 1.852, 1)
    & $Adb emu geo fix $lng $lat 30 8 $knots | Out-Null
}

# 콘솔 조향각: 음수가 왼쪽입니다.
function Steer([int]$deg) { Put "controls?speed=0&accelerator=0&brake=0&steering=$deg" }

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
    if (-not $Auto) { Read-Host '  Enter를 누르면 진행합니다' | Out-Null }
}

function Reset-Car {
    Put 'turn-signal?mode=Off'
    Steer 0
    Put 'body?door_fl=false&window_fl=0&window_fr=0&window_rl=0&window_rr=0'
    Put 'gear?gear=Park'
    Geo $Home_[0] $Home_[1] 0
}

& $Adb forward tcp:8088 tcp:8088 | Out-Null
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

    # 콘솔의 차량 속도는 0으로 둡니다. Pleos는 차량 속도가 0보다 크면 일반 앱 화면을 가리는데,
    # 시연에서는 화면을 보여줘야 하므로 앱이 쓰는 속도(GPS)만 geo fix로 넣습니다.
    # 실제 주행에서는 화면 대신 음성 코칭만 나간다는 점을 발표에서 짚어주세요.
    Step '시연 1-1  출발 → 시청 사거리 방향으로 주행  (앱: 실시간 코칭 화면으로 자동 전환)'
    Put 'gear?gear=Drive'
    Steer 0
    Drive $Home_ $CityHall 30 6

    Step '시연 1-2  시청 사거리 우회전 (깜빡이 없이)  →  기록: 깜빡이 없이 회전'
    Steer 120
    Drive $CityHall $CityHall 20 3
    Steer 0
    Drive $CityHall $CityHall 30 1

    Step '시연 1-3  이번에는 깜빡이를 먼저 켜고 좌회전  →  기록: 깜빡이 켜고 회전 (초록)'
    Put 'turn-signal?mode=BlinkLeft'
    Drive $CityHall $CityHall 25 3
    Steer -120
    Drive $CityHall $CityHall 20 3
    Steer 0
    Put 'turn-signal?mode=Off'
    Drive $CityHall $CityHall 30 1

    Step '시연 1-4  차선 변경 후 왼쪽 깜빡이를 켜둔 채 직진  →  10초 뒤 음성 코칭 + 기록'
    Put 'turn-signal?mode=BlinkLeft'
    Drive $CityHall $Office 40 13
    Put 'turn-signal?mode=Off'

    Step '시연 2-1  회사 주차장 도착, D단인 채로 운전석 문 열기  →  "기어가 P가 아니에요"'
    Geo $Office[0] $Office[1] 0
    Start-Sleep -Seconds 4   # GPS 속도가 0으로 갱신될 때까지
    Put 'body?door_fl=true'
    Start-Sleep -Seconds 4
    Put 'body?door_fl=false'

    Step '시연 2-2  후진 주차: 후진 → 전진으로 다시 → 후진 → P단  →  "주차 완료. 기어를 3번 바꿨어요…"'
    Put 'gear?gear=Reverse'
    Steer -200
    Start-Sleep -Seconds 3
    Put 'gear?gear=Drive'
    Steer 200
    Start-Sleep -Seconds 3
    Put 'gear?gear=Reverse'
    Steer -200
    Start-Sleep -Seconds 3
    Steer 0
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
