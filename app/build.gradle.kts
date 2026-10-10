import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * La clave de Gemini se lee de local.properties, que no se versiona.
 * Si falta, la app compila igual y el asistente queda desactivado con un
 * mensaje claro en pantalla, en vez de fallar en tiempo de ejecución.
 */
private val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY", "")

/**
 * Dirección del servidor propio. Si está vacía, la app guarda todo en el
 * teléfono, como antes de que el servidor existiera.
 */
val backendUrl: String = localProperties.getProperty("BACKEND_URL", "")
val googleWebClientId: String = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "").trim()

/** Correo de soporte. Si está vacío, la pantalla de Soporte no ofrece contacto. */
val supportEmail: String = localProperties.getProperty("SUPPORT_EMAIL", "").trim()

android {
    namespace = "com.peru.expresdm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.peru.expresdm"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "GEMINI_MODEL", "\"gemini-3.5-flash-lite\"")
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"$supportEmail\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric necesita los recursos compilados para inflar layouts.
            isIncludeAndroidResources = true

            all {
                // Las capturas necesitan la librería nativa de Robolectric, que
                // Windows bloquea en equipos con Smart App Control o WDAC
                // activos. Se ejecutan solo cuando se piden:
                //     ./gradlew testDebugUnitTest -Pscreenshots
                if (!project.hasProperty("screenshots")) {
                    it.filter.excludeTestsMatching("*ScreenshotTest")
                }
            }
        }
    }

    signingConfigs {
        // Solo se define si local.properties trae los datos del keystore, para
        // que quien clone el repo sin él pueda seguir compilando en debug.
        val storeFileName = localProperties.getProperty("RELEASE_STORE_FILE", "")
        if (storeFileName.isNotBlank() && rootProject.file(storeFileName).exists()) {
            create("release") {
                storeFile = rootProject.file(storeFileName)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Credential Manager para Google; compatibles con compileSdk 35.
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // AndroidX esenciales
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)

    // Material Design 3
    implementation(libs.material)

    // Navegacion entre pantallas (una sola Activity + fragments)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Ciclo de vida / ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Mapas OpenStreetMap: libre, sin API key ni cuenta de facturacion
    implementation(libs.osmdroid.android)

    // Dependencias de prueba
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
