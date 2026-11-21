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
    versionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
      ?: project.findProperty("versionCode")?.toString()?.toIntOrNull()
      ?: 1
    versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
      ?: project.findProperty("versionName")?.toString()?.takeIf { it.isNotBlank() }
      ?: "1.0"

    val githubRepo = System.getenv("GITHUB_REPOSITORY")?.takeIf { it.isNotBlank() }
      ?: project.findProperty("githubRepo")?.toString()?.takeIf { it.isNotBlank() }
      ?: "thepoo7an/chand-iran-currency-gold-crypto"
    buildConfigField("String", "GITHUB_REPOSITORY", "\"$githubRepo\"")

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
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
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
