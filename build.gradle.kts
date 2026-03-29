// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Root build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library).apply(false) 
    alias(libs.plugins.kotlin.android) apply false
    id("com.diffplug.spotless") version "8.4.0"
}
//tasks.register<Delete>("clean") {
//    delete(rootProject.layout.buildDirectory)
//}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**/*.kt")
            ktfmt("0.62").googleStyle() // 使用 ktfmt 并指定 Google 风格
            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
        }
        kotlinGradle {
            target("**/*.gradle.kts")
            ktfmt("0.47").googleStyle()
        }
    }
}