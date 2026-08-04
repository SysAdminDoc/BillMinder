plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sysadmindoc.billminder.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sysadmindoc.billminder.wear"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.1.1"
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
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.guava)
    implementation(libs.play.services.wearable)
}
