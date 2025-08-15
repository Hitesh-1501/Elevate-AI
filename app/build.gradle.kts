plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.elevateai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.elevateai"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

    }
    buildFeatures{
        buildConfig = true// This enables the BuildConfig class
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "GEMINI_API_KEY", properties["GEMINI_API_KEY"].toString())
        }
        debug {
            buildConfigField("String", "GEMINI_API_KEY", properties["GEMINI_API_KEY"].toString())
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Firebase BoM - auto-manages versions
    implementation(platform("com.google.firebase:firebase-bom:32.7.3"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")

// AndroidX Credential Manager
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.gms:play-services-auth:21.1.1")

// Google Identity Services
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Add this for Storage
    implementation("com.google.firebase:firebase-storage-ktx")
    // Add this for easy image loading
    implementation("io.coil-kt:coil:2.6.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.2")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")


    // Google Gemini AI SDK
    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")
    // MVVM & Coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    implementation("io.noties.markwon:core:4.6.2") // For Markdown formatting
}