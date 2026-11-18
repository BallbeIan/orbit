import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: put the upload key's details in keystore.properties (git-ignored, see keystore.properties.example).
// Without that file the release build is signed with the debug key, so it still installs for local testing.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "app.orbit.cameraisland"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.orbit.cameraisland"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "3.5"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Side-by-side design preview: its own app id, so it installs next to the real app (debug-signed).
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            matchingFallbacks += "debug"
        }
        release {
            // R8 shrinks and optimises the code; resource shrinking drops anything unused.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
