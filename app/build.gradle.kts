plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.goenforcer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.goenforcer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Optional release keystore — supply via gradle.properties or CI env:
        //   RELEASE_KEYSTORE=/path/to/release.jks
        //   RELEASE_KEYSTORE_PASSWORD=***  RELEASE_KEY_ALIAS=***  RELEASE_KEY_PASSWORD=***
        create("release") {
            val ksPath = System.getenv("RELEASE_KEYSTORE")
                ?: project.findProperty("RELEASE_KEYSTORE") as String?
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                    ?: project.findProperty("RELEASE_KEYSTORE_PASSWORD") as String?
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                    ?: project.findProperty("RELEASE_KEY_ALIAS") as String?
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                    ?: project.findProperty("RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinking + obfuscation now actually runs in CI/local builds.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // No keystore configured yet → fall back to the debug key so
            // assembleRelease always produces an installable APK.
            signingConfig = if (ksConfigured()) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.APPLICATION_ID feeds SecureSettings/PASS
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

fun ksConfigured(): Boolean =
    (System.getenv("RELEASE_KEYSTORE") ?: project.findProperty("RELEASE_KEYSTORE") as String?) != null

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Local JVM unit tests (Core engine, scheduling math, state reducers)
    testImplementation(libs.junit)
}
