# Running Unit Tests for Syntona

All commands should be executed from the `syntona-kc` project root (`cd ~/work/syntona-kc`).

## 1. Main Unit Test Suite (JVM)

Runs the comprehensive test suite in `:shared` (including all shared tests in `commonTest` and `jvmTest`):

```bash
./gradlew :shared:jvmTest
```

This runs all unit tests for modules, audio algorithms, serialization, dataflow graphs, and editor logic.

## 2. Running Specific Tests

To filter by test class:

```bash
./gradlew :shared:jvmTest --tests "com.syntona.dataflow.editor.FileDialogsJvmTest"
```

To filter by a specific test method:

```bash
./gradlew :shared:jvmTest --tests "com.syntona.dataflow.editor.FileDialogsJvmTest.testDocumentAndWavDirectoriesSavedSeparately"
```

You can also use wildcards:

```bash
./gradlew :shared:jvmTest --tests "*.RecorderTest"
```

## 3. Viewing Test Reports

Gradle generates an HTML report after test execution:

```bash
open shared/build/reports/tests/jvmTest/index.html
```

