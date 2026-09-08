import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties
plugins { kotlin("jvm"); id("org.jetbrains.compose"); id("org.jetbrains.kotlin.plugin.compose") }
val releaseProperties = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val releaseVersion = releaseProperties.getProperty("versionName")
tasks.processResources { from(rootProject.file("version.properties")) }
java { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }
dependencies {
    implementation(project(":designsystem"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation(if (project.hasProperty("windowsTarget")) compose.desktop.windows_x64 else compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("io.insert-koin:koin-core:4.2.1")
    implementation("org.eclipse.angus:jakarta.mail:2.0.5")
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    implementation("org.jsoup:jsoup:1.23.2")
    testImplementation(kotlin("test"))
    testImplementation("com.icegreen:greenmail:2.1.13")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.28.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
tasks.test { useJUnit(); systemProperty("java.awt.headless", "true") }
compose.desktop {
    application {
        mainClass = "com.kemi.windows.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "KEMI邮箱"
            packageVersion = releaseVersion
            outputBaseDir.set(project.layout.buildDirectory.dir("compose/binaries/$releaseVersion"))
            description = "KEMI邮箱"
            vendor = "KEMI"
            licenseFile.set(rootProject.file("../LICENSE"))
            modules("java.naming", "java.security.jgss", "java.sql", "jdk.crypto.ec", "jdk.unsupported", "jdk.charsets")
            windows { upgradeUuid = "a61e8dc4-bfa7-3458-89f8-669238465324"; iconFile.set(project.file("resources/kemi-mail.ico")); menuGroup = "KEMI邮箱"; shortcut = true; dirChooser = true; perUserInstall = true }
        }
    }
}

tasks.register<JavaExec>("renderPreview") {
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.kemi.windows.MainKt")
    args("--render-preview", layout.buildDirectory.dir("preview").get().asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
