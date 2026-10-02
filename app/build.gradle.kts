plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI passes its run number so every build installs as an update over the previous one.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// The Google Play upload key is private and never in the repo. CI writes it to a file from the
// PLAY_UPLOAD_KEYSTORE_BASE64 secret; see store/PLAY_STORE.md. Without it the Play bundle is unsigned.
val playUploadKeystore = System.getenv("PLAY_UPLOAD_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
val playUploadPassword = System.getenv("PLAY_UPLOAD_PASSWORD")

android {
    namespace = "com.threepatti.tracker"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        // A shared key kept in the repo so builds from any machine can update each other on the phones.
        create("shared") {
            storeFile = file("signing/tracker.keystore")
            storePassword = "tracker"
            keyAlias = "tracker"
            keyPassword = "tracker"
        }
        if (playUploadKeystore != null && playUploadPassword != null) {
            create("playUpload") {
                storeFile = playUploadKeystore
                storePassword = playUploadPassword
                keyAlias = "upload"
                keyPassword = playUploadPassword
            }
        }
    }

    flavorDimensions += "store"
    productFlavors {
        // The APK shared on GitHub. It keeps the first app ID so it updates phones that installed earlier APKs.
        create("github") {
            dimension = "store"
            applicationId = "com.threepatti.tracker"
            signingConfig = signingConfigs.getByName("shared")
        }
        // The Google Play app. Play signs it with its own key, so it is a separate app with its own ID.
        create("play") {
            dimension = "store"
            applicationId = "com.afler.chipshandler"
            signingConfig = signingConfigs.findByName("playUpload")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            // No signing config here: a build type's would override the flavors' keys.
            isMinifyEnabled = false
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
