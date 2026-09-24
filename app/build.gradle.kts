plugins {
    id("com.android.application")
}

val releaseKeystore = System.getenv("TCH_KEYSTORE_FILE")
val releaseStorePassword = System.getenv("TCH_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("TCH_KEY_ALIAS")
val releaseKeyPassword = System.getenv("TCH_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseKeystore,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.cepeter.royalty"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.cepeter.royalty"
        minSdk = 27
        targetSdk = 35
        versionCode = 11
        versionName = "2.2.1"

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst {
        check(releaseSigningConfigured) {
            "Release signing requires TCH_KEYSTORE_FILE, TCH_STORE_PASSWORD, TCH_KEY_ALIAS, and TCH_KEY_PASSWORD"
        }
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    testImplementation("junit:junit:4.13.2")
}
