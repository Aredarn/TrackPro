plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("kotlinx-serialization")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.trackpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.trackpro"
        minSdk = 26
        targetSdk = 34
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

dependencies {

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.generativeai)
    implementation(libs.androidx.ui.test.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.graphics.core)

    implementation(libs.androidx.runtime.livedata)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    //OWN:

    implementation("androidx.navigation:navigation-compose:2.7.3") //Nav
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0") //Graph

    implementation("androidx.room:room-runtime:2.5.1")
    ksp("androidx.room:room-compiler:2.5.1") // Annotation processor for Room
    implementation("androidx.room:room-ktx:2.5.1") // Kotlin extensions
    implementation ("androidx.room:room-testing:2.5.1")
    implementation(libs.androidx.ktx)
    implementation ("com.squareup.okhttp3:okhttp:4.11.0")


    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    testImplementation ("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2")

    testImplementation ("org.mockito.kotlin:mockito-kotlin:4.0.0")// Kotlin extension for Mockito
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2")  // for coroutine testing

    implementation ("androidx.compose.material3:material3:<latest_version>")
    implementation ("androidx.compose.material:material-icons-extended:<latest_version>")


    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0") // or the latest version)

    implementation ("com.google.accompanist:accompanist-pager:0.31.1-alpha")
    implementation ("com.google.accompanist:accompanist-pager-indicators:0.31.1-alpha")

    implementation(platform("androidx.compose:compose-bom:2024.03.00")) // Update to latest BOM

}


