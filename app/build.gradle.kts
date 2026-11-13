plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    useLibrary("android.car") // CarPropertyManager: vehicle speed
    namespace = "com.example.hyundai_contest"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.hyundai_contest"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
