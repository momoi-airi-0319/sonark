plugins {
    id("com.android.library")
}

android {
    namespace = "uniffi.sonark_sdk"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }
    
    // Explicitly handle Kotlin for AGP compatibility
    sourceSets {
        getByName("main") {
            java.directories.add("src/main/java")
            jniLibs.directories.add("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation(libs.jna)
}
