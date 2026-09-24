import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

val syntonaVersion = run {
    val versionFile = project(":shared").file("src/commonMain/kotlin/com/syntona/SyntonaVersion.kt")
    val text = versionFile.readText()
    val major = Regex("""val major\s*=\s*(\d+)""").find(text)?.groupValues?.get(1) ?: "0"
    val minor = Regex("""val minor\s*=\s*(\d+)""").find(text)?.groupValues?.get(1) ?: "0"
    val revision = Regex("""val revision\s*=\s*(\d+)""").find(text)?.groupValues?.get(1) ?: "0"
    "$major.$minor.$revision"
}

compose.desktop {
    application {
        mainClass = "com.syntona.MainKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Syntona"
            packageVersion = if (syntonaVersion.startsWith("0.")) "1.0.0" else syntonaVersion

            macOS {
                bundleID = "com.syntona.app"
                iconFile.set(project.file("syntona.icns"))
                entitlementsFile.set(project.file("entitlements.plist"))
                signing {
                    sign.set(true)
                    identity.set("Developer ID Application: Philip Burk (9D2MR4L34E)")
                    keychain.set("${System.getProperty("user.home")}/Library/Keychains/login.keychain-db")
                }
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleShortVersionString</key>
                        <string>$syntonaVersion</string>
                        <key>NSMicrophoneUsageDescription</key>
                        <string>This app requires access to the microphone for audio recording.</string>
                        <key>NSHumanReadableCopyright</key>
                        <string>Copyright (C) 2026 Phil Burk</string>
                    """
                }
            }
        }
    }
}

abstract class FinalizeDmgTask : DefaultTask() {
    @get:Internal
    abstract val dmgDir: DirectoryProperty

    @get:Input
    abstract val version: Property<String>

    @TaskAction
    fun finalizeDmg() {
        val dir = dmgDir.get().asFile
        val oldDmg = File(dir, "Syntona-1.0.0.dmg")
        val targetDmg = File(dir, "Syntona-${version.get()}.dmg")
        if (oldDmg.exists() && oldDmg != targetDmg) {
            targetDmg.delete()
            oldDmg.renameTo(targetDmg)
        }
        if (targetDmg.exists()) {
            ProcessBuilder(
                "codesign", "--force", "--sign",
                "Developer ID Application: Philip Burk (9D2MR4L34E)",
                targetDmg.absolutePath
            ).inheritIO().start().waitFor()
            println("Created and signed DMG: ${targetDmg.absolutePath}")
        }
    }
}

val finalizeDmg = tasks.register<FinalizeDmgTask>("finalizeDmg") {
    dmgDir.set(layout.buildDirectory.dir("compose/binaries/main/dmg"))
    version.set(syntonaVersion)
}

tasks.matching { it.name == "packageDmg" }.configureEach {
    finalizedBy(finalizeDmg)
}