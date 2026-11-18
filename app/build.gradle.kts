plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Librerías nativas del decodificador de audio FFmpeg (se sacan de su paquete al compilar; ver "ffmpegNative" abajo).
val ffmpegJniDir: File = layout.buildDirectory.dir("ffmpeg-aar/jni").get().asFile

android {
    namespace = "com.example.mitvplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ultrapremier.tv"
        // Fire OS 6 (Android 7.1 = API 25) en adelante
        minSdk = 25
        targetSdk = 34
        // En GitHub cada compilación sube el número, para que Android la instale como actualización.
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        // Versión de prueba (rama diseno-nuevo): otro paquete, así se instala junto a la app normal.
        if (System.getenv("PREVIEW") == "1") applicationIdSuffix = ".prueba"
        // Solo español e inglés: quita los idiomas de las librerías que no se usan y la app pesa menos.
        resourceConfigurations += listOf("es", "en")
        // Los Fire TV son ARM: solo se incluyen esas versiones del decodificador de audio (la app pesa menos).
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(ffmpegJniDir)
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
        }
    }

    // La llave de firma llega por variables de entorno (la crea el flujo de GitHub en cada compilación).
    // Si no existen (por ejemplo, al compilar desde Android Studio), se firma con la llave de depuración.
    val releaseKeystorePath: String? = System.getenv("KEYSTORE_PATH")

    signingConfigs {
        create("release") {
            if (releaseKeystorePath != null) {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: quita lo que no se usa y acorta el código; la app pesa menos y abre más rápido.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false
            signingConfig = if (releaseKeystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
}

// Decodificador de audio FFmpeg (el que usa Jellyfin en su app para Fire TV). De ese paquete solo se toman las
// librerías nativas (libffmpegJNI.so); el código Java que las usa es el de Media3 1.4.1, que va en
// app/src/main/java/androidx/media3/decoder/ffmpeg (es idéntico en 1.4.1 y 1.5.0, así no se mezclan versiones).
val ffmpegNative: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")

    // Red, imágenes y sesión
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Reproductor
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-decoder:1.4.1")
    // Sonido en todos los canales (MP2, AC3, E-AC3, DTS…): ver AudioSupport.kt
    ffmpegNative("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1@aar")

    // Instala el perfil de arranque (baseline-prof.txt) para que la app abra más rápido
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Convertir grabaciones (.ts) a MP4
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-muxer:1.4.1")
}

// Saca libffmpegJNI.so (ARM) del paquete del decodificador antes de compilar.
val extractFfmpegJni by tasks.registering(Sync::class) {
    from(provider { ffmpegNative.map { zipTree(it) } }) {
        include("jni/armeabi-v7a/*.so", "jni/arm64-v8a/*.so")
    }
    into(ffmpegJniDir.parentFile)
}
tasks.named("preBuild") {
    dependsOn(extractFfmpegJni)
}
// La tarea que junta las librerías nativas las lee: se declara que va después (Gradle 8 lo exige).
tasks.matching { it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(extractFfmpegJni)
}
