plugins { id("com.android.application") }

android {
    namespace = "dev.erinlkolp.glasslauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.erinlkolp.glasslauncher"
        minSdk = 22
        targetSdk = 22
        versionCode = 2
        versionName = "0.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation(project(":gesture-core"))
    testImplementation("junit:junit:4.13.2")
}
