import org.cyclonedx.Version
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.Sync

plugins {
    id(ThunderbirdPlugins.App.androidCompose)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.cyclonedx.bom)
    alias(libs.plugins.dependency.guard)
    alias(libs.plugins.tb.app.badging)
    alias(libs.plugins.tb.app.versioning)
}

val testCoverageEnabled = hasProperty("testCoverageEnabled")
val kemiVersionCode = 39040
val kemiVersionName = "20.1"

android {
    namespace = "com.fsck.k9"

    defaultConfig {
        applicationId = "com.fsck.k9"
        testApplicationId = "com.fsck.k9.tests"

        versionCode = kemiVersionCode
        versionName = kemiVersionName

        buildConfigField("String", "CLIENT_INFO_APP_NAME", "\"KEMI Mail\"")
        buildConfigField("String", "KEMI_UPDATE_API_BASE_URL", "\"https://kemi.newlinksz.com/kd-api\"")
    }

    androidResources {
        // Keep in sync with the resource string array "supported_languages"
        localeFilters += listOf(
            "ar",
            "be",
            "bg",
            "br",
            "ca",
            "co",
            "cs",
            "cy",
            "da",
            "de",
            "el",
            "en",
            "en-rGB",
            "eo",
            "es",
            "et",
            "eu",
            "fa",
            "fi",
            "fr",
            "fy",
            "ga",
            "gd",
            "gl",
            "hr",
            "hu",
            "in",
            "is",
            "it",
            "iw",
            "ja",
            "ko",
            "lt",
            "lv",
            "nb",
            "nl",
            "nn",
            "pl",
            "pt-rBR",
            "pt-rPT",
            "ro",
            "ru",
            "sk",
            "sl",
            "sq",
            "sr",
            "sv",
            "ta-rIN",
            "tr",
            "uk",
            "vi",
            "zh-rCN",
            "zh-rTW",
        )
    }

    signingConfigs {
        createSigningConfig(project, SigningType.K9_RELEASE, isUpload = false)
    }

    buildTypes {
        val isCI = project.findProperty("ci") == "true"
        release {
            signingConfig = signingConfigs.getByType(SigningType.K9_RELEASE)

            isMinifyEnabled = !isCI
            isShrinkResources = !isCI

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        create("performance") {
            initWith(getByName("release"))

            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"

            isDebuggable = false

            matchingFallbacks += listOf("release")
        }

        debug {
            applicationIdSuffix = ".debug"

            enableUnitTestCoverage = testCoverageEnabled
            enableAndroidTestCoverage = testCoverageEnabled

            isMinifyEnabled = false
        }
    }

    sourceSets.getByName("performance").kotlin.directories.add("src/release/kotlin")

    flavorDimensions += "app"
    productFlavors {
        create("kemi") {
            dimension = "app"
            buildConfigField("String", "PRODUCT_FLAVOR_APP", "\"kemi\"")
        }
    }

    packaging {
        jniLibs {
            excludes += listOf("kotlin/**")
        }

        resources {
            excludes += listOf(
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }
}

dependencies {
    baselineProfile(projects.appK9mailBaselineProfile)

    implementation(projects.appCommon)
    implementation(projects.core.ui.compose.common)
    implementation(projects.core.ui.compose.designsystem)
    implementation(projects.core.ui.compose.theme2)
    implementation(projects.core.ui.contract)
    implementation(projects.core.ui.legacy.theme2.k9mail)
    implementation(projects.feature.launcher)
    implementation(projects.feature.mail.message.list.api)
    implementation(projects.feature.mail.message.list.internal)
    implementation(projects.feature.mail.message.reader.api)

    implementation(projects.legacy.core)
    implementation(projects.legacy.ui.legacy)

    implementation(projects.core.featureflag)

    implementation(projects.feature.account.settings.impl)

    "kemiImplementation"(projects.feature.funding.noop)
    implementation(projects.feature.migration.launcher.noop)
    implementation(projects.feature.onboarding.migration.noop)
    implementation(projects.feature.thundermail.api)
    implementation(projects.feature.thundermail.k9mail)
    implementation(projects.feature.telemetry.noop)
    implementation(projects.feature.widget.messageList)
    implementation(projects.feature.widget.messageListGlance)
    implementation(projects.feature.widget.shortcut)
    implementation(projects.feature.widget.unread)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.okhttp)

    implementation(projects.feature.autodiscovery.api)
    debugImplementation(projects.backend.demo)
    debugImplementation(projects.feature.autodiscovery.demo)

    // Required for DependencyInjectionTest
    testImplementation(projects.feature.account.api)
    testImplementation(projects.feature.account.common)
    testImplementation(projects.feature.thundermail.internal.common)
    testImplementation(projects.plugins.openpgpApiLib.openpgpApi)
    testImplementation(libs.appauth)
    testImplementation(libs.okhttp.mockwebserver)
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}

dependencyGuard {
    configuration("kemiReleaseRuntimeClasspath")
}

fun CyclonedxDirectTask.configureKemiReleaseSbom() {
    group = "reporting"
    description = "Generates the KEMI release runtime CycloneDX SBOM."
    includeConfigs = listOf("kemiReleaseRuntimeClasspath")
    skipConfigs = emptyList()
    testConfigs = emptyList()
    projectType = Component.Type.APPLICATION
    schemaVersion = Version.VERSION_17
    componentGroup = "app.k9mail"
    componentName = "KEMI Mail"
    componentVersion = kemiVersionName
    includeBomSerialNumber = false
    includeLicenseText = false
    includeMetadataResolution = true
    includeBuildEnvironment = false
    includeBuildSystem = false
    jsonOutput = layout.buildDirectory.file("reports/sbom/kemi-release.cdx.json")
    xmlOutput.convention(null as RegularFile?)
}

val cyclonedxKemiReleaseBom = tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    configureKemiReleaseSbom()
}

tasks.register("kemiReleaseSbom") {
    group = "reporting"
    description = "Generates the KEMI release runtime SBOM."
    dependsOn(cyclonedxKemiReleaseBom)
}

val validateKemiReleaseSigning = tasks.register("validateKemiReleaseSigning") {
    group = "verification"
    description = "Verifies that the official KEMI release signing configuration is available."
    val hasReleaseSigning = android.signingConfigs.findByName(SigningType.K9_RELEASE.type) != null

    doLast {
        check(hasReleaseSigning) {
            "Official KEMI release signing is missing. Use kemiDebugApk for a Debug-signed test build."
        }
    }
}

tasks.configureEach {
    if (name == "assembleKemiRelease") {
        mustRunAfter(validateKemiReleaseSigning)
    }
}

tasks.register<Sync>("kemiDebugApk") {
    group = "build"
    description = "Builds the standard Debug-signed KEMI APK."
    dependsOn("assembleKemiDebug")

    from(layout.buildDirectory.dir("outputs/apk/kemi/debug")) {
        include("*.apk")
        rename(".*\\.apk", "KEMI-Mail-v$kemiVersionName-debug.apk")
    }
    into(layout.buildDirectory.dir("outputs/kemi/debug"))
}

tasks.register<Sync>("kemiPerformanceApk") {
    group = "build"
    description = "Builds a release-optimized, Debug-signed KEMI APK for startup performance testing."
    dependsOn("assembleKemiPerformance")

    from(layout.buildDirectory.dir("outputs/apk/kemi/performance")) {
        include("*.apk")
        rename(".*\\.apk", "KEMI-Mail-v$kemiVersionName-performance-debug.apk")
    }
    into(layout.buildDirectory.dir("outputs/kemi/performance"))
}

tasks.register<Sync>("kemiReleaseApk") {
    group = "build"
    description = "Builds the standard officially signed KEMI release APK."
    dependsOn(validateKemiReleaseSigning, "assembleKemiRelease")

    from(layout.buildDirectory.dir("outputs/apk/kemi/release")) {
        include("*-release.apk")
        rename(".*\\.apk", "KEMI-Mail-v$kemiVersionName.apk")
    }
    into(layout.buildDirectory.dir("outputs/kemi/release"))
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 24
}
