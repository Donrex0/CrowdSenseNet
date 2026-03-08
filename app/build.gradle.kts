    plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp") version "2.0.20-1.0.24"
    id("org.jetbrains.kotlin.android") version "2.0.20"
}

android {
    namespace = "com.example.crowdsensenet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.crowdsensenet"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    dependencies {
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.material)
        implementation(libs.androidx.activity)
        implementation(libs.androidx.constraintlayout)
        // Maps (OpenStreetMap - Free Alternative)
        implementation("org.osmdroid:osmdroid-android:6.1.18")
        implementation("org.osmdroid:osmdroid-mapsforge:6.1.18")
        
        // Firebase
        implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
        implementation("com.google.firebase:firebase-firestore-ktx")
        
        // WorkManager
        implementation("androidx.work:work-runtime-ktx:2.9.0")
        implementation(libs.androidx.room.common.jvm)
        implementation(libs.androidx.room.ktx)
        ksp(libs.androidx.room.compiler)
        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)

        implementation("com.google.android.gms:play-services-maps:18.2.0")
        implementation("com.google.android.gms:play-services-location:21.0.1")
    }
}