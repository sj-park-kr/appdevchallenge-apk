# 차마고찌 (Carmagotchi)

Pleos Connect(Android Automotive OS)에서 차량 신호에 반응하며 함께 자라는 차량 내장 펫 앱입니다.
주행·날씨·동승·충전 등의 경험이 성장, 길동무 도감, 장소 도장과 추억 앨범으로 남습니다.

## 상태
펫 부화·교감·성장·모습 꾸미기, 도감·앨범·공유 카드, 설정과 차량 신호 연동이 구현되어 있습니다.
상세 화면은 P단에서 사용하며 주행 중에는 숨깁니다. 기억은 기기 내 암호화 파일로 저장합니다.

## 실행과 검증

JDK 17과 Android SDK 35가 필요합니다. 자세한 준비 절차는 [설정 가이드](docs/SETUP.md)를 확인하세요.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- 테스트 보고서: `app/build/reports/tests/testDebugUnitTest/index.html`
- 화면 캡처: `app/build/shots/` (`-Proborazzi.test.record=true`로 화면 테스트 실행 시)
- 에뮬레이터 실행·설치: `tools\run-emulator.bat`

공유 카드의 이름과 장소는 기본으로 숨깁니다. 각각의 스위치를 켜면 카드 제목과 QR에도 반영됩니다.
QR은 추억의 **글자**를 담으며, 카드 이미지는 화면을 촬영해 저장합니다.
저장에 실패하면 정차 화면에서 재시도할 수 있으며 자동으로도 다시 시도합니다. 저장될 때까지 앱을 종료하지 마세요.
설정의 ‘장소 정보 모두 지우기’로 과거 주행·앨범과 진행 중 주행의 장소를 함께 지우고 위치 사용을 끌 수 있습니다.

## 문서
- [PRD](docs/PRD.md) — 펫 기능·화면·차량 신호 판정·수용 기준·시연 시나리오
- [아키텍처](docs/architecture.md) — 모듈, 데이터 흐름, 저장 스키마, 데이터 소스 교체, 테스트 전략
- [ADR (결정 기록)](docs/adr/README.md)
- [완성도 개선 기록](docs/QUALITY.md) — 조사 결과·반영 사항·검증 방법·후속 후보
- [인수인계](docs/HANDOFF.md) — 남은 작업·우선순위·검증 및 완료 기준

## 사용하는 차량 데이터
| 용도 | API |
|---|---|
| 주행 전·후 계기판 거리 | Vehicle State `getCurrentDistanceDriven` |
| 운행 거리·시간 교차 검증 | `getTripCurrentDrivingDistance`, `getTripCurrentDrivingTime` |
| 주유 감지 | `getFuelPercent`, `getEvBatteryStateOfCharge` |
| 차량 식별 | `getVINInfo` |
| 출발·도착 위치, 목적지 | NaviHelper `getCurrentLocationInfo`, `getDestinationInfo`, `getRecentDestinationInfo` |
| 시동·기어·속도 | AAOS `CarPropertyManager` |

## 블라인드 심사
이 저장소는 심사 제출용입니다. 코드·커밋·이슈에 실명·연락처·소속을 쓰지 않습니다.
