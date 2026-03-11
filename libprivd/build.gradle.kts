
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.benwiegand.projection.libprivd"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
