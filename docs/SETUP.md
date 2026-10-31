# 개발 환경 설정과 테스트 가이드 (Windows + Android Studio + Pleos Connect 에뮬레이터)

## 빠른 시작 (스크립트)

1. 처음 한 번만 PC를 **재부팅**합니다(에뮬레이터 가속 기능 적용).
2. `tools\run-emulator.bat`을 **더블클릭**합니다. 에뮬레이터 실행, 앱 빌드·설치·실행, 브라우저 열기까지 자동으로 합니다.
3. 열린 브라우저의 **Virtual Console**에서 기어(P/R/N/D) 등을 바꿔 펫의 반응을 봅니다.
   - 스크립트가 에뮬레이터 시간대를 한국(`Asia/Seoul`)으로 맞춥니다. 직접 켠 에뮬레이터는 `adb shell cmd alarm set-timezone Asia/Seoul`을 한 번 실행하세요. 주행 중 동행 소리는 기기 현지 시각 22~06시에 나지 않습니다.

단위 테스트는 `tools\run-tests.bat`을 더블클릭합니다. 스크립트가 안 되면 아래 수동 절차를 따르세요.

---

이 문서는 Android Studio만 설치된 Windows PC에서 시작해, 차마고찌(Carmagotchi) 앱을 Pleos Connect 에뮬레이터에서 실행하고 시험해 보는 데까지 필요한 모든 단계를 순서대로 설명합니다. 개발 경험이 많지 않아도 따라 할 수 있도록 누를 메뉴를 하나씩 적었습니다.

> **현재 상태 안내**: 부화·펫 방·도감·앨범·공유·설정 화면이 구현되어 있습니다. 차량 신호와 상태 엔진은 단위 테스트로, 화면은 Robolectric Compose 테스트로 확인할 수 있습니다. 실제 차량 신호 연동은 에뮬레이터의 Virtual Console로 확인합니다(11장).

## 0. 이 문서로 할 수 있는 것

아래 순서대로 진행합니다. 처음 한 번만 하면 되는 단계가 대부분입니다.

1. PC 사양과 BIOS 가상화 설정을 확인합니다(1장).
2. 레포를 받아 Android Studio로 엽니다(2장).
3. Gradle이 쓸 JDK를 17로 지정합니다(3장).
4. SDK Manager에서 Android 15(API 35)와 Build-Tools 35.0.0을 받습니다(4장).
5. 에뮬레이터 가속(하이퍼바이저)을 켭니다(5장).
6. Pleos Connect 에뮬레이터 이미지(약 4.1 GB)를 받습니다(6장).
7. Pleos 가상 기기(AVD)를 만듭니다(7장).
8. 앱을 에뮬레이터에서 실행합니다(8장).
9. 단위 테스트를 돌립니다(9장).
10. Virtual Console로 기어·브레이크·와이퍼 같은 차량 신호를 바꿔 봅니다(10장).
11. 시연 시나리오로 앱 동작을 확인합니다(11장).
12. 필요하면 명령줄에서 APK를 만듭니다(12장).

문제가 생기면 13장의 문제 해결 표를 먼저 보세요.

### 이 문서를 읽기 전에: 숨어 있는 메뉴 막대

Android Studio 2026.2는 새 UI를 써서 화면 위쪽의 메뉴 막대(File, View, Tools 등)가 기본으로 숨어 있습니다. 왼쪽 위의 **☰ 아이콘**을 누르면 메뉴가 나옵니다.

이 문서에서 "☰ > Tools > SDK Manager"라고 쓰면, ☰를 누르고 Tools에 마우스를 올린 뒤 SDK Manager를 누르라는 뜻입니다.

### 이 문서에서 쓰는 표기

| 표기 | 뜻 |
|---|---|
| `%LOCALAPPDATA%\Android\Sdk` | Android SDK가 설치된 폴더. Android Studio의 기본 위치입니다. 탐색기 주소창에 그대로 붙여 넣으면 열립니다 |
| `<레포 경로>` | 이 레포를 받아 둔 폴더(예: `C:\dev\appdevchallenge-apk`) |
| `<JDK17 경로>` | JDK 17이 설치된 폴더(3장에서 확인) |
| `<레포 주소>` | GitHub에 있는 이 레포의 주소 |

---

## 1. 준비물과 PC 확인

### 1.1 필요한 것

| 항목 | 기준 |
|---|---|
| 운영체제 | Windows 11 (64비트) |
| Android Studio | 2026.2 이상. 설치만 되어 있으면 됩니다 |
| 메모리(RAM) | 16 GB 이상을 권장합니다. 에뮬레이터가 4 GB, Gradle 빌드가 최대 4 GB(`gradle.properties`의 `-Xmx4g`)를 쓰고 Android Studio도 메모리를 씁니다 |
| 디스크 | Pleos 이미지만 약 4.1 GB입니다. SDK, Gradle 캐시, 가상 기기 데이터까지 합쳐 20 GB 이상 비워 두기를 권장합니다(대략적인 값) |
| 그래픽 카드 | Pleos Connect 에뮬레이터는 그래픽 카드가 **필수**입니다. 아래 표를 보세요 |
| 인터넷 | SDK, 에뮬레이터 이미지, Gradle 의존성을 처음 한 번 내려받을 때 필요합니다 |
| 관리자 권한 | 5장(에뮬레이터 가속)에서 필요할 수 있습니다 |

Pleos 공식 문서가 밝힌 그래픽 카드 사양은 다음과 같습니다.

| 구분 | NVIDIA | AMD |
|---|---|---|
| 권장 | RTX 4060 / 5060 Laptop 8 GB 이상 | Radeon RX 7700S / RX 7600M XT 8 GB급 이상 외장 GPU |
| 최소 | GeForce RTX 3060 Laptop GPU 6 GB 이상 | Radeon RX 6600M 8 GB 또는 RX 6700S 8 GB 이상 |

### 1.2 BIOS 가상화가 켜져 있는지 확인

에뮬레이터를 빠르게 돌리려면 CPU의 가상화 기능이 켜져 있어야 합니다.

1. `Ctrl` + `Shift` + `Esc`를 눌러 작업 관리자를 엽니다.
2. 왼쪽에서 **성능**을 누르고, **CPU**를 고릅니다.
3. 오른쪽 아래의 **가상화** 항목이 **사용**인지 확인합니다.

"사용 안 함"으로 나오면 PC를 다시 켜면서 BIOS(UEFI) 설정 화면에 들어가 Intel VT-x 또는 AMD-V(SVM)를 켜야 합니다. BIOS에 들어가는 키와 메뉴 이름은 PC 제조사마다 다르므로 제조사 안내를 참고하세요.

---

## 2. 레포 받기와 Android Studio로 열기

### 2.1 레포 받기

폴더 경로에는 **한글과 공백이 없는 곳**을 고르세요(예: `C:\dev`). Android Gradle 플러그인은 경로에 ASCII가 아닌 글자가 있으면 경고를 내거나 빌드를 멈출 수 있습니다.

방법 A. git을 쓰는 경우: 명령 프롬프트나 Git Bash에서 실행합니다.

```bash
cd C:\dev
git clone <레포 주소>
```

방법 B. git이 없는 경우

1. 웹 브라우저로 GitHub 레포 페이지를 엽니다.
2. 초록색 **Code** 버튼을 누르고 **Download ZIP**을 고릅니다.
3. 받은 ZIP 파일의 압축을 `C:\dev` 같은 곳에 풉니다.

### 2.2 Android Studio로 열기

1. Android Studio를 실행합니다. 다른 프로젝트가 열려 있으면 ☰ > File > Close Project를 눌러 시작 화면(Welcome)으로 돌아갑니다.
2. 시작 화면에서 **Open**을 누릅니다.
3. `<레포 경로>`(안에 `settings.gradle.kts`와 `gradlew.bat`이 있는 폴더)를 골라 **OK**를 누릅니다.
4. 프로젝트를 믿을지 묻는 창이 나오면 **Trust Project**를 누릅니다.
5. 프로젝트가 열리면 Android Studio가 자동으로 Gradle sync(의존성 내려받기와 프로젝트 분석)를 시작합니다. 처음에는 Gradle 8.10.2와 라이브러리를 내려받느라 몇 분 걸립니다.

처음 sync가 `Unsupported class file major version 69` 같은 오류로 실패해도 괜찮습니다. 아직 JDK를 17로 바꾸지 않았기 때문입니다. 3장을 마친 뒤 다시 sync하면 됩니다.

> Android Studio가 Android Gradle Plugin(AGP)을 새 버전으로 올리라고 권해도 **받지 마세요**. 이 프로젝트는 Pleos 샘플 앱과 같은 조합(Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.21, JDK 17)으로 버전을 고정해 두었습니다.

### 2.3 이 프로젝트의 버전 정보 (참고)

| 항목 | 값 | 정의된 파일 |
|---|---|---|
| Gradle | 8.10.2 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.8.2 | `build.gradle.kts` |
| Kotlin | 2.0.21 | `build.gradle.kts` |
| compileSdk / targetSdk | 35 | `app/build.gradle.kts` |
| minSdk | 34 (Pleos Connect = Android 14) | `app/build.gradle.kts` |
| Java | 17 | `app/build.gradle.kts` |
| Pleos SDK | `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5` | `app/build.gradle.kts` |

Pleos SDK는 공개 Maven 저장소(`settings.gradle.kts`에 등록됨)에서 자동으로 받아집니다. 계정이나 비밀번호는 필요 없습니다.

---

## 3. JDK 17 지정

Android Studio에 들어 있는 Java(JBR)는 **Java 25**입니다. Java 25로는 이 프로젝트가 쓰는 Gradle 8.10.2를 실행할 수 없고, `Unsupported class file major version 69` 같은 오류가 납니다. 그래서 Gradle이 JDK 17을 쓰도록 지정해야 합니다.

### 3.1 Android Studio에서 지정하기

1. `Ctrl` + `Alt` + `S`를 눌러 Settings 창을 엽니다. (☰ > File > Settings로도 열 수 있습니다.)
2. 왼쪽 목록에서 **Build, Execution, Deployment > Build Tools > Gradle**로 갑니다.
3. 오른쪽의 **Gradle JDK** 목록을 엽니다.
   - 목록에 이미 17 버전 JDK가 있으면 그것을 고르고 5번으로 넘어갑니다.
   - 없으면 **Download JDK...**를 고릅니다.
4. Download JDK 창에서 다음과 같이 고르고 **Download**를 누릅니다.
   - Version: **17**
   - Vendor: **Eclipse Temurin**
   - Location 칸에 나오는 폴더가 JDK가 설치될 곳입니다. 이 경로가 `<JDK17 경로>`이므로 메모해 두세요(보통 `%USERPROFILE%\.jdks\` 아래에 `temurin-17`로 시작하는 폴더가 생깁니다).
5. 내려받기가 끝나면 Gradle JDK 목록에 17 버전이 선택되어 있는지 확인하고 **OK**를 누릅니다.
6. ☰ > File > **Sync Project with Gradle Files**를 눌러 다시 sync합니다. 아래쪽 Build 창에 `BUILD SUCCESSFUL` 또는 sync 성공 표시가 나오면 됩니다.

이미 PC에 JDK 17이 설치되어 있다면 4번 대신 Gradle JDK 목록에서 그 JDK를 고르거나, 목록의 **Add JDK...**로 설치 폴더를 지정해도 됩니다.

### 3.2 명령줄 빌드용 JAVA_HOME

명령줄에서 `gradlew`를 실행할 때는 Android Studio 설정이 적용되지 않습니다. `JAVA_HOME` 환경 변수가 JDK 17을 가리켜야 합니다. 매번 지정하는 방법은 9.2절과 12장에 있습니다.

항상 쓰도록 등록하려면 다음과 같이 합니다.

1. Windows 검색창에 `환경 변수`를 입력하고 **계정의 환경 변수 편집**을 엽니다.
2. 위쪽 "사용자 변수" 칸에서 **새로 만들기**를 누릅니다.
3. 변수 이름에 `JAVA_HOME`, 변수 값에 `<JDK17 경로>`를 넣고 **확인**을 누릅니다.
4. 창을 모두 **확인**으로 닫고, 이미 열려 있던 터미널은 닫았다가 새로 엽니다.

---

## 4. SDK Manager 여는 법과 필요한 SDK 받기

### 4.1 SDK Manager 여는 세 가지 방법

어느 방법으로 열어도 같은 화면이 나옵니다.

1. `Ctrl` + `Alt` + `S`로 Settings를 열고 **Languages & Frameworks > Android SDK**로 갑니다.
2. ☰ > **Tools > SDK Manager**를 누릅니다.
3. 프로젝트를 열기 전 시작 화면(Welcome)에서 **More Actions > SDK Manager**를 누릅니다.

### 4.2 화면 구성

Android SDK 화면 위쪽에 탭이 세 개 있습니다.

| 탭 | 하는 일 |
|---|---|
| **SDK Platforms** | Android 버전별 플랫폼과 시스템 이미지(에뮬레이터 이미지)를 받습니다 |
| **SDK Tools** | 빌드 도구, 에뮬레이터, adb(platform-tools), 하이퍼바이저 드라이버 같은 도구를 받습니다 |
| **SDK Update Sites** | 패키지를 받아 올 저장소 목록입니다. 6장에서 Pleos 저장소를 여기에 추가합니다 |

각 탭 오른쪽 아래에 **Show Package Details** 체크박스가 있습니다. 이것을 켜야 세부 버전(예: Build-Tools 35.0.0)을 골라서 받을 수 있습니다.

### 4.3 SDK Platforms: Android 15 (API 35)

1. **SDK Platforms** 탭을 누릅니다.
2. API Level이 **35**(Android 15)인 줄을 체크합니다.
3. 아직 **Apply**는 누르지 말고 다음 절로 넘어가도 됩니다. 한 번에 받는 편이 편합니다.

### 4.4 SDK Tools: Build-Tools 35.0.0

1. **SDK Tools** 탭을 누릅니다.
2. 오른쪽 아래 **Show Package Details**를 켭니다.
3. **Android SDK Build-Tools** 항목을 펼쳐 **35.0.0**을 체크합니다.
4. 다음 두 항목이 설치되어 있는지(체크되어 있는지) 확인합니다. 없으면 체크합니다.
   - **Android Emulator**
   - **Android SDK Platform-Tools** (adb가 들어 있습니다)
5. (선택) 명령줄에서 `sdkmanager`·`avdmanager`를 쓰고 싶다면 **Android SDK Command-line Tools (latest)**도 체크합니다.
6. **Apply**를 누릅니다. 확인 창이 나오면 **OK**를 누르고, 라이선스 창이 나오면 **Accept**를 고른 뒤 **Next**를 누릅니다.
7. 설치가 끝나면 **Finish**를 누릅니다.

"Android Emulator hypervisor driver (installer)"는 여기서 받지 말고 5장을 먼저 읽으세요.

---

## 5. 에뮬레이터 가속 켜기

가속이 꺼져 있으면 에뮬레이터가 아예 켜지지 않거나 매우 느립니다.

### 5.1 현재 상태 확인

명령 프롬프트(cmd)를 열고 실행합니다.

```bat
"%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -accel-check
```

PowerShell에서는 다음과 같이 씁니다.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -accel-check
```

결과에 `Android Emulator hypervisor driver is not installed`가 나오면 가속이 꺼진 상태입니다. 아래 방법 1 또는 방법 2 중 **하나만** 하세요. 두 가지를 함께 쓰면 서로 충돌합니다.

### 5.2 왜 SDK Tools에서 드라이버 설치가 실패하나

SDK Tools 탭에서 **Android Emulator hypervisor driver (installer)**를 관리자 권한 없이 설치하면 실패합니다. 이때 로그에 다음과 같은 줄이 나오고, 받던 폴더도 지워집니다(롤백).

```
Failed to update status to COMPLETE
failed. Failed packages: - Android Emulator hypervisor driver (installer) (extras;google;Android_Emulator_Hypervisor_Driver)
```

### 5.3 방법 1 (권장): Windows 하이퍼바이저 플랫폼 켜기

WSL2, Docker Desktop, Hyper-V를 쓰는 PC라면 반드시 이 방법을 쓰세요.

1. Windows 검색창에 `Windows 기능 켜기/끄기`를 입력하고 엽니다.
2. 목록에서 **Windows 하이퍼바이저 플랫폼**을 체크합니다.
3. **확인**을 누릅니다. 설치가 끝나면 PC를 **다시 시작**합니다.

같은 일을 관리자 권한 PowerShell에서 할 수도 있습니다. 시작 버튼을 오른쪽 클릭해 **터미널(관리자)** 또는 **Windows PowerShell(관리자)**를 연 뒤 실행하고, 끝나면 재부팅합니다.

```powershell
Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform
```

### 5.4 방법 2 (대안): 에뮬레이터 하이퍼바이저 드라이버(AEHD) 설치

방법 1을 쓰지 않을 때만 합니다.

1. Android Studio를 완전히 종료합니다.
2. 시작 메뉴나 바탕화면의 Android Studio 아이콘을 오른쪽 클릭하고 **관리자 권한으로 실행**을 누릅니다.
3. SDK Manager(4.1절) > **SDK Tools** 탭에서 **Android Emulator hypervisor driver (installer)**를 체크하고 **Apply**를 누릅니다.
4. 설치가 끝나면 Android Studio를 닫고, 평소처럼(관리자 권한 없이) 다시 엽니다.

`%LOCALAPPDATA%\Android\Sdk\extras\google\Android_Emulator_Hypervisor_Driver` 폴더가 남아 있다면, 그 안의 `silent_install.bat`를 오른쪽 클릭해 **관리자 권한으로 실행**해도 됩니다.

### 5.5 가속이 켜졌는지 확인

5.1절의 `-accel-check` 명령을 다시 실행합니다.

- 방법 1을 썼다면 `WHPX ... is installed and usable`처럼 WHPX를 쓸 수 있다는 문장이 나와야 합니다.
- 방법 2를 썼다면 하이퍼바이저 드라이버(AEHD)를 쓸 수 있다는 문장이 나와야 합니다(정확한 문구는 확인 필요).

---

## 6. Pleos 에뮬레이터 이미지 받기

이전 버전의 Pleos Connect 에뮬레이터 이미지가 설치되어 있다면, Pleos 안내에 따라 먼저 지운 뒤 진행하세요.

1. SDK Manager를 엽니다(4.1절).
2. **SDK Update Sites** 탭을 누릅니다.
3. 목록 왼쪽 위의 **+** 버튼을 누릅니다.
4. 다음과 같이 입력하고 **OK**를 누릅니다.
   - Name: `Pleos Connect System Image`
   - URL: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` (Windows용 주소입니다)
5. **SDK Platforms** 탭을 누르고, 오른쪽 아래 **Show Package Details**를 켭니다.
6. API Level 34(Android 14) 묶음을 펼치고 **Pleos Connect v2.1.0 APIs Intel x86_64 Atom System Image**를 체크합니다. 크기는 약 **4.1 GB**이고, 내부 이름은 `system-images;android-34;pleos_car_emulator;x86_64`입니다.
7. **Apply**(또는 **OK**)를 누르고, 라이선스에 동의한 뒤 내려받기가 끝날 때까지 기다립니다.
8. **Finish**를 누르고 Android Studio를 **완전히 종료한 뒤 다시 실행**합니다.

---

## 7. 가상 기기(AVD) 만들기

1. Device Manager를 엽니다. ☰ > **Tools > Device Manager**를 누릅니다.
2. **Create device**를 누릅니다. (이전 버전의 Android Studio에서는 Create Virtual Device입니다.)
3. 왼쪽 Form Factor에서 **Automotive**를 고릅니다.
4. 목록에서 **Pleos Connect v2.1.0**을 고르고 **Next**를 누릅니다.
5. Configure virtual device 창에서 **Advanced Settings** 탭으로 갑니다. (Android Studio 버전에 따라 이름이 조금 다를 수 있습니다. 이전 버전에서는 Show Advanced Settings 버튼입니다.)
6. 다음 두 값을 바꿉니다.
   - Startup > **Default boot**: **Cold**
   - Emulated Performance > **RAM**: **4 GB**
   - (이전 버전의 Android Studio라면 Graphics를 **Hardware**로, Boot option을 **Cold boot**로 고릅니다.)
7. 다시 **Device** 탭으로 돌아가 Select system image에서 API를 **API 34**로 고르고, 아래 표에서 **Pleos Connect v2.1.0 APIs**를 고릅니다.
8. **Finish**를 누릅니다.

> **반드시 Cold boot로 두세요.** Quick Boot로 두면 차량 신호를 전달하는 VHAL이 동작하지 않아, Virtual Console에서 기어나 브레이크를 바꿔도 앱에 신호가 오지 않습니다.

---

## 8. 앱 실행

1. Device Manager에서 방금 만든 Pleos 기기 옆의 ▶(실행) 버튼을 눌러 에뮬레이터를 켭니다. Cold boot라서 처음에는 몇 분 걸릴 수 있습니다. 차량 홈 화면이 나올 때까지 기다리세요.
2. Android Studio 위쪽 툴바의 ▶ 버튼 근처에 있는 **기기 선택 목록**에서 Pleos 기기를 고릅니다.
3. 실행 구성이 **app**인지 확인하고 ▶ **Run 'app'**을 누릅니다(단축키 `Shift` + `F10`).
4. 빌드가 끝나면 앱이 에뮬레이터에 설치되고 자동으로 열립니다.

지금은 UI가 구현 중이라 "차마고찌"라는 글자만 보이는 임시 화면이 나옵니다. 이 화면이 보이면 빌드·설치·실행은 정상입니다.

에뮬레이터 언어는 기본이 한국어입니다. 영어로 바꾸려면 에뮬레이터에서 **차량 설정 > 일반 설정 > 언어/Language**로 가서 `English`를 고르세요.

---

## 9. 단위 테스트

단위 테스트는 에뮬레이터 없이 PC(JVM)에서 돌아갑니다. 이벤트 감지, 상태 엔진, PRD 계산 예시, 대사 규칙 등을 확인하며, 현재 91개입니다.

### 9.1 Android Studio에서

1. 왼쪽 Project 창 위쪽의 보기 목록을 **Android**에서 **Project**로 바꿉니다.
2. `app > src > test` 폴더를 오른쪽 클릭합니다.
3. **Run 'Tests in …'**를 누릅니다(… 부분에는 폴더나 패키지 이름이 들어갑니다).
4. 아래쪽 Run 창에 결과가 나옵니다. 모두 초록색 체크면 통과입니다.

테스트 파일 하나만 돌리려면 그 파일(예: `app/src/test/java/com/carmagotchi/app/domain/EngineTest.kt`)을 오른쪽 클릭해 같은 메뉴를 누르면 됩니다.

### 9.2 명령줄에서

Android Studio 안의 터미널(`Alt` + `F12`, 또는 ☰ > View > Tool Windows > Terminal)을 쓰면 `<레포 경로>`에서 바로 시작합니다. 기본 터미널은 PowerShell입니다.

Git Bash:

```bash
cd <레포 경로>
JAVA_HOME="<JDK17 경로>" ./gradlew test
```

명령 프롬프트(cmd):

```bat
cd <레포 경로>
set JAVA_HOME=<JDK17 경로>
gradlew.bat test
```

PowerShell:

```powershell
cd <레포 경로>
$env:JAVA_HOME = "<JDK17 경로>"
.\gradlew.bat test
```

3.2절에서 `JAVA_HOME`을 등록해 두었다면 `JAVA_HOME` 줄은 생략해도 됩니다.

- 마지막에 `BUILD SUCCESSFUL`이 나오면 모두 통과입니다.
- 결과 보고서는 `app\build\reports\tests\testDebugUnitTest\index.html`에 생깁니다. 브라우저로 열면 테스트별 결과를 볼 수 있습니다.
- `test`는 debug와 release 두 번 테스트를 돌립니다. 빠르게 보려면 `testDebugUnitTest`를 쓰세요.
- 특정 클래스만 돌리려면: `./gradlew testDebugUnitTest --tests "com.carmagotchi.app.domain.EngineTest"`

---

## 10. Virtual Console로 차량 신호 바꾸기

Virtual Console은 에뮬레이터 안의 차량 상태(기어, 브레이크, 와이퍼 등)를 PC 브라우저에서 바꾸는 도구입니다.

### 10.1 adb 쓰는 법

adb는 기본으로 PATH에 들어 있지 않습니다. 아래 셋 중 하나로 실행하세요.

1. **Android Studio 터미널**(`Alt` + `F12`)에서 `adb`를 실행합니다.
2. 전체 경로로 실행합니다.
   - cmd: `"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" devices`
   - PowerShell: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices`
3. PATH에 등록합니다(한 번만 하면 됩니다).
   1. Windows 검색창에 `환경 변수`를 입력하고 **계정의 환경 변수 편집**을 엽니다.
   2. 사용자 변수에서 **새로 만들기**를 눌러 이름 `ANDROID_HOME`, 값 `%LOCALAPPDATA%\Android\Sdk`를 넣습니다.
   3. 사용자 변수의 **Path**를 고르고 **편집** > **새로 만들기**를 눌러 `%LOCALAPPDATA%\Android\Sdk\platform-tools`를 넣습니다.
   4. 모두 **확인**으로 닫고 터미널을 새로 엽니다.

이 문서의 나머지 명령은 `adb`가 실행되는 상태라고 가정합니다.

### 10.2 접속하기

1. 에뮬레이터가 완전히 부팅되었는지(차량 홈 화면이 보이는지) 확인합니다.
2. 터미널에서 실행합니다.

   ```
   adb devices
   adb forward tcp:8088 tcp:8088
   ```

   `adb devices` 결과에 `emulator-5554  device` 같은 줄이 있어야 합니다.
3. PC 브라우저에서 `http://127.0.0.1:8088/`을 엽니다.
4. **Emulation Controls**를 엽니다.
5. 왼쪽 Domain 목록에서 영역을 고르거나, 검색창에 카드 이름을 입력합니다.
6. 카드에 영역 선택(예: 앞좌석/뒷좌석)이 있으면 먼저 고르고, 버튼·목록·슬라이더로 값을 바꿉니다.

에뮬레이터를 다시 켜면 `adb forward`를 다시 실행해야 합니다.

### 10.3 이 앱이 쓰는 카드

| Domain | 카드 | 범위 | 앱에서의 의미 |
|---|---|---|---|
| Motion | Gear Selection | P, R, N, D | 주행 시작(D)과 정차(P) |
| Motion | Brake Pedal | 0~100% | 급정거 흉내(브레이크 + 조향) |
| Motion | Steering Angle | -540~540° | 급정거·급회전 흉내 |
| Motion | Odometer | 0~1,000,000 km | 누적 거리, 거리 기념일 |
| Convenience | Seat Occupancy | 좌석별 Empty / Occupied | 동승자 탑승 |
| Convenience | Windshield Wiper Switch | 앞/뒤, Off·Auto·Low·High | 비 |
| Convenience | Turn Signal State | Off, 좌·우 Blink/Flash | 방향지시등 |
| Safety | Telltales | 경고등별 Off·On·Blink (TPMS 포함) | 차 경고(펫이 아파함) |
| Energy | Battery SOC | 0~100% | 충전(밥), 배터리 부족(배고픔) |
| Climate | HVAC Power 등 | 공조 상태 | 공조 |

> 에뮬레이터에는 **속도, 연료 %, 위치, 내비 목적지** 카드가 없습니다. 그래서 에뮬레이터에서는 급정거를 "브레이크 + 조향"으로 흉내 내고, 주유(밥)는 배터리 SOC 상승(충전)으로 보여 줍니다.

---

## 11. 시나리오로 확인하기

> **UI 구현 중**: 아래 "기대 화면"은 완성된 앱 기준입니다. 지금은 임시 화면만 있어 화면 변화는 볼 수 없습니다. 저장점 불러오기와 배속(가짜 시계)은 debug 빌드 전용 개발자 패널 기능이며, 이 패널도 아직 구현 전입니다. 지금 바로 확인할 수 있는 것은 9장의 단위 테스트와 11.3절의 Gleo 호출 흉내입니다.

### 11.1 공통 준비

- [ ] AVD를 Cold boot로 켭니다(7장).
- [ ] `adb forward tcp:8088 tcp:8088` 후 Emulation Controls를 엽니다(10.2절).
- [ ] 앱을 설치하고 부화(첫 실행 설정)까지 마칩니다.
- [ ] 앱을 세로 1칸 창에 띄워 둡니다.

이 시나리오에서 "주행"과 "정차"는 다음 뜻입니다(확인 필요: 속도 카드가 없어서 기어만으로는 플랫폼이 주행 규제를 켜지 않을 수 있어 아래 명령을 함께 씁니다).

- **주행**: Gear Selection을 **D**로 바꾼 뒤 `adb shell cmd hmg_car_service --service drivingstate driving_regulation on`
- **정차**: `adb shell cmd hmg_car_service --service drivingstate driving_regulation off`를 실행한 뒤 Gear Selection을 **P**로

### 11.2 PT 2분 시연 체크리스트

| 순서 | Virtual Console 조작 | 기대 화면 |
|---|---|---|
| ☐ 0:00 | (개발자 패널) 저장점 "아기 콩이" 불러오기, 기어 P | 방 안의 콩이(아기 단계) |
| ☐ 0:10 | Convenience > Seat Occupancy > 뒷좌석 왼쪽(Row 2 Left) > **Occupied**, 기어 P에서 10초 유지 | 출발 전 화면에 "새 친구다! 안녕 서윤아?"와 이름 칩 |
| ☐ 0:25 | 주행 → Brake Pedal **80%**(유지) → Steering Angle **400°** → Brake **0%** → Steering **0°** → 정차 | 주행 중에는 바탕만 보입니다. P가 되면 도착 화면에서 머리 위 별과 "아까 한 번 깜짝 놀랐어." (에뮬레이터에서는 "브레이크 ON + 1초 안에 조향 90° 이상"을 급정거로 봅니다) |
| ☐ 0:45 | Windshield Wiper Switch > Front > **Low** → 주행 → (배속으로 60초) → Wiper **Off** → 정차 | 우산과 달팽이, 도감 1/12 |
| ☐ 1:05 | Safety > Telltales > TPMS > **On**, 기어 P 유지 | 발을 감싼 콩이, 반창고, 할 일 한 줄 |
| ☐ 1:15 | TPMS **Off** (배속으로 30초 유지) | "다 나았다! 고마워."와 리본 |
| ☐ 1:25 | Energy > Battery SOC **40 → 80** (기어 P) | "찌릿, 충전 완료! 든든해." |
| ☐ 1:35 | 11.3절의 `CHAMA_MOOD` 명령(Gleo가 있으면 음성으로 "콩이 기분 어때?") | 응답 로그와 개발자 패널에 "뒤에 친구가 있어서 신나! 커브에선 살살 가 줘." |
| ☐ 1:50 | (개발자 패널) 저장점 "1년 차" → 1년 앨범 | 첫 비, 첫 바다, 귀성길 |

### 11.3 Gleo 호출 흉내 (지금 확인 가능)

에뮬레이터에 차량 AI(Gleo)가 없거나 앱 스키마가 아직 승인되지 않았으면, Gleo가 보내는 것과 같은 broadcast를 adb로 직접 보냅니다. Windows 터미널의 따옴표 문제를 피하려고 에뮬레이터 안의 셸에 들어가서 실행합니다.

1. 앱을 한 번 실행해 둡니다(8장).
2. 터미널을 하나 더 열고 응답 로그를 지켜봅니다.

   ```
   adb logcat -s ChamaGleo:I
   ```

3. 다른 터미널에서 에뮬레이터 셸에 들어갑니다.

   ```
   adb shell
   ```

4. 프롬프트가 바뀌면 아래 명령을 한 줄씩 입력합니다.

   ```sh
   am broadcast -a CHAMA_MOOD -p com.carmagotchi.app --es ACTION_PARAM '{"topic":"mood"}'
   am broadcast -a CHAMA_DISTANCE -p com.carmagotchi.app --es ACTION_PARAM '{}'
   am broadcast -a CHAMA_THANKS -p com.carmagotchi.app --es ACTION_PARAM '{"kind":"thanks"}'
   exit
   ```

5. 로그 터미널에 `ChamaGleo`와 함께 `CHAMA_MOOD success`나 `CHAMA_MOOD fail`처럼 함수 이름과 결과가 한 줄씩 나오면 수신과 응답이 동작하는 것입니다. 아직 부화 화면이 없어서 지금은 `fail`이 나오는 것이 정상일 수 있습니다(확인 필요).

---

## 12. 명령줄 빌드

`JAVA_HOME`을 JDK 17로 지정하는 방법은 9.2절과 같습니다. 아래는 Git Bash 기준이며, cmd·PowerShell에서는 `./gradlew` 대신 `gradlew.bat`(PowerShell은 `.\gradlew.bat`)을 쓰세요.

### 12.1 debug APK

```bash
cd <레포 경로>
JAVA_HOME="<JDK17 경로>" ./gradlew assembleDebug
```

- 결과 파일: `app\build\outputs\apk\debug\app-debug.apk`
- 에뮬레이터가 켜진 상태에서 설치: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

### 12.2 release APK

```bash
JAVA_HOME="<JDK17 경로>" ./gradlew assembleRelease
```

- 현재는 서명 설정이 레포에 없어서 결과 파일이 `app\build\outputs\apk\release\app-release-unsigned.apk`로 나옵니다. 서명되지 않은 APK는 에뮬레이터에 설치할 수 없습니다.
- 서명 키는 레포 밖에 두고, 비밀번호는 레포 밖 `%USERPROFILE%\.gradle\gradle.properties`에 넣는 방식으로 추가할 예정입니다. 서명이 설정되면 결과 파일은 `app\build\outputs\apk\release\app-release.apk`가 됩니다.

---

## 13. 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| `-accel-check` 결과가 `Android Emulator hypervisor driver is not installed`이고 에뮬레이터가 안 켜지거나 매우 느림 | 에뮬레이터 가속이 꺼져 있음 | 5.3절(Windows 하이퍼바이저 플랫폼 켜기, 권장) 또는 5.4절(AEHD 설치) 중 하나만 하고 재부팅 후 5.5절로 확인 |
| SDK Tools에서 hypervisor driver 설치 중 `Failed to update status to COMPLETE`, `failed. Failed packages: - Android Emulator hypervisor driver (installer) (extras;google;Android_Emulator_Hypervisor_Driver)`가 나오고 폴더가 사라짐 | 관리자 권한 없이 드라이버를 설치하려 함 | 5.3절 방법 1을 쓰거나, Android Studio를 **관리자 권한으로 실행**해 다시 설치(5.4절). WSL2·Docker·Hyper-V를 쓰면 방법 1만 사용 |
| Gradle sync나 빌드가 `Unsupported class file major version 69`로 실패 | Gradle이 Android Studio 내장 Java 25로 실행됨 | 3.1절대로 Gradle JDK를 17로 바꾸고 다시 sync. 명령줄이라면 `JAVA_HOME`을 `<JDK17 경로>`로 지정(9.2절) |
| 명령줄 빌드가 `SDK location not found`로 실패 | `local.properties`가 없고 `ANDROID_HOME`도 없음 | Android Studio로 프로젝트를 한 번 열어 sync하거나(파일이 자동 생성됨), 10.1절대로 `ANDROID_HOME`을 등록 |
| Virtual Console에서 기어·브레이크를 바꿔도 앱이 반응하지 않음 | AVD가 Quick Boot로 켜져 VHAL이 동작하지 않음 | Device Manager에서 기기 설정을 열어 Default boot를 **Cold**로 바꾸거나, 7장대로 AVD를 새로 만든 뒤 에뮬레이터를 다시 켬 |
| 브라우저에서 `http://127.0.0.1:8088/`이 열리지 않음 | 부팅이 덜 끝났거나, adb 연결·포트 포워딩이 안 됐거나, 8088 포트를 다른 프로그램이 씀 | ① 차량 홈 화면이 나올 때까지 기다림 ② `adb devices`에 에뮬레이터가 `device`로 보이는지 확인 ③ `adb forward tcp:8088 tcp:8088` 다시 실행(에뮬레이터를 다시 켰다면 필수) ④ 8088 포트를 쓰는 다른 프로그램을 종료 ⑤ Pleos Connect v2.1.0 이미지로 만든 AVD인지 확인 |
| `adb`를 입력하면 `'adb'은(는) 내부 또는 외부 명령...이 아닙니다` 또는 `adb : 'adb' 용어가 cmdlet...으로 인식되지 않습니다` | adb가 PATH에 없음 | Android Studio 터미널을 쓰거나, 전체 경로 `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`로 실행하거나, PATH에 등록(10.1절) |
| SDK Platforms에 Pleos 이미지가 보이지 않음 | 업데이트 사이트가 없거나 주소가 틀림, 세부 항목이 숨겨져 있음, 목록이 갱신되지 않음 | ① SDK Update Sites에 `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`이 정확히 있는지 확인 ② SDK Platforms에서 **Show Package Details** 켜기 ③ API Level 34 묶음 펼치기 ④ Android Studio 재시작 |
| AVD 만들 때 Automotive에 Pleos Connect v2.1.0이 없음 | 이미지 설치 후 Android Studio를 재시작하지 않음 | Android Studio를 완전히 종료하고 다시 실행(6장 8번) |
