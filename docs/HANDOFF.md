# 차마고찌 완성도 개선 인수인계

## 현재 상태

공유 개인정보 처리, 저장 실패 안내·재시도, 장소 정보 일괄 삭제와 차량 연동 예외 회귀 테스트를 구현했다. 변경 근거와 테스트별 설명은 [QUALITY.md](QUALITY.md), 실행 환경은 [SETUP.md](SETUP.md)를 먼저 읽는다.

최근 완료한 검증:

- Debug 테스트 198개, Release 테스트 190개 모두 통과.
- APK 빌드와 `lintDebug` 성공. `git diff --check` 통과.
- 1차 개선 당시 Pleos Connect Android 14 에뮬레이터에 설치하고 예시 상태의 공유 화면 실행·캡처를 확인했다.
- 추가 개선의 저장 실패·삭제·권한·신호 누락·복원·화면 전환은 JVM 및 Robolectric 테스트로 확인했다. 추가 개선 전체를 실제 차량에서 검증한 것은 아니다.

재실행 명령(PowerShell, JDK 17·Android SDK 35):

```powershell
.\gradlew.bat test assembleDebug lintDebug '-Proborazzi.test.record=true'
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`, 테스트 보고서는 `app/build/reports/tests/`, 화면 캡처는 `app/build/shots/`에 생성된다. 빌드 산출물은 커밋하지 않는다.

## 다음 작업 순서

아래는 이번에 수정하지 않은 항목이다. 코드로 확인한 구조와 그 구조에서 예상되는 위험을 구분했다. 실제 장애 재현은 후속 작업에서 수행해야 한다.

### 1. 두 저장 파일의 일관성 보장

- 관련 코드: [PetStore.kt](../app/src/main/java/com/carmagotchi/app/data/PetStore.kt), [SessionSaver.kt](../app/src/main/java/com/carmagotchi/app/platform/SessionSaver.kt).
- 확인한 구조: `core.bin`을 교체한 다음, 변경된 `history.bin`을 교체한다. 각각의 파일 교체는 원자적이지만 두 파일을 함께 커밋하는 장치는 없다.
- 위험: 첫 파일만 교체된 뒤 프로세스 종료나 두 번째 쓰기 실패가 발생하면 서로 다른 시점의 상태를 읽을 수 있다. 현재 저장 재시도는 프로세스가 살아 있을 때의 복구다. 파일 사이에서 종료되는 상황은 아직 재현하지 않았다.
- 할 일: 두 파일 사이의 실패·종료를 주입하는 테스트부터 추가한다. 세대 번호와 커밋 포인터, 복구 저널 또는 단일 스냅샷 중 현재 데이터 크기에 맞는 방식을 선택한다. 기존 저장 파일 호환성을 유지한다.
- 완료 기준: 어느 쓰기 단계에서 종료돼도 이전 또는 새 스냅샷 하나로 일관되게 복원되며 주행·보상이 중복되거나 누락되지 않는다.

### 2. 개인정보 삭제의 디스크 잔여 파일 처리

- 관련 코드: [PetStore.kt](../app/src/main/java/com/carmagotchi/app/data/PetStore.kt), [Privacy.kt](../app/src/main/java/com/carmagotchi/app/domain/Privacy.kt), [PetSession.kt](../app/src/main/java/com/carmagotchi/app/platform/PetSession.kt).
- 완료한 범위: 현재 사용하는 스냅샷과 메모리 캐시에서 장소를 지우며, 삭제 후 정상 저장·재로딩을 테스트했다. 동승자 이름도 현재 기록에서 익명화한다.
- 확인한 구조: 읽기 실패 파일은 `*.corrupt`로 보관된다. 장소·동승자 삭제는 전체 삭제와 달리 이 파일이나 과거 쓰기에서 남은 임시 파일을 정리하지 않는다.
- 할 일: 삭제 대상의 범위를 정의하고, 저장 성공 시 이전 개인정보를 담은 잔여 파일까지 정리하는 흐름을 구현한다. 파일 삭제 실패도 성공으로 처리하지 않도록 검토한다. 저장 실패 중에는 이전 디스크 데이터가 남을 수 있다는 안내를 유지한다.
- 완료 기준: 손상·임시 파일이 존재하는 상태에서 삭제하고 재시작해도 삭제한 정보가 앱 데이터에 남거나 다시 나타나지 않는다. 재로딩 가능한 현재 기록과 관련 없는 성장·추억은 유지한다.

### 3. 차량 신호의 신선도와 실제 기기 예외 검증

- 관련 코드: [VehicleSignalSource.kt](../app/src/main/java/com/carmagotchi/app/platform/VehicleSignalSource.kt), [PetService.kt](../app/src/main/java/com/carmagotchi/app/platform/PetService.kt), [RuntimePetController.kt](../app/src/main/java/com/carmagotchi/app/RuntimePetController.kt).
- 완료한 범위: 속도 권한 거부·null 신호를 실제 0과 구분하며, 신호 누락·복구와 체크포인트 복원을 자동 테스트했다. P↔D에 따른 화면 숨김도 테스트했다.
- 확인한 구조: 신호 소스의 심장박동은 마지막 원시 값을 다시 발행한다. 현재 테스트는 실제 SDK 연결을 물리적으로 끊어 기어·속도 등 마지막 값이 얼마나 오래 유지되는지를 검증하지 않았다.
- 할 일: 신호별 마지막 수신 시각과 연결 상태를 확인하고, 지원하지 않는 신호와 끊긴 신호를 구분한다. 기어·주행 규제 상태를 확인할 수 없을 때 사용할 화면 제한 정책을 정한다.
- 기기 시험: OS 속도 권한 거부·재허용, 차량 서비스 연결 끊김·복구, 화면 꺼짐·켜짐, 앱 프로세스 강제 종료·재시작, P→D→P, 주행 중 UX 제한 변경, 장시간 운행을 수행한다. 시험은 통제된 환경에서 한다.
- 완료 기준: 불명확하거나 오래된 신호로 정차 전용 조작이 노출되지 않고, 복구 후 주행 기록과 효과음 횟수가 중복되지 않는다. 기기·OS·빌드·재현 절차·로그와 결과를 기록한다.

### 4. 실제 사용 화면에서 저장 실패·삭제 확인

- 자동 테스트: [SessionSaverTest.kt](../app/src/test/java/com/carmagotchi/app/platform/SessionSaverTest.kt), [PlacePrivacyTest.kt](../app/src/test/java/com/carmagotchi/app/platform/PlacePrivacyTest.kt), [ScreensTestReliability.kt](../app/src/test/java/com/carmagotchi/app/ui/ScreensTestReliability.kt).
- 할 일: 기기에서 쓰기 실패를 주입해 실패 안내·수동 재시도·자동 재시도·성공 후 안내 해제와 주행 중 안내 숨김을 확인한다. 장소 삭제 확인창을 취소한 경우와 실행한 경우를 각각 확인하고 재부팅 후 결과도 확인한다.
- 완료 기준: 작은 화면·가로·세로·어두운 테마에서 안내와 버튼이 가려지지 않는다. 저장 실패 후 앱을 종료하면 최신 변경을 잃을 수 있음을 사용자에게 알린다. 강제 종료 전 저장에 성공한 데이터의 복원은 별도로 검증한다.

### 5. 기획·아키텍처 문서와 현재 제품 정합성

- README와 SETUP의 임시 화면 안내는 현재 펫 앱에 맞춰 수정했다.
- [PRD.md](PRD.md), [architecture.md](architecture.md)와 기존 ADR에는 이전 법인 차량 운행기록부 기획이 남아 있다. 현재 구현을 설명하는 최신 명세로 사용하지 않는다.
- 할 일: 이전 결정의 기록은 보존하되 현재 유효한 명세와 구분하고, 실제 펫 기능·데이터 저장·주행 규제·공유 방식을 기준으로 문서와 링크를 정리한다.
- 완료 기준: 신규 작업자가 README에서 현재 유효한 요구사항과 설계를 찾을 수 있고, QR을 이미지 저장 링크로 오해하거나 운행기록부 요구사항을 현재 제품 요구사항으로 적용하지 않는다.

## 작업 시 유지할 사항

- 이름·장소의 공유 선택은 기본 꺼짐이며, 카드 제목과 QR에 같은 공개 범위를 적용한다.
- QR은 이미지 링크가 아닌 UTF-8 텍스트이며 300바이트 이하다.
- 장소 삭제는 위치 사용을 함께 끈다. 늦게 들어온 내비 콜백과 대기 중 샘플이 장소를 되살리지 않아야 한다.
- 저장 실패 후 변경 표시를 유지하고, 재시도에서는 최신 상태를 저장한다. 실패 안내와 재시도 버튼은 정차 중에만 노출한다.
- Compose 화면 테스트는 debug 테스트 manifest가 필요하므로 Release 실행 대상에서 제외한다.
- 개인정보, VIN 원문, 실제 이름·연락처·소속을 로그·커밋·시험 증거에 남기지 않는다.
