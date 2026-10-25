plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.carmagotchi.app"
    compileSdk = 35
    useLibrary("android.car") // AAOS CarPropertyManager, CarUxRestrictionsManager

    defaultConfig {
        applicationId = "com.carmagotchi.app"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions.unitTests.isIncludeAndroidResources = true
    // 화면 PNG 테스트는 debug 전용(compose test manifest가 debug에만 있다)
    testOptions.unitTests.all { if (it.name.contains("Release")) it.exclude("**/ScreensTest*") }
    // 단위 테스트가 assets/dialogue.tsv를 그대로 읽는다
    sourceSets["test"].resources.srcDir("src/main/assets")
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.code.gson:gson:2.10.1") // 저장 파일 직렬화(Pleos SDK가 이미 같은 버전을 끌어온다)

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("com.google.zxing:core:3.5.3") // QR, Apache-2.0

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // JVM 테스트에서 org.json 실제 구현(Gleo 응답 JSON)
    // 에뮬레이터 없이 화면 PNG를 만든다(테스트 전용)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.36.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.36.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
