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
        versionCode = 2
        versionName = "1.0.2"

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

    // The release asset is WakeRemote.apk. Setting it here means the artifact is
    // correctly named at the source rather than renamed in CI.
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = if (buildType.name == "release") "WakeRemote.apk" else "WakeRemote-debug.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // zxing's CaptureManager calls ContextCompat/ActivityCompat directly. Without this the
    // scanner activity dies on resume with NoClassDefFoundError for
    // androidx.core.content.ContextCompat, which looks exactly like the QR button doing
    // nothing and returning to the previous screen.
    implementation("androidx.core:core:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
