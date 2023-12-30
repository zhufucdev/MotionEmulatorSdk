plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    signing
    `maven-publish`
}

version = "1.0.0"
group = "com.zhufucdev.me"

android {
    namespace = "com.zhufucdev.me"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // Xposed
    implementation(libs.yukihook)
    // Internal
    implementation(project(":stub"))
    implementation(project(":plugin"))
    // Misc
    implementation(libs.corektx)
    implementation(libs.annotation)
    implementation(libs.jnanoid)
    implementation(libs.maps.util)
    implementation(libs.kotlinx.coroutine)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.junit)
    androidTestImplementation(libs.android.junit)
    androidTestImplementation(libs.espresso)
}

publish {
    signing {
        sign(publications.getAt(it))
    }
}
