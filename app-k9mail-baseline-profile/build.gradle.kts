plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
    id("thunderbird.quality.detekt.typed")
    id("thunderbird.quality.spotless")
}

android {
    namespace = "com.fsck.k9.baselineprofile"
    compileSdk = ThunderbirdProjectConfig.Android.sdkCompile
    targetProjectPath = ":app-k9mail"

    defaultConfig {
        minSdk = ThunderbirdProjectConfig.Android.sdkMin
        targetSdk = ThunderbirdProjectConfig.Android.sdkTarget
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = ThunderbirdProjectConfig.Compiler.javaCompatibility
        targetCompatibility = ThunderbirdProjectConfig.Compiler.javaCompatibility
    }

    flavorDimensions += "app"
    productFlavors {
        create("kemi") {
            dimension = "app"
        }
    }

    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget = ThunderbirdProjectConfig.Compiler.jvmTarget
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit.ktx)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
}
