plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gridline.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gridline.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // A dedicated sideload key kept in the repo so every build can update
    // the installed app in place. It is not a secret and not for Play.
    signingConfigs {
        create("sideload") {
            storeFile = rootProject.file("keystore/gridline-sideload.jks")
            storePassword = "gridline"
            keyAlias = "gridline"
            keyPassword = "gridline"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sideload")
        }
        debug {
            signingConfig = signingConfigs.getByName("sideload")
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

dependencies {
    testImplementation("junit:junit:4.13.2")
}
