plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.mdeberly.rsseink"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.mdeberly.rsseink"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.55"
    }

    signingConfigs {
        // Personal app: a fixed key committed with the project so that each
        // new APK built by CI installs over the previous one.
        create("personal") {
            storeFile = file("personal.keystore")
            storePassword = "android"
            keyAlias = "personal"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("personal")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("personal")
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
    implementation("org.jsoup:jsoup:1.17.2")
    testImplementation("junit:junit:4.13.2")
}
