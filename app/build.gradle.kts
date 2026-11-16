plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rakdatak.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rakdatak.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 16
        versionName = "0.5.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appName"] = "ركضتك"
    }

    buildTypes {
        getByName("debug") {
            // CI debug signing keys differ by run; separate package prevents an
            // update-signature conflict with the previously installed 0.5.1 test APK.
            applicationIdSuffix = ".preview053"
            versionNameSuffix = "-TEST"
            manifestPlaceholders["appName"] = "ركضتك 0.5.3 - تجربة"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:training"))

    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
