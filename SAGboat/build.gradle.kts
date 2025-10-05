plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}

val compileSdkVersion by extra(34)  // Ensure alignment with module build files
