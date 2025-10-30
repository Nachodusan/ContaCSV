plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Para Postgrest + @Serializable
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.example.contacsv10"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.contacsv10"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
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
        // Si tu JDK es 17 puedes subir estas dos líneas a JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

/* ❌ NO pongas repositories aquí. Van en settings.gradle.kts */

dependencies {
    // AndroidX / UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    // Mapas (OSMDroid)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Supabase (desde Maven Central, sin JitPack)
    implementation(platform("io.github.jan-tennert.supabase:bom:2.5.0"))

    implementation("io.github.jan-tennert.supabase:gotrue-kt")     // Auth
    implementation("io.github.jan-tennert.supabase:postgrest-kt")  // Tablas

    // Ktor (requerido por supabase-kt) y Serialization
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Utilidades
    implementation("com.google.code.gson:gson:2.11.0")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
