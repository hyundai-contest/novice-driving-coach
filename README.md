# 초보운전 코치

> **연수가 끝나 혼자 운전하는 초보에게, 차가 직접 조작을 보고 그 순간 알려주는 Pleos Connect 운전 코치 앱.**

폰으로는 알 수 없는 깜빡이·기어·핸들·상향등·문·창문 조작을 차량 신호로 읽어, 놓친 순간에 음성 한 문장으로 알려주고 실력이 느는 과정을 숫자로 보여줍니다. 시동을 걸면 알아서 시작하고, 기록은 차 밖으로 나가지 않습니다.

| 항목 | 내용 |
| --- | --- |
| 앱 이름 | 초보운전 코치 (패키지 `com.example.hyundai_contest`, 버전 1.0) |
| 대상 | Pleos Connect(Android Automotive) 차량용 앱 |
| APK | [Releases](https://github.com/hyundai-contest/novice-driving-coach/releases)의 `초보운전코치-debug.apk` |
| 시연 영상 | [Releases](https://github.com/hyundai-contest/novice-driving-coach/releases)의 `demo-video.mp4` (에뮬레이터 화면 녹화) |
| 기획안 | [`docs/contest/접수양식-02-앱소개.md`](docs/contest/접수양식-02-앱소개.md) |
| 문서 안내 | 설계 [`docs/design.md`](docs/design.md) · 시연 순서 [`demo/README.md`](demo/README.md) |

## APK 파일 위치

- 받는 곳: **[GitHub Releases](https://github.com/hyundai-contest/novice-driving-coach/releases)** 의 `초보운전코치-debug.apk` (디버그 서명, 약 57.5 MB)
- APK와 영상은 저장소에 넣지 않습니다(`.gitignore`). 소스에서 빌드하면 `app/build/outputs/apk/debug/app-debug.apk`에 생깁니다.
- SHA-256 (2026-11-13 빌드): `4b4cffa4c6f41de372f48c401bb025a0d8449419602a160911392bbfe5281bb0`
- 서명: 안드로이드 디버그 키. 릴리스 서명 설정은 아직 없습니다.

## 실행 방법

### 1) 제출 APK로 실행 (소스 빌드 불필요)

1. Pleos Connect 에뮬레이터(`Pleos_Connect_v2.1.0`)를 부팅합니다.
2. APK를 설치합니다.

   ```bash
   adb install -r 초보운전코치-debug.apk
   ```

3. 앱 목록에서 **초보운전 코치**를 실행하고, 권한 창에서 위치와 차량 속도를 허용합니다.
4. 이후에는 앱을 열 필요가 없습니다. 시동(부팅)과 함께 코칭 서비스가 돌고, 기어가 P에서 벗어나면 주행 기록이 시작됩니다. 설치 직후나 강제 종료 뒤에는 앱을 한 번 열어야 자동 시작이 동작합니다(안드로이드 규칙).

기록이 없는 상태에서 화면을 채워 보려면 제목 **"초보운전 코치"를 길게 눌러** 시연 패널을 열고 **12주 데모 기록 불러오기**를 누릅니다.

### 2) 소스에서 빌드해 실행 (시연 패널·시연 스크립트 포함)

준비물: Android Studio(AGP 9.5, Gradle 9.6, Kotlin 2.2), Pleos Connect 에뮬레이터. 빌드에 쓰는 JDK는 Gradle이 자동으로 받습니다.

```bash
./gradlew :app:testDebugUnitTest      # 단위 테스트 (69개)
./gradlew :app:installDebug           # 에뮬레이터에 설치
```

```bash
adb forward tcp:8088 tcp:8088         # Virtual Console: http://127.0.0.1:8088/
```

차량 신호를 순서대로 넣어 전체 흐름(출발 → 회전 → 주차 → 하차)을 보려면 시연 스크립트를 씁니다. 자세한 순서는 [`demo/README.md`](demo/README.md).

```powershell
powershell -ExecutionPolicy Bypass -File demo\demo.ps1        # 단계마다 Enter
powershell -ExecutionPolicy Bypass -File demo\demo.ps1 -Auto  # 끝까지 자동
```

## 테스트 환경 및 필요한 설정

| 항목 | 값 |
| --- | --- |
| 개발·테스트 PC | Windows 10, Android Studio |
| 빌드 | AGP 9.5.1, Gradle 9.6.0, Kotlin 2.2.10, compileSdk/targetSdk 37, minSdk 34 |
| 의존 저장소 | Google, Maven Central, Pleos SDK 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/` (빌드할 때만 필요) |
| 실행 환경 | Pleos Connect 에뮬레이터, 앱 사용자 **user 10** |
| 차량 신호 조작 | 에뮬레이터 Virtual Console (`adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088/) |
| 네트워크 | 필요 없음(서버 통신 없음). 기록은 기기 내 SQLite에만 저장하고 운전자 프로필별로 분리됩니다 |

필요한 설정과 권한

- 첫 실행 화면에서 권한을 받습니다. 자동화나 재설치 때는 직접 줄 수 있습니다.

  ```bash
  adb shell pm grant --user 10 com.example.hyundai_contest android.car.permission.CAR_SPEED
  adb shell pm grant --user 10 com.example.hyundai_contest android.permission.ACCESS_FINE_LOCATION
  adb shell pm grant --user 10 com.example.hyundai_contest android.permission.ACCESS_BACKGROUND_LOCATION
  ```

- 차량 속도 권한이 없으면 GPS 속도로 대신합니다. 위치 권한이 없으면 "자주 놓치는 곳"이 비어 있습니다. 위치 "항상 허용"이 있어야 앱을 열지 않은 부팅 자동 시작에서도 위치를 받습니다.
- 에뮬레이터를 재부팅하면 `adb forward`를 다시 설정합니다.
- **시연 스크립트를 돌리는 동안에는 Virtual Console 브라우저 탭을 닫습니다.** 열려 있으면 콘솔 페이지의 주행 시뮬레이션이 스크립트가 넣은 속도를 덮어씁니다.
- 에뮬레이터에서도 Pleos 온디바이스 TTS로 음성이 나옵니다(PC 스피커). 로캘은 국가까지 넣은 `ko_KR`로 지정해야 합니다. 언어만 넣은 `ko`는 엔진이 "unsupported locale"로 거부합니다. 말한 문장은 `adb logcat -s Coach`의 `announce:` 줄로도 확인할 수 있습니다.
- 에뮬레이터에서만 다른 점: 조향각 부호가 콘솔 입력과 반대라 앱이 뒤집어 읽고(`STEERING_SIGN`), 콘솔에 운전석 벨트 조작이 없어 벨트는 SDK 수신만 확인했습니다. 후진 기어에서는 차량 기본 후방 카메라가 앱 화면을 덮습니다.
- 검증은 에뮬레이터 기준입니다. 감지 기준값(회전 조향각 90° 등)은 실차 주행 로그로 보정이 필요합니다.

## 개발에 사용한 에뮬레이터와 화면 규격

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect 에뮬레이터 **v2.1.0** (AVD 이름 `Pleos_Connect_v2.1.0`) |
| 시스템 이미지 | Android Automotive, API 34, x86_64 |
| 화면 | **2560 × 1440 px**, 밀도 160 dpi(1dp = 1px), 가로 화면 |
| 화면 구성 | 왼쪽 차량 패널 + 오른쪽 앱 창(앱 창 약 1680 × 1200 px) |
| 대응 | 오른쪽 앱 창 크기에 맞춘 가로 레이아웃 하나입니다. 팔 길이 거리에서 읽히도록 전체를 1.4배 키웠습니다(`CAR_SCALE`). 다른 창 크기는 아직 확인하지 않았습니다 |

## 구조

```
Pleos Vehicle SDK · AAOS 차량 속도 · 플랫폼 위치
  → PleosSignalSource → CarState (250ms 샘플링)
      → DetectionEngine (순수 로직) → Record → CoachDb (SQLite) → 화면 4개
      → Coach (코칭 문장) → Announcer (Pleos TTS → 기본 TTS)
```

| 패키지 | 역할 |
| --- | --- |
| `signal` | SDK 구독 → `CarState`, 샘플링·저장·음성(`Recorder`), TTS(`Announcer`) |
| `detect` | 감지 엔진, 기준값(`DetectorConfig`), 음성 문구 |
| `coach` | 주행 요약, 팁 한 가지, 주차 피드백 문장 |
| `store` | 주행·기록 저장, 장소 묶기 |
| `ui` | Jetpack Compose 화면 |
| `demo` | 시연용 12주 합성 기록 |

- 앱 화면은 달리는 동안에도 보입니다(주행 중 표시 허용 선언). 그래서 주행 중 화면에는 누를 것을 두지 않았고, 새 코칭 문장은 8초 뒤 목록으로 내려갑니다.
- 음성은 한 사건에 한 번, 사실만 짧게 말합니다. 회전 중에는 말하지 않고 기록만 합니다.
- 속도는 차량 속도 신호를 쓰고, 3초 넘게 갱신이 없으면 "모름"으로 보고 GPS 속도로 넘어갑니다.
- 차량 제어 권한은 쓰지 않습니다(읽기만).
- 음성을 끄거나 줄이는 설정은 아직 없습니다.

## 코칭 규칙 (9종)

수치는 `detect/DetectorConfig.kt` 한 곳에 있습니다. 주행 구간은 기어가 P에서 벗어나면 시작하고, P단에서 운전석 문이 열리면 끝납니다.

| 코칭 | 조건 | 주요 값 | 그 순간 음성 |
| --- | --- | --- | --- |
| 깜빡이 시점 (제때 / 늦음 / 없음) | D단에서 큰 조향이 유지되는 회전. 회전 시작 전부터 같은 방향 깜빡이를 봄 | 조향 90°, 1초, 5km/h 이상, 5초 전부터, 1초 전 이후는 늦음 | 없음 (기록만) |
| 빠른 회전 | 회전 구간의 최고 속도 | 30km/h | 없음 (기록만) |
| 깜빡이 켜둔 채 주행 | 깜빡이를 켠 채 직진이 이어짐 | 20km/h, 조향 15° 미만, 10초 | "왼쪽 깜빡이가 켜져 있어요" |
| 상향등 켜둔 채 주행 | 상향등을 켠 채 주행이 이어짐 | 20km/h, 30초 | "상향등이 켜져 있어요" |
| 후진 주차 | D(또는 N)에서 R로 바꾸면 시작, P에서 끝. 기어 변경·핸들 되감기 횟수와 시간을 셈 | 되감기 90°, 20km/h 넘으면 주차 아님 | "주차 완료. 기어를 3번 바꿨어요. 평소 5번보다 줄었어요" |
| 덜 멈춘 변속 | 주차 구간에서 차가 움직이는 중에 D↔R을 바꿈 | 3km/h | 없음 (주차 기록에 횟수) |
| 빠른 후진 | R단에서의 속도 | 7km/h | "후진이 빨라요. 천천히 가세요" |
| 출발 점검 | 움직이기 시작할 때 벨트가 풀려 있거나 문이 열려 있음 | 5km/h, 주행당 1번 | 문만 "운전석 문이 열려 있어요" (벨트는 차가 이미 알림) |
| 하차 점검 | P단이 아닌데 운전석 문을 엶 / 창문·전조등(수동)을 켜둔 채 하차 | 3km/h 미만 | "기어가 P가 아니에요. P에 놓고 내리세요" / "조수석 창문이 열려 있어요" |

주차 음성의 "평소"는 기기에 저장된 최근 주차 10번의 평균입니다. 비교 대상은 다른 운전자가 아니라 자기 기록뿐입니다. 주차 지표는 후진 주차만 봅니다. 벨트는 에뮬레이터에서 값이 바뀌지 않아, 한 번도 "맴"으로 바뀐 적 없는 센서는 없는 것으로 취급합니다. 상향등은 어두운 빈 도로에서는 정상 사용이라 켤 때마다 한 번만 알립니다.

## Pleos SDK 사용

SDK 호출은 `signal/PleosSignalSource.kt`와 `signal/Announcer.kt` 안에만 있습니다.

| SDK | 버전 | 용도 | 권한 |
| --- | --- | --- | --- |
| Vehicle | 2.3.2 | 깜빡이·기어·조향각·문(좌석별)·창문(좌석별)·전조등·상향등·운전석 벨트 | `CAR_LIGHTS` `CAR_DRIVE` `CAR_DOORS` `CAR_WINDOWS` `CAR_SEATS` |
| TextToSpeech | 3.3.1 | 그 순간의 코칭 한 문장(ON_DEVICE), 실패하면 안드로이드 기본 TTS | `TTS_SERVICE` |
| AAOS Car API | — | 차량 속도(`PERF_VEHICLE_SPEED`) | `CAR_SPEED`(런타임) |
| 플랫폼 위치 | — | 장소 묶기, 차량 속도를 못 받을 때의 속도 | `ACCESS_FINE_LOCATION`(런타임) |

차가 주지 않는 신호에 의존하는 지표는 0이 아니라 "이 차량에서 측정할 수 없음"으로 표시합니다.

## 시연 스크립트

- `demo/demo.ps1`: PC에서 Virtual Console(HTTP)과 에뮬레이터 GPS로 차량 신호를 넣습니다. 신호가 실제 SDK 경로로 들어오므로 시연에서 도는 감지 코드는 실차와 같습니다. 출발 → 깜빡이 없이 빠른 우회전 → 깜빡이 켜고 좌회전 → 깜빡이 켜둔 채 직진 → 상향등 켜둔 채 주행 → D단에서 문 열기 → 후진 주차 → 창문 연 채 하차 순서입니다.
- 12주 데모 기록(`demo/SyntheticHistory.kt`): 시연 패널에서 불러오는 결정적 합성 데이터입니다. 성장 기록과 자주 놓치는 곳 화면을 채우는 용도이고, 불러오면 기존 기록을 모두 지웁니다. 성장 기록 화면의 12주 추이는 이 합성 데이터입니다.
