import java.util.Properties
import java.io.FileInputStream
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Lee las credenciales de firma desde keystore.properties o utiliza el keystore fijo (debug.keystore).
val debugKeystore = rootProject.file("debug.keystore")
val base64Keystore = rootProject.file("debug.keystore.base64")
if (!debugKeystore.exists() && base64Keystore.exists()) {
    try {
        val bytes = Base64.getDecoder().decode(base64Keystore.readText().trim())
        debugKeystore.writeBytes(bytes)
    } catch (e: Exception) {
        println("Warning: could not restore debug.keystore from base64: ${e.message}")
    }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasKeystore = keystorePropertiesFile.exists()
if (hasKeystore) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.jetgo.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jetgo.tvplayer"
        minSdk = 21          // cubre teléfonos, tablets, TV box y Android TV/Google TV
        targetSdk = 34
        // El número de build de GitHub Actions se usa como versionCode, así la app
        // puede compararlo con el que trae la última Release y saber si hay que actualizar.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0.${versionCode}"
        buildConfigField("String", "GITHUB_REPO", "\"pjaraf/JetGo\"")
    }

    signingConfigs {
        create("releaseSigning") {
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
            enableV1Signing = true
            enableV2Signing = true
        }
        // Firma fija garantizada en debug y release (evita conflictos de actualización)
        getByName("debug") {
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("releaseSigning")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // LibVLC trae binarios nativos (.so) para cada arquitectura de procesador — sin esto,
        // el empaquetado puede fallar por archivos nativos duplicados entre módulos.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Compose base (funciona en móvil, tablet y TV)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // Compose para TV: foco con D-pad, tarjetas optimizadas para control remoto
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")
    implementation("androidx.tv:tv-material:1.0.0")

    // Reproducción de streams (HLS / TS / MP4) — libVLC. Reemplaza por completo a
    // Media3/ExoPlayer: no queda ninguna dependencia de androidx.media3 en el proyecto.
    implementation("org.videolan.android:libvlc-all:3.6.4")

    // Red: cliente Xtream Codes API + parser M3U
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Guardar credenciales/servidor del usuario
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Carga de imágenes (logos de canales, carátulas)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Navegación
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
