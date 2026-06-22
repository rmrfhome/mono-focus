import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun loadProperties(file: File): Properties =
    Properties().apply {
        if (file.isFile) {
            file.inputStream().use(::load)
        }
    }

fun String?.notBlankOrNull(): String? =
    this?.trim()?.takeIf { value -> value.isNotEmpty() }

fun loadRawProperty(file: File, key: String): String? =
    file.takeIf { it.isFile }
        ?.readLines()
        ?.firstNotNullOfOrNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
                null
            } else {
                val separatorIndex = trimmed.indexOf("=")
                if (separatorIndex <= 0) {
                    null
                } else {
                    val rawKey = trimmed.substring(0, separatorIndex).trim()
                    val rawValue = trimmed.substring(separatorIndex + 1).trim()
                    rawValue.takeIf { rawKey == key }?.notBlankOrNull()
                }
            }
        }

fun File.resolveRelativeOrAbsolute(path: String): File {
    val candidate = File(path)
    if (candidate.isAbsolute) return candidate

    val relativeToProperties = File(parentFile, path)
    if (relativeToProperties.isFile) return relativeToProperties

    return File(parentFile, candidate.name)
}

val localProperties = loadProperties(rootProject.file("local.properties"))
val signingPropertiesFile = listOfNotNull(
    localProperties.getProperty("monofocus.signing.properties").notBlankOrNull(),
    System.getenv("MONOFOCUS_SIGNING_PROPERTIES").notBlankOrNull(),
).firstNotNullOfOrNull { path ->
    rootProject.file(path).takeIf { file -> file.isFile }
}
val signingProperties = signingPropertiesFile?.let(::loadProperties)

android {
    namespace = "com.monofocus.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rmrfhome.monofocus"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = false
        }
    }

    signingConfigs {
        if (signingPropertiesFile != null && signingProperties != null) {
            create("playUpload") {
                val storeFilePath = loadRawProperty(signingPropertiesFile, "storeFile")
                    ?: signingProperties.getProperty("storeFile").notBlankOrNull()
                    ?: error("storeFile is missing from ${signingPropertiesFile.path}")
                storeFile = signingPropertiesFile.resolveRelativeOrAbsolute(storeFilePath)
                storePassword = signingProperties.getProperty("storePassword")
                    .notBlankOrNull()
                    ?: error("storePassword is missing from ${signingPropertiesFile.path}")
                keyAlias = signingProperties.getProperty("keyAlias")
                    .notBlankOrNull()
                    ?: error("keyAlias is missing from ${signingPropertiesFile.path}")
                keyPassword = signingProperties.getProperty("keyPassword")
                    .notBlankOrNull()
                    ?: error("keyPassword is missing from ${signingPropertiesFile.path}")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfigs.findByName("playUpload")?.let { signingConfig = it }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
