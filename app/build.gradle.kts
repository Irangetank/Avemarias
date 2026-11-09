plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "br.igreja.avemarias"
    compileSdk = 34
    defaultConfig {
        applicationId = "br.igreja.avemarias"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }
    signingConfigs {
        create("fixa") {
            storeFile = file("avemarias.p12"); storePassword = "avemarias"
            keyAlias = "avemarias"; keyPassword = "avemarias"; storeType = "pkcs12"
        }
    }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("fixa") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
