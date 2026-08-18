import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Credenciales de firma de release: viven FUERA del repo (nunca se commitean) en
// C:\Users\chris\android-signing\keystore.properties, generado una sola vez con
// keytool. Si el archivo no está (ej. clon nuevo en otra máquina), assembleRelease
// simplemente queda sin firmar en vez de romper el build de debug.
val keystorePropertiesFile = file("C:\\Users\\chris\\android-signing\\keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.privatestore.tvmanager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.privatestore.tvmanager"
        // minSdk 26 = Android 8.0 Oreo, requisito del proyecto.
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.1"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // Compose for TV
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Solo el set chico (Delete/PlayArrow/Refresh/Settings, que ya usamos): la
    // variante "-extended" agrega miles de clases por unos pocos glifos que ni
    // siquiera necesitamos, y eso alargaba el arranque en frío en la TV.
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Carga de íconos remotos (iconUrl del catálogo).
    // Coil 3.x arrastra versiones de androidx (compose-runtime, core-ktx, etc.)
    // que exigen compileSdk 35 + AGP 8.6+; este proyecto está en compileSdk 34 /
    // AGP 8.5.2 a propósito, así que se usa Coil 2.x (incluye OkHttp por defecto,
    // sin artefacto de red aparte).
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Red / descarga de catálogo y APKs
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")

    // Deja que Android instale los "baseline profiles" que Compose/Material3 ya
    // traen empaquetados en sus propios .aar: evita que las primeras corridas se
    // ejecuten interpretadas (sin AOT) en vez de compiladas, que es buena parte
    // del arranque en frío lento en fierros modestos como esta TV box.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
