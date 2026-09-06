plugins {
    id("com.android.application")
}

android {
    namespace = "com.apextechlabs.wakeremote"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.apextechlabs.wakeremote"
        minSdk = 23
        targetSdk = 34
        versionCode = 3
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (System.getenv("WAKE_STORE_FILE") != null) {
            create("production") {
                storeFile = file(System.getenv("WAKE_STORE_FILE"))
                storePassword = System.getenv("WAKE_STORE_PASSWORD")
                keyAlias = System.getenv("WAKE_KEY_ALIAS")
                keyPassword = System.getenv("WAKE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (System.getenv("WAKE_STORE_FILE") != null) signingConfig = signingConfigs.getByName("production")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation("junit:junit:4.13.2")
}
