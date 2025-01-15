// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0" // Match your Kotlin version
    alias(libs.plugins.compose.compiler) apply false
}