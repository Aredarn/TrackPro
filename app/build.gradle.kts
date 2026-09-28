plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Writes the resolved Room schema to app/schemas/<version>.json on every build.
//
// Without this there is no record of what any past schema version actually looked like,
// which is what made the historical migrations here guesswork to reconstruct. With it,
// each version is checked in alongside the code and future migrations can be written (and
// tested) against a known-good schema instead of archaeology.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.example.trackpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.trackpro"
        minSdk = 26
        targetSdk = 34 // intentionally behind compileSdk 35, not yet verified against Android 15 behavior changes
        versionCode = 1
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Exposes the exported Room schemas (see the ksp block above) to instrumented tests as
    // assets, which is where MigrationTestHelper looks for
    // <database class>/<version>.json when building a database at an older version.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    // Plain JVM unit tests run against a stub android.jar whose every method throws unless
    // told otherwise. The timing and geometry code logs through android.util.Log, so without
    // this any test reaching a log call failed before its assertions ran.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

}

dependencies {

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation(platform("androidx.compose:compose-bom:2024.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.generativeai)
    implementation(libs.androidx.ui.test.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.graphics.core)

    implementation(libs.androidx.runtime.livedata)
    implementation(libs.play.services.location)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    //OWN:

    implementation("androidx.navigation:navigation-compose:2.7.3") //Nav
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0") //Graph

    implementation("androidx.room:room-runtime:2.5.1")
    ksp("androidx.room:room-compiler:2.5.1") // Annotation processor for Room
    implementation("androidx.room:room-ktx:2.5.1") // Kotlin extensions
    // Test-only: MigrationTestHelper for the instrumented migration tests. Was previously
    // an `implementation` dependency, which shipped the test helper inside the app.
    androidTestImplementation("androidx.room:room-testing:2.5.1")
    implementation(libs.androidx.ktx)
    implementation ("com.squareup.okhttp3:okhttp:4.11.0")

    // TrackBoard sync: background uploads that survive process death and wait for a network.
    implementation(libs.androidx.work.runtime.ktx)
    // Replays the TrackBoard API's real responses in JVM tests.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")


    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    testImplementation ("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2")

    testImplementation ("org.mockito.kotlin:mockito-kotlin:4.0.0")// Kotlin extension for Mockito
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2")  // for coroutine testing

    implementation ("androidx.compose.material3:material3:1.2.1")
    implementation ("androidx.compose.material:material-icons-extended:1.6.7")


    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0") // or the latest version)

    implementation ("com.google.accompanist:accompanist-pager:0.31.1-alpha")
    implementation ("com.google.accompanist:accompanist-pager-indicators:0.31.1-alpha")

    implementation ("com.google.code.gson:gson:2.10.1")

    //MAP
    implementation("org.maplibre.gl:android-sdk:11.11.0")

}


