plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "xyz.fivekov.terminal"
    compileSdk = 36

    defaultConfig {
        applicationId = "xyz.fivekov.terminal"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

}

// Build frontend assets before Android compilation
tasks.register<Exec>("buildFrontend") {
    workingDir = file("${rootDir}/terminal-frontend")
    commandLine("npm", "run", "build")
    inputs.dir("${rootDir}/terminal-frontend/src")
    inputs.file("${rootDir}/terminal-frontend/index.html")
    inputs.file("${rootDir}/terminal-frontend/package.json")
    inputs.file("${rootDir}/terminal-frontend/vite.config.ts")
    outputs.dir("src/main/assets/www")
}

tasks.named("preBuild") {
    dependsOn("buildFrontend")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.ktx)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.runtime)

    implementation(libs.koin.android)
    implementation(libs.sshlib)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.coroutines.android)
    implementation(libs.webkit)
    implementation(libs.recyclerview)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
}
