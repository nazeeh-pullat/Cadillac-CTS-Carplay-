plugins { id("com.android.application") }

val legacyAndroid51 = providers.gradleProperty("legacyAndroid51").map(String::toBoolean).getOrElse(false)
val authenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR").orNull
val legacyTestJvm = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }

android {
    namespace = "com.shihab.diplay.legacy"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.shihab.diplay.legacy"
        minSdk = if (legacyAndroid51) 22 else 24
        targetSdk = 37
        versionCode = 6
        versionName = "0.6-android51-experimental"
        testInstrumentationRunner = "com.shihab.diplay.legacy.UiCaptureInstrumentation"
        ndk { abiFilters += "armeabi-v7a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    authenticationAssets?.let { sourceSets.getByName("main").assets.srcDir(it) }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // The API-22 Robolectric runner predates Java 25 class files.
        unitTests.all { it.javaLauncher.set(legacyTestJvm) }
    }
}

dependencies {
    implementation(project(":shared"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    // Newer Robolectric versions dropped the API-22 framework that this host targets.
    testImplementation("org.robolectric:robolectric:4.14.1")
}
