plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // generates Room's database code at build time
}

// Room writes a JSON description of the database here. It is committed to Git
// so future database changes (migrations) can be checked against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// ---- The app's permanent identity (Google Play) ----
// The Kotlin namespace and the source packages stay com.example.dukanhisabnext: they are only names
// inside the code. What Android and Google Play see is the applicationId below, and it can NEVER change
// once the app is published.
val newAppId = "com.taqwatech.dokanhisab"

// The test package the app had before Group D. Used ONLY for the one-time move of the shopkeeper's
// data (docs/MOVE_TO_NEW_APP.md, step 1): `-PoldAppId=true` builds the latest code under the OLD id so it
// installs OVER the old app (keeping its data and running the database migrations). Never for anything else.
val oldAppId = "com.example.dukanhisabnext"
val buildForOldApp = providers.gradleProperty("oldAppId").orNull == "true"

android {
    namespace = "com.example.dukanhisabnext"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = if (buildForOldApp) oldAppId else newAppId
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        // The name under the launcher icon ("দোকান হিসাব"); the debug and the old-id builds say what they are.
        manifestPlaceholders["appLabel"] = if (buildForOldApp) "@string/app_name_old" else "@string/app_name"

        // SafeTestRunner = AndroidJUnitRunner + "never on a real phone" (see testing/SafeTestRunner.kt)
        testInstrumentationRunner = "com.example.dukanhisabnext.testing.SafeTestRunner"
    }

    buildTypes {
        debug {
            // com.taqwatech.dokanhisab.debug: a separate app with its own data, so the test app and the
            // real app can live on one phone. (Not for the one-time old-id build: it must replace the old app.)
            if (!buildForOldApp) {
                applicationIdSuffix = ".debug"
                manifestPlaceholders["appLabel"] = "@string/app_name_debug"
            }
        }
        release {
            // R8 shrinks and optimizes the WHOLE app (our code and the libraries together).
            // Do NOT add "packageScope" here: optimizing only the library packages moved one
            // Kotlin class (CollectionsKt__MutableCollectionsJVMKt) to another package while its
            // package-private parent stayed behind, and the release app crashed at start with
            // IllegalAccessError (M9 group A).
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        // The exported database schemas (app/schemas) are given to the phone tests, so the
        // migration test can rebuild an old database version from its JSON and upgrade it.
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}