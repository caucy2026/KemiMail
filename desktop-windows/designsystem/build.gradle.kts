plugins { kotlin("jvm"); id("org.jetbrains.compose"); id("org.jetbrains.kotlin.plugin.compose") }
java { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
    sourceSets.main {
        kotlin.srcDir("../../core/ui/compose/theme2/src/commonMain/kotlin")
        kotlin.include("com/kemi/**", "net/thunderbird/core/ui/compose/theme2/ThemeColorScheme.kt", "net/thunderbird/core/ui/compose/theme2/k9mail/ThemeColors.kt")
    }
}
dependencies { implementation(if (project.hasProperty("windowsTarget")) compose.desktop.windows_x64 else compose.desktop.currentOs); implementation("org.jetbrains.compose.material3:material3:1.9.0") }
