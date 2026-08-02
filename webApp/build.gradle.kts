import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(libs.compose.ui)
        }
    }
}

val copyAudioBridgeJsFiles = tasks.register<Copy>("copyAudioBridgeJsFiles") {
    val wasmJsRuntime = configurations.named("wasmJsRuntimeClasspath")
    from(wasmJsRuntime.map { configuration ->
        configuration.files
            .filter { it.isFile && it.name.startsWith("audio-bridge") }
            .map { zipTree(it) }
    }) {
        include("kcab-*.js")
    }
    into(layout.buildDirectory.dir("processedResources/wasmJs/main"))
}

tasks.named("wasmJsProcessResources") {
    dependsOn(copyAudioBridgeJsFiles)
}

tasks.matching {
    it.name == "wasmJsProductionExecutableCompileSync" || it.name == "wasmJsDevelopmentExecutableCompileSync"
}.configureEach {
    val buildDir = layout.buildDirectory.get().asFile
    doLast {
        val destDir = File(buildDir, "wasm/packages/SyntonaKC-webApp/kotlin")
        val skikoSrcDir = File(buildDir, "wasm/packages_imported/skiko-js-wasm-runtime")
        if (skikoSrcDir.exists()) {
            skikoSrcDir.walkTopDown().filter { it.isFile && (it.name.endsWith(".mjs") || it.name.endsWith(".wasm")) }.forEach { file ->
                file.copyTo(File(destDir, file.name), overwrite = true)
            }
        }
    }
}