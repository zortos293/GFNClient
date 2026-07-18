import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun Sequence<String?>.firstNonBlankOrNull(): String? =
    mapNotNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }.firstOrNull()

fun gradlePropertyValue(vararg names: String): String? =
    names.asSequence().map { name -> providers.gradleProperty(name).orNull }.firstNonBlankOrNull()

fun localPropertyValue(vararg names: String): String? =
    names.asSequence().map { name -> localProperties.getProperty(name) }.firstNonBlankOrNull()

fun environmentValue(vararg names: String): String? =
    names.asSequence().map { name -> providers.environmentVariable(name).orNull }.firstNonBlankOrNull()

fun firstNonBlankValue(vararg values: String?): String =
    values.asSequence().firstNonBlankOrNull().orEmpty()

fun buildConfigString(value: String): String =
    "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r") + "\""

val defaultPostHogProjectToken = "phc_pdob6BhBvbayfd7BA6zXBkty8o6EkxKYY7sF3ZwLymk3"
val defaultPostHogHost = "https://aa.printedwaste.com"

val postHogProjectToken = firstNonBlankValue(
    gradlePropertyValue("posthog.apiKey", "posthog.projectToken"),
    environmentValue("POSTHOG_API_KEY", "POSTHOG_PROJECT_TOKEN"),
    localPropertyValue("posthog.apiKey", "posthog.projectToken"),
    defaultPostHogProjectToken,
)
val postHogHost = firstNonBlankValue(
    gradlePropertyValue("posthog.host"),
    environmentValue("POSTHOG_HOST"),
    localPropertyValue("posthog.host"),
    defaultPostHogHost,
)
val buildingPlayReleaseBundle = gradle.startParameter.taskNames.any { taskName ->
    taskName.substringAfterLast(":").equals("bundleRelease", ignoreCase = true)
}

android {
    namespace = "com.opencloudgaming.opennow"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.opencloudgaming.opennow"
        minSdk = 23
        targetSdk = 36
        versionCode = 44
        versionName = "0.8.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "POSTHOG_PROJECT_TOKEN", buildConfigString(postHogProjectToken))
        buildConfigField("String", "POSTHOG_HOST", buildConfigString(postHogHost))
        buildConfigField("boolean", "APK_UPDATES_SUPPORTED", "true")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "APK_UPDATES_SUPPORTED", (!buildingPlayReleaseBundle).toString())
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets {
        if (buildingPlayReleaseBundle) {
            getByName("release").manifest.srcFile("src/playBundle/AndroidManifest.xml")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.media3:media3-common:1.10.1")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.work:work-runtime:2.11.2")

    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.4.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("io.github.webrtc-sdk:android:144.7559.09")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.posthog:posthog-android:3.51.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
