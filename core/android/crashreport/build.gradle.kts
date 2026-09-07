plugins {
    id(ThunderbirdPlugins.Library.android)
}

android {
    namespace = "net.thunderbird.core.android.crashreport"
}

dependencies {
    implementation(libs.koin.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.assertk)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
}

codeCoverage {
    branchCoverage = 80
    lineCoverage = 90
}
