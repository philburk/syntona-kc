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
    val processSkiko = tasks.named("processSkikoRuntimeForKWasm")
    dependsOn(processSkiko)
    (this as? org.jetbrains.kotlin.gradle.targets.js.ir.DefaultIncrementalSyncTask)?.from?.from(processSkiko)
}

tasks.named<Delete>("clean") {
    delete(rootProject.layout.buildDirectory.dir("wasm/packages/SyntonaKC-webApp"))
}