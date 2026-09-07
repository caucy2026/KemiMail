plugins {
    id(ThunderbirdPlugins.Library.android)
}

android {
    namespace = "net.thunderbird.feature.smartassistant.internal"
}

dependencies {
    implementation(projects.feature.smartassistant.api)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.assertk)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
