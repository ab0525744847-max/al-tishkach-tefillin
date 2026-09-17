plugins {
    id("com.android.application")
}

android {
    namespace = "com.ariberman.altishkachtefillin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ariberman.altishkachtefillin"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
dependencies {
    implementation("androidx.core:core:1.13.1")
}
