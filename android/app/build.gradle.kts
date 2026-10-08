plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.devtools.ksp")
}

import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val mapboxAccessToken: String =
    localProperties.getProperty("MAPBOX_ACCESS_TOKEN") ?: System.getenv("MAPBOX_ACCESS_TOKEN") ?: ""

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    // AND-M3 (store-audit 2026-05-18): namespace (Kotlin package root)
    // intentionally diverges from `applicationId` (Play Store package
    // identifier). The original Play Store listing was first created
    // under `dev.rahier.pouleparty`, then archived; Play Store package
    // names are permanent so we bumped to `…pouleparty2` for the new
    // listing. The Kotlin/Java code keeps the original namespace to
    // avoid a sweeping rename — they are decoupled by design. Do NOT
    // try to "align" these unless we're prepared to ship a brand-new
    // listing (which would lose the Play Store reviews).
    namespace = "dev.rahier.pouleparty"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.rahier.pouleparty2"
        minSdk = 26
        targetSdk = 36
        versionCode = 43
        versionName = "1.14.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "mapbox_access_token", mapboxAccessToken)
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("staging") {
            dimension = "environment"
        }
        create("production") {
            dimension = "environment"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk { debugSymbolLevel = "FULL" }
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

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }

    // AND-M7 (store-audit 2026-05-18): 16 KB page-size alignment for
    // native libraries. Required by Google Play for uploads targeting
    // API 35+ starting 2026-05-31 (Android 15 devices ship with a 16 KB
    // kernel page size). AGP 9.x defaults `useLegacyPackaging = false`
    // for targetSdk >= 35 but pinning it explicitly survives any future
    // AGP default flip. Mapbox already ships via the `ndk27` artifacts
    // which are pre-aligned (see dependencies block).
    //
    // Verify the produced AAB with :
    //   unzip -p app-production-release.aab base/lib/arm64-v8a/<lib>.so \
    //     | objdump -p - | grep LOAD
    // → every LOAD segment must have align = 0x4000 (16 KB).
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val requireMapboxToken = tasks.register("requireMapboxToken") {
    doLast {
        if (mapboxAccessToken.isBlank()) {
            throw GradleException("MAPBOX_ACCESS_TOKEN is missing: set it in local.properties or the environment")
        }
    }
}
tasks.matching { (it.name.startsWith("assemble") || it.name.startsWith("bundle")) && it.name.endsWith("Release") }
    .configureEach { dependsOn(requireMapboxToken) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0")

    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    // ndk27 artifacts are aligned for 16 KB pages, required by Play for Android 15+.
    implementation("com.mapbox.maps:android-ndk27:11.20.2")
    implementation("com.mapbox.extension:maps-compose-ndk27:11.20.2")

    implementation("com.google.android.gms:play-services-location:21.4.0")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
