plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Версия приложения приходит из метки релиза: метка v1.2.3 даёт APP_VERSION=1.2.3.
 * versionCode = major * 1_000_000 + minor * 1_000 + patch, он растёт вместе с версией:
 * 0.0.1 → 1, 0.0.2 → 2, 0.1.0 → 1000, 1.0.0 → 1000000.
 * Без APP_VERSION (обычная проверочная сборка) версия 0.0.0-dev, код 1.
 */
val devVersion = "0.0.0-dev"
val appVersionName: String = providers.environmentVariable("APP_VERSION").getOrElse(devVersion)

fun versionCodeOf(version: String): Int {
    if (version == devVersion) return 1
    val match = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})""").matchEntire(version)
        ?: throw GradleException("APP_VERSION должна выглядеть как 1.2.3, а получено: '$version'")
    val (major, minor, patch) = match.destructured
    return major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
}

val appVersionCode: Int = versionCodeOf(appVersionName)

fun requiredEnv(name: String): String =
    providers.environmentVariable(name).orNull
        ?: throw GradleException("Для подписи релиза нужна переменная окружения $name")

android {
    namespace = "io.github.dumahuch.pogodaapp"
    // Compose 1.12 требует компиляции против API 37. Работа на телефонах не меняется: minSdk 36, targetSdk 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.dumahuch.pogodaapp"
        minSdk = 36
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // Только три языка интерфейса: библиотеки не тянут лишние переводы в APK.
    androidResources {
        localeFilters += listOf("en", "ru", "de")
    }

    // Подпись релиза включается, только если задан путь к ключу (на GitHub его кладёт сборка релиза).
    signingConfigs {
        val keystorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = requiredEnv("KEYSTORE_PASSWORD")
                keyAlias = requiredEnv("KEY_ALIAS")
                keyPassword = requiredEnv("KEY_PASSWORD")
                // v3 нужна, чтобы при необходимости можно было сменить ключ, не потеряв обновления.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    val releaseSigning = signingConfigs.findByName("release")

    buildTypes {
        debug {
            // Проверочная сборка не конфликтует с настоящим приложением на телефоне.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        // Сознательные решения: цель 36 (телефоны на Android 16), только портрет.
        disable += setOf("OldTargetApi", "LockedOrientationActivity", "DiscouragedApi")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
}

// Для проверки в CI: печатает версию, которую получит сборка.
tasks.register("printVersion") {
    val name = appVersionName
    val code = appVersionCode
    doLast { println("versionName=$name versionCode=$code") }
}
