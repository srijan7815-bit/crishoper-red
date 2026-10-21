import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }
val hasSigning = keystoreProps.getProperty("storeFile") != null

// R8 needs roughly 3 GB of heap. Builders on constrained machines (CI containers,
// low-RAM laptops) can pass -Pred.release.minify=false to produce a larger but
// identical release APK without shrinking.
val minifyRelease = providers.gradleProperty("red.release.minify").orNull?.toBoolean() ?: true

android {
    namespace = "red.crishoper.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "red.crishoper.agent"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
        resourceConfigurations += listOf("en")
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/*.kotlin_module",
            "DebugProbesKt.bin"
        )
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

/*
 * The agent's system prompt is authored once, at the repository root, and copied
 * into assets at build time so the runtime and the docs can never drift apart.
 */
val syncSystemPrompt by tasks.registering(Copy::class) {
    from(rootProject.file("../system_prompt.md"))
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "system_prompt.md" }
}
tasks.named("preBuild") { dependsOn(syncSystemPrompt) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
