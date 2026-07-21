import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidLibrary {
       namespace = "com.syntona.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()

       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }

    sourceSets {
        val audioMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(audioMain)
        jvmMain.get().dependsOn(audioMain)
        wasmJsMain.get().dependsOn(audioMain)

        audioMain.dependencies {
            implementation("com.softsynth:audio-bridge:0.4.0")
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation("com.softsynth.ksyn:ksyn:1.2.0")
            implementation("com.softsynth.ksyn.compose:ksyn-compose:1.2.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

val generateFilesIndexTask = tasks.register("generateFilesIndex") {
    val resourcesDir = file("src/commonMain/composeResources/files")
    val indexFile = file("src/commonMain/composeResources/files/files_index.txt")

    inputs.dir(resourcesDir)
    outputs.file(indexFile)

    doLast {
        if (resourcesDir.exists()) {
            val allFiles = mutableListOf<File>()
            fun addFilesRecursively(dir: File) {
                dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                    if (file.name == ".DS_Store" || file.name.startsWith(".")) {
                        return@forEach
                    }
                    if (file.isFile) {
                        if (file != indexFile) {
                            allFiles.add(file)
                        }
                    } else if (file.isDirectory) {
                        addFilesRecursively(file)
                    }
                }
            }
            addFilesRecursively(resourcesDir)
            val relativePaths = allFiles.map { file ->
                file.relativeTo(resourcesDir).path.replace('\\', '/')
            }.filter { !it.startsWith(".") && it != "files_index.txt" }.sorted()
            indexFile.writeText(relativePaths.joinToString("\n"))
        } else {
            indexFile.writeText("")
        }
    }
}

tasks.matching {
    it.name.startsWith("generateComposeResClass") ||
    it.name.startsWith("copyNonXmlValueResources") ||
    it.name.startsWith("prepareComposeResources")
}.configureEach {
    dependsOn(generateFilesIndexTask)
}
