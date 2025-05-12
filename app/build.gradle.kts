plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    kotlin("plugin.parcelize") // 👈 AGGIUNGI QUESTO
}

android {
    namespace = "io.github.luposolitario.mbi"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.luposolitario.mbi"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders.putIfAbsent("appAuthRedirectScheme", "com.example.myapplication")

    }

    sourceSets {
        getByName("main").assets.srcDirs("src/main/assets")
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


    buildFeatures {
        compose = true          // <‑‑ abilita Compose
        buildConfig = true // Aggiungi questa riga per Kotlin DSL
    }

}





dependencies {
    // Networking
    val retrofitVersion = "2.9.0"
    val okhttpVersion = "4.11.0"
    val roomVersion = "2.5.1"

    implementation("net.openid:appauth:0.7.1") {
        exclude(group = "com.android.support")
    }

    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    // Aggiungi Glide per il caricamento delle immagini
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")

    //photoview
    implementation("io.github.baseflow:photoview:2.3.0")

    // Android Standard Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat) {
        exclude(group = "com.android.support")
    }
    implementation(libs.material) {
        exclude(group = "com.android.support")
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Aggiornamento RecyclerView (se necessario, verifica la versione più recente)
    implementation("androidx.recyclerview:recyclerview:1.3.0")

    // ExoPlayer (Media3)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.2.1") // Per supporto DASH
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")  // Per supporto HLS
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")


    implementation("androidx.media:media:1.6.0")   // ⬅️ media‑compat

    //Compose
    implementation(platform("androidx.compose:compose-bom:2025.04.01")) // :contentReference[oaicite:3]{index=3}

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")   // 🔑
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")      // 🔑

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    implementation("com.google.accompanist:accompanist-themeadapter-material3:0.36.0")

    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-android-compiler:2.56.1")

    implementation("androidx.compose.material:material-icons-core:1.6.7") // Usa l'ultima versione stabile compatibile con la tua versione di Compose UI
    implementation("androidx.compose.material:material-icons-extended:1.6.7") // Usa l'ultima versione stabile compatibile con la tua versione di Compose UI

    // Room runtime & coroutines
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    // Compiler via KSP
    ksp("androidx.room:room-compiler:$roomVersion")

}