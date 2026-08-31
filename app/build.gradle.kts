plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.abhi.mymoney"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.abhi.mymoney"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
