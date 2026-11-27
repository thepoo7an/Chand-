import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "ir.thepoo7an.chand"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "ir.thepoo7an.chand"
    minSdk = 24
    targetSdk = 36
    val resolvedVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
      ?: project.findProperty("versionCode")?.toString()?.toIntOrNull()
      ?: 1
    val resolvedVersionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
      ?: project.findProperty("versionName")?.toString()?.takeIf { it.isNotBlank() }
      ?: "1.0.0-dev"
    println("Nerkhnama build version -> versionCode=$resolvedVersionCode, versionName=$resolvedVersionName")

    versionCode = resolvedVersionCode
    versionName = resolvedVersionName

    val githubRepo = System.getenv("GITHUB_REPOSITORY")?.takeIf { it.isNotBlank() }
      ?: project.findProperty("githubRepo")?.toString()?.takeIf { it.isNotBlank() }
      ?: "thepoo7an/chand-iran-currency-gold-crypto"
    buildConfigField("String", "GITHUB_REPOSITORY", "\"$githubRepo\"")

    val expectedReleaseCertSha256 = (System.getenv("CERT_SHA256")?.takeIf { it.isNotBlank() }
      ?: project.findProperty("expectedReleaseCertSha256")?.toString()?.takeIf { it.isNotBlank() }
      ?: "8cbacff1b11ee42a6e527d1a577007c20d375a75d419a368852b301ee2846d8b")
      .lowercase()
      .replace(":", "")
      .trim()
    buildConfigField("String", "EXPECTED_RELEASE_CERT_SHA256", "\"$expectedReleaseCertSha256\"")

    // Optional U.S. EIA API v2 key for official Brent/WTI spot validation. Must be baked in at
    // build time: Android app processes cannot read shell environment variables at runtime.
    // Provide it as `EIA_API_KEY=...` or `-PeiaApiKey=...`; empty by default (feature disabled).
    val eiaApiKey = (System.getenv("EIA_API_KEY")?.takeIf { it.isNotBlank() }
      ?: project.findProperty("eiaApiKey")?.toString()?.takeIf { it.isNotBlank() }
      ?: "")
      .trim()
      .replace("\\", "")
      .replace("\"", "")
    buildConfigField("String", "EIA_API_KEY", "\"$eiaApiKey\"")

    // Live-config trust store: `keyId:base64SubjectPublicKeyInfo` pairs. Only PUBLIC keys live
    // here; the matching private key stays outside the repository (LIVE_CONFIG_SIGNING_KEY secret).
    // Override with LIVE_CONFIG_PUBLIC_KEYS (env) or -PliveConfigPublicKeys=... for forks.
    val liveConfigKeysFile = rootProject.file("gradle/live-config-keys.properties")
    val liveConfigKeysFromFile = if (liveConfigKeysFile.exists()) {
      Properties().apply {
        liveConfigKeysFile.inputStream().use { stream -> load(stream) }
      }.getProperty("liveConfigPublicKeys").orEmpty()
    } else {
      ""
    }
    val liveConfigPublicKeys = (
      System.getenv("LIVE_CONFIG_PUBLIC_KEYS")?.takeIf { it.isNotBlank() }
        ?: project.findProperty("liveConfigPublicKeys")?.toString()?.takeIf { it.isNotBlank() }
        ?: liveConfigKeysFromFile
      )
      .trim()
      .replace("\\", "")
      .replace("\"", "")
    buildConfigField("String", "LIVE_CONFIG_PUBLIC_KEYS", "\"$liveConfigPublicKeys\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      applicationIdSuffix = ".debug"
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    resources {
      excludes += listOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "DebugProbesKt.bin",
        "kotlin-tooling-metadata.json"
      )
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  if (project.hasProperty("enableAbiSplits")) {
    splits {
      abi {
        isEnable = true
        reset()
        include("armeabi-v7a", "arm64-v8a")
        isUniversalApk = true
      }
    }
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  // EIA_API_KEY is injected explicitly by the buildConfigField above so local `.env` values and
  // CI env vars can never produce a duplicate BuildConfig field.
  ignoreList.add("EIA_API_KEY")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)
  implementation(libs.retrofit)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.moshi.kotlin.codegen)
}
