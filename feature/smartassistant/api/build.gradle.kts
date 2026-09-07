plugins {
    id(ThunderbirdPlugins.Library.android)
}

android {
    namespace = "net.thunderbird.feature.smartassistant"
}

dependencies {
    testImplementation(libs.assertk)
    testImplementation(libs.kotlin.test)
}
