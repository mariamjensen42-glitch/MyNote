import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release signing material, read from `keystore.properties` at the repository root.
 *
 * That file is git-ignored and holds the path to a keystore that lives outside the repository
 * entirely, so neither the private key nor its password can be committed by accident. See
 * `keystore.properties.example` for the format and the README for how to create one.
 *
 * When the file is absent the release build type is left unsigned rather than failing the build:
 * CI and anyone cloning the repository can still assemble everything, and only the person holding
 * the key can produce a publishable APK.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val hasReleaseKeystore = keystorePropertiesFile.isFile

/** Kept in one place: the manifest, the APK's file name and the settings screen all read it. */
val appVersionName = "0.0.3"

android {
    namespace = "com.cycling.mynote"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.cycling.mynote"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
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
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }

            // R8: shrinking, obfuscation and optimisation, plus unused-resource removal.
            // Hilt, Room and Compose all ship consumer keep rules, so this needs no hand-written
            // keeps today; app/proguard-rules.pro exists for the ones that inevitably come up.
            optimization {
                enable = true
            }
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/**
 * Names the APK after the version: `mynote-0.0.3.apk` rather than `app-release.apk`.
 *
 * Two releases shipped under the same file name, and a browser — or a phone's download manager —
 * happily hands back the file it already has under that name. What arrives is then the previous
 * version, and it looks for all the world like the new one failed to build. The version in the name
 * makes that impossible, and makes a downloaded file self-describing.
 */
androidComponents {
    onVariants { variant ->
        val suffix = if (variant.name == "release") "" else "-${variant.name}"
        variant.outputs.forEach { output ->
            output.outputFileName.set("mynote-$appVersionName$suffix.apk")
        }
    }
}
