# TrueLog (가칭)

시동만 걸면 차가 계기판 거리와 실제 목적지로 직접 써 주는, 사람이 고칠 수 없는 법인 차량 운행기록부.
Pleos Connect(Android Automotive OS) 차량 내장 앱.

## 상태
개발 시작 전. 첫 단계는 에뮬레이터에서 차량 데이터(계기판 거리·목적지·연료량·시동·기어)가 읽히는지 확인하는 것.

## 사용하는 차량 데이터
| 용도 | API |
|---|---|
| 주행 전·후 계기판 거리 | Vehicle State `getCurrentDistanceDriven` |
| 운행 거리·시간 교차 검증 | `getTripCurrentDrivingDistance`, `getTripCurrentDrivingTime` |
| 주유 감지 | `getFuelPercent`, `getEvBatteryStateOfCharge` |
| 차량 식별 | `getVINInfo` |
| 출발·도착 위치, 목적지 | NaviHelper `getCurrentLocationInfo`, `getDestinationInfo`, `getRecentDestinationInfo` |
| 거래처(업무) 판정 | `getBookmarkInfo` |
| 시동·기어·속도 | AAOS `CarPropertyManager` |

## 블라인드 심사
이 저장소는 심사 제출용입니다. 코드·커밋·이슈에 실명·연락처·소속을 쓰지 않습니다.
