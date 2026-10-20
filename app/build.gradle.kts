import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Why the release cannot be signed with an upload key, or null if keystore.properties is usable.
 * The Android debug key is refused: Google Play rejects bundles signed with it.
 */
fun releaseSigningProblem(fileExists: Boolean, keystore: Properties, storeExists: (String) -> Boolean): String? {
    val help = "ver store/PLAY_CONSOLE.md, secção 1"
    if (!fileExists) return "keystore.properties em falta — $help."
    val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").filter { keystore.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) return "keystore.properties incompleto (falta ${missing.joinToString()}) — $help."
    val storeFile = keystore.getProperty("storeFile")
    if (!storeExists(storeFile)) return "A keystore indicada em keystore.properties não existe: $storeFile — $help."
    if (File(storeFile).name == "debug.keystore" || keystore.getProperty("keyAlias") == "androiddebugkey") {
        return "keystore.properties aponta para a chave de debug do Android, que a Google Play rejeita — $help."
    }
    return null
}

// Release signing comes from keystore.properties at the project root, which is git-ignored.
// Release builds refuse to run without a usable upload key (see checkReleaseSigning below);
// debug builds never need it.
val keystoreFile: File = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use(::load) }
val releaseSigningProblem: String? = releaseSigningProblem(keystoreFile.exists(), keystore) { rootProject.file(it).exists() }

android {
    namespace = "io.github.admilsonlandim.veyra"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.admilsonlandim.veyra"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningProblem == null) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    bundle {
        // The in-app language switch needs every language in the install: an App Bundle split
        // by language would leave the other language's texts out.
        language { enableSplit = false }
    }
    androidResources {
        // Generates the locale_config for Android 13+'s per-app language picker from values-*.
        generateLocaleConfig = true
    }
}

// Fails release builds early (before compiling) instead of producing an unsigned or debug-signed
// bundle. Only the message is captured, so the task stays configuration-cache compatible.
val checkReleaseSigning by tasks.registering {
    val problem = releaseSigningProblem
    doLast { if (problem != null) throw GradleException(problem) }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseSigning) }

room {
    // Exported schemas are committed: they document every version and back migration tests.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    // Cuts a section of a track into a new file, for "set as ringtone" with an excerpt.
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.appcompat)
    // Aligns the app with room-testing (see libs.versions.toml).
    implementation(libs.kotlinx.serialization.core)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}