# Syntona

Syntona is an interactive modular synthesizer application built with Kotlin Multiplatform and Compose Multiplatform, targeting Desktop (JVM), Web (WASM), and Android.

Syntona is available online as a web app at: [softsynth.com/syntona](https://softsynth.com/syntona)

## Prerequisites

* **JDK 17** or higher
* Android SDK (only needed if building `:androidApp`)

## Project Structure

* `/shared` — Shared multiplatform code (UI, dataflow graphs, audio graph logic):
  * `commonMain` — Multiplatform logic shared across all targets
  * `audioMain` — Audio streaming and bridge logic shared across desktop, web, and android
  * `jvmMain`, `wasmJsMain`, `androidMain` — Platform-specific implementations
* `/desktopApp` — Desktop JVM entry point and macOS/Windows/Linux packaging
* `/webApp` — WebAssembly (Wasm) browser application
* `/androidApp` — Android application wrapper

## Running the Apps

### Desktop
* Run desktop app:
  ```bash
  ./gradlew :desktopApp:run
  ```
* Run with Compose Hot Reload:
  ```bash
  ./gradlew :desktopApp:hotRun
  ```

### Web (Wasm)
* Run local development server:
  ```bash
  ./gradlew :webApp:wasmJsBrowserDevelopmentRun
  ```
* Build production web distribution:
  ```bash
  ./gradlew :webApp:wasmJsBrowserDistribution
  ```

### Android
* Build debug APK:
  ```bash
  ./gradlew :androidApp:assembleDebug
  ```

## Running Tests

* Run main test suite (JVM):
  ```bash
  ./gradlew :shared:jvmTest
  ```

## Community & Feedback

* **Website:** [softsynth.com/syntona](https://softsynth.com/syntona)
* **Discord:** Join the discussion on [Discord](https://discord.gg/Q5tAD5bqw)
* **Issues:** Report bugs or submit feature requests on [GitHub Issues](https://github.com/philburk/syntona-kc/issues)

---

## License and Source Code

* **Application License:** Syntona is free software released under the terms of the [GNU Affero General Public License v3.0 (AGPLv3)](./LICENSE).
* **Source Code:** [https://github.com/philburk/syntona-kc](https://github.com/philburk/syntona-kc)
* **Libraries:** Core audio engine libraries ([KSyn](https://github.com/philburk/ksyn) and JSyn) are licensed separately under the Apache License, Version 2.0.
