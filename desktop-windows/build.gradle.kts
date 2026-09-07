plugins {
    kotlin("jvm") version "2.3.21" apply false
    id("org.jetbrains.compose") version "1.10.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
subprojects {
    dependencyLocking {
        val target = if (project.hasProperty("windowsTarget") || System.getProperty("os.name").startsWith("Windows")) "windows" else "macos"
        lockFile.set(file("gradle-$target.lockfile"))
        lockAllConfigurations()
    }
}
