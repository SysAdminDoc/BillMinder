import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

ksp {
    // Exported schemas are what the migration tests validate against.
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Signing credentials live outside the repository. See keystore.properties.example.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val hasSigningCredentials = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }

gradle.taskGraph.whenReady {
    if (!hasSigningCredentials && allTasks.any { it.name.contains("Release") }) {
        throw GradleException(
            "Release builds need signing credentials. Copy keystore.properties.example to " +
                "keystore.properties in the repository root and fill in the four values."
        )
    }
}

android {
    namespace = "com.sysadmindoc.billminder"
    compileSdk = 36

    flavorDimensions += "distribution"

    productFlavors {
        create("play") {
            dimension = "distribution"
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    defaultConfig {
        applicationId = "com.sysadmindoc.billminder"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "2.4.0"
    }

    signingConfigs {
        if (hasSigningCredentials) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.gson)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
}
