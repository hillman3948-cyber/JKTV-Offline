plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.jktv.offline"
    compileSdk = 35

    val configuredVersionName = providers.gradleProperty("versionName").orElse("0.1.0").get()
    val configuredVersionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()
    val repositoryValue = providers.gradleProperty("jktvGithubRepository")
        .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
        .orElse("hillman3948-cyber/JKTV-Offline")
        .get()
    val githubRepository = repositoryValue.takeIf { Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(it) }.orEmpty()

    defaultConfig {
        applicationId = providers.gradleProperty("jktvApplicationId")
            .orElse("com.jktv.offline").get()
        minSdk = 26
        targetSdk = 35
        versionCode = configuredVersionCode
        versionName = configuredVersionName
        buildConfigField("String", "GITHUB_REPOSITORY", "\"$githubRepository\"")
        buildConfigField(
            "String",
            "GITHUB_LIBRARY_MANIFEST_URL",
            "\"${if (githubRepository.isNotEmpty()) "https://github.com/$githubRepository/releases/latest/download/library-manifest.json" else ""}\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

configurations.configureEach {
    resolutionStrategy.force(
        "androidx.core:core:1.15.0",
        "androidx.core:core-ktx:1.15.0",
        "androidx.tracing:tracing:1.2.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0",
        "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20",
        "com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.28",
        "com.google.guava:guava:33.0.0-jre",
        "androidx.collection:collection:1.4.4",
        "androidx.collection:collection-ktx:1.4.4"
    )
}
