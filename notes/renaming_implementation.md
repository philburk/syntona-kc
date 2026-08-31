# Renaming Syntona Modules and Jacks

This plan implements the major renaming of Syntona modules and jacks as specified in [`notes/renaming.md`](file:///Users/phil/Work/syntona-kc/notes/renaming.md), while maintaining backwards compatibility for existing JSON patch files (bundled examples, tutorials, help patches, and user saves).

Source compatibility is not required; classes and tests will be renamed directly to match the new names.

## Key Changes & Clarifications

> [!NOTE]
> - `input~` and `output~` (formerly `aInput` and `aOutput`) are **audio** modules in the `ksyn` bundle that create audio passthrough jacks on a patch.
> - Control-rate patch jacks are handled by `input` and `output` in `core.patch`.
> - All jacks named `"input*"` will become `"in*"` (e.g. `"input"` -> `"in"`, `"inputA"` -> `"inA"`, `"input0"` -> `"in0"`), and `"output*"` will become `"out*"` (e.g. `"output"` -> `"out"`, `"output0"` -> `"out0"`).
> - Backward compatibility mappings in `GraphSerializer` and `ModuleRegistry` ensure that old JSON files referring to legacy module names and legacy jack names continue to deserialize seamlessly without error.

---

## Proposed Changes

### 1. KSyn Unit Port Coercion

#### [MODIFY] [KSynModules.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/KSynModules.kt)
- Update `UnitGeneratorModule` constructor port-mapping:
    - Normalize port names:
        - If name starts with `"input"` (case-insensitive): coerce to `"in"` + suffix (e.g. `"Input"` -> `"in"`, `"InputA"` -> `"inA"`).
        - If name starts with `"output"` (case-insensitive): coerce to `"out"` + suffix (e.g. `"Output"` -> `"out"`, `"OutputA"` -> `"outA"`).
        - Otherwise: lowercase first character.
    - Use coerced port names for all audio jacks created from KSyn units.

---

### 2. Syntona Module Class Definitions & Jack Names

#### [MODIFY] [RandomModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/RandomModule.kt)
- Change jack `"value"` to `"out"`.

#### [MODIFY] [BasicModules.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/BasicModules.kt)
- `CounterModule`: change input jack `"inc"` to `"kick"` (`val increment = addInput("kick", isKickable = true)`).
- `ClockDividerModule`: change type from `"ckDiv"` to `"clockDiv"`, and change `"N"` input jack to `"divisor"` (as `IntegerInputJack`).
- Rename `output` jacks to `"out"` where applicable.

#### [MODIFY] [StepperModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/StepperModule.kt)
- Change `loop` jack from `"loop"` to `"looping"`.

#### [MODIFY] [SamplePlayerModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/SamplePlayerModule.kt)
- Rename `rate` jack to `"speed"`.
- Rename `output` jack to `"out"`.

#### [MODIFY] [SampleReaderModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/SampleReaderModule.kt)
- Rename `rate` jack to `"speed"`.
- Rename `loop` jack to `"looping"` and change to `addIntegerInput("looping")`.
- Rename `output` jack to `"out"`.

#### [MODIFY] [ClockModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/ClockModule.kt)
- Rename `"rate(hz)"` jack to `"rateHz"`.
- Rename `output` jack to `"out"`.

#### [MODIFY] [AddTimeModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/AddTimeModule.kt)
- Rename `"delay(ms)"` jack to `"delayMs"`.
- Rename `input` jack to `"in"` and `output` to `"out"`.

#### [MODIFY] [OutputModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/OutputModule.kt)
- Rename input jack from `"input"` to `"in"`.

#### [MODIFY] [AudioInputModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/AudioInputModule.kt)
- Change module type from `"aInput"` to `"input~"`.
- Rename jack `"output"` to `"out"`.

#### [MODIFY] [AudioOutputModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/AudioOutputModule.kt)
- Change module type from `"aOutput"` to `"output~"`.
- Rename jack `"input"` to `"in"`.

#### [MODIFY] [LoadKickModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/LoadKickModule.kt)
- Rename class `LoadKickModule` -> `OnLoadModule` (type `"onLoad"`).
- Rename references across codebase (`GraphState.kt`, `GraphEditor.kt`, tests).

#### [MODIFY] [ArmModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/ArmModule.kt)
- Rename class `ArmModule` -> `OneShotModule` (type `"oneShot"`).
- Rename jack `"output"` to `"out"`.

#### [MODIFY] [LogicModules.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/LogicModules.kt)
- `GateModule` -> `PassModule` (type `"pass"`).
- `Choose4Module` / `8` / `16` -> `Select4Module`, `Select8Module`, `Select16Module` (types `"select4"`, `"select8"`, `"select16"`).
- Rename jacks `"input*"` -> `"in*"`, `"output*"` -> `"out*"`.

#### [MODIFY] [ArithmeticModules.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/ArithmeticModules.kt)
- `PitchToFrequencyModule`: change type from `"p2f"` to `"pitchToHz"`.
- Rename jacks: `input` -> `in`, `inputA` -> `inA`, `inputB` -> `inB`, `output` -> `out`.

#### [MODIFY] [RecSeqModule.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/modules/RecSeqModule.kt)
- Rename class `RecSeqModule` -> `RecursiveSeqModule` (type `"recursiveSeq"`).
- Rename jack `"output"` to `"out"`.

---

### 3. Module Registry Updates & Legacy Type Aliases

#### [MODIFY] [ModuleRegistry.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/registry/ModuleRegistry.kt)
- Register updated audio module types with `~`:
    - `"+~"`, `"-~"`, `"*~"`, `"*+~"`, `"/~"`, `">~"`, `"min~"`, `"max~"`, `"range~"`, `"pitchToHz~"`, `"power2~"`.
    - `"delay~"` (`ksyn.effects.delay~`), `"select~"` (`ksyn.control.select~`).
    - `"input~"` (`ksyn.input~`), `"output~"` (`ksyn.output~`).
- Register updated control rate module types:
    - `"pitchToHz"`, `"clockDiv"`, `"latchZeroX"`, `"envDAHDSR"`, `"onLoad"`, `"recursiveSeq"`, `"oneShot"`, `"pass"`, `"select4"`, `"select8"`, `"select16"`.
- In `ModuleRegistry.findEntry(typeName: String)`:
    - Add comprehensive legacy type aliasing for deserialization:
        - `"a+"` -> `"+~"`, `"a-"` -> `"-~"`, `"a*"` -> `"*~"`, `"a*+"` -> `"*+~"`, `"a/"` -> `"/~"`, `"a>"` -> `">~"`, `"aMin"` -> `"min~"`, `"aMax"` -> `"max~"`, `"aRanger"` -> `"range~"`, `"ap2f"` -> `"pitchToHz~"`, `"aPower2"` -> `"power2~"`.
        - `"ksyn.effects.delay"` -> `"ksyn.effects.delay~"`, `"delay"` -> `"delay~"`.
        - `"ksyn.control.select"` -> `"ksyn.control.select~"`, `"select"` -> `"select~"`.
        - `"p2f"` -> `"pitchToHz"`, `"DAHDSR"` -> `"envDAHDSR"`, `"ckDiv"` -> `"clockDiv"`, `"latchZC"` -> `"latchZeroX"`.
        - `"aInput"` -> `"input~"`, `"aOutput"` -> `"output~"`.
        - `"loadKick"` -> `"onLoad"`, `"recSeq"` -> `"recursiveSeq"`, `"arm"` -> `"oneShot"`, `"gate"` -> `"pass"`.
        - `"choose4"` -> `"select4"`, `"choose8"` -> `"select8"`, `"choose16"` -> `"select16"`.
- In `ModuleRegistry.ksynFactories`:
    - Register under both new names (`delay~`, `select~`, `+~`, etc.) and old names for polyphonic voice instantiation.

---

### 4. Graph Deserializer Backward Compatibility

#### [MODIFY] [GraphSerializer.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/editor/GraphSerializer.kt)
- Resolve module types using `ModuleRegistry.findEntry(rawTypeName)`.
- Add legacy jack name resolution `resolveJackName(module: Module, serializedJackName: String): Jack?`:
    - Step 1: Exact name match.
    - Step 2: Fallback transforms:
        - Map `"input*"` -> `"in*"`.
        - Map `"output*"` -> `"out*"`.
        - Specific mappings: `"value"` -> `"out"`, `"inc"` -> `"kick"`, `"rate"` -> `"speed"`, `"loop"` -> `"looping"`, `"rate(hz)"` -> `"rateHz"`, `"delay(ms)"` -> `"delayMs"`.
    - Step 3: Case-insensitive fallback match.
- Use `resolveJackName` for deserializing connections (`fromJack`, `toJack`) and module jack state dictionaries.

---

### 5. Tests & Verification

#### [MODIFY] Tests in `shared/src/commonTest` and `jvmTest`
- Update test files for renamed classes and jack names:
    - `LoadKickModuleTest.kt` -> `OnLoadModuleTest.kt`
    - `ArithmeticMathTest.kt`
    - `ModuleRegistryTest.kt`
    - `BundledFilesValidationTest.kt`


---

### 3. Module Registry Updates & Legacy Type Aliases

#### [MODIFY] [ModuleRegistry.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/registry/ModuleRegistry.kt)
- Register updated module types and short names:
    - Audio math / ambiguity tildes:
        - `"+~"` (from `"a+"`)
        - `"-~"` (from `"a-"`)
        - `"*~"` (from `"a*"`)
        - `"*+~"` (from `"a*+"`)
        - `"/~"` (from `"a/"`)
        - `">~"` (from `"a>"`)
        - `"min~"` (from `"aMin"`)
        - `"max~"` (from `"aMax"`)
        - `"range~"` (from `"aRanger"`)
        - `"pitchToHz~"` (from `"ap2f"`)
        - `"power2~"` (from `"aPower2"`)
        - `"ksyn.effects.delay~"` / `"delay~"` (from `"ksyn.effects.delay"`)
        - `"ksyn.control.select~"` / `"select~"` (from `"ksyn.control.select"`)
        - `"latchZeroX"` (from `"latchZC"`)
        - `"envDAHDSR"` (from `"DAHDSR"`)
    - Control-rate modules:
        - `"pitchToHz"`, `"clockDiv"`, `"input~"`, `"output~"`, `"onLoad"`, `"recursiveSeq"`, `"oneShot"`, `"pass"`, `"select4"`, `"select8"`, `"select16"`.
- In `ModuleRegistry.findEntry(typeName: String)`:
    - Add mapping for legacy names to new names so that any lookup by old name resolves to the correct entry:
        - `"a+"` -> `"+~"`, `"a-"` -> `"-~"`, `"a*"` -> `"*~"`, `"a*+"` -> `"*+~"`, `"a/"` -> `"/~"`, `"a>"` -> `">~"`, `"aMin"` -> `"min~"`, `"aMax"` -> `"max~"`, `"aRanger"` -> `"range~"`, `"ap2f"` -> `"pitchToHz~"`, `"aPower2"` -> `"power2~"`, `"p2f"` -> `"pitchToHz"`, `"DAHDSR"` -> `"envDAHDSR"`, `"ckDiv"` -> `"clockDiv"`, `"latchZC"` -> `"latchZeroX"`, `"aInput"` -> `"input~"`, `"aOutput"` -> `"output~"`, `"loadKick"` -> `"onLoad"`, `"recSeq"` -> `"recursiveSeq"`, `"arm"` -> `"oneShot"`, `"gate"` -> `"pass"`, `"choose4"` -> `"select4"`, `"choose8"` -> `"select8"`, `"choose16"` -> `"select16"`.
        - `"ksyn.effects.delay"` -> `"ksyn.effects.delay~"`, `"ksyn.control.select"` -> `"ksyn.control.select~"`.
- In `ModuleRegistry.ksynFactories`:
    - Ensure entries are keyed by both the new types and legacy alias types so `PolyPatchModule` voice instantiation works with both.

---

### 4. Graph Deserializer Backward Compatibility

#### [MODIFY] [GraphSerializer.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonMain/kotlin/com/syntona/dataflow/editor/GraphSerializer.kt)
- Add legacy type mapping in `GraphSerializer.deserialize`:
    - Route all old module type strings through alias resolution before looking up creators.
- Add legacy jack name resolution helper `resolveJackName(module: Module, serializedJackName: String): Jack?`:
    - First attempt exact match: `module.findJack(serializedJackName)`.
    - Fallback 1: Case-insensitive match.
    - Fallback 2: Coercion rules:
        - `"input*"` -> `"in*"` (e.g. `"input"` -> `"in"`, `"inputA"` -> `"inA"`).
        - `"output*"` -> `"out*"` (e.g. `"output"` -> `"out"`, `"outputA"` -> `"outA"`).
        - `"value"` -> `"out"` (for `RandomModule`).
        - `"inc"` -> `"kick"` (for `CounterModule`).
        - `"rate"` -> `"speed"` (for `SamplePlayerModule` / `SampleReaderModule`).
        - `"loop"` -> `"looping"` (for `SampleReaderModule`).
        - `"rate(hz)"` -> `"rateHz"`.
        - `"delay(ms)"` -> `"delayMs"`.
- Use this resolver when deserializing jack values, overrides, and connections (`fromJack`, `toJack`).

---

### 5. Tests & Verification

#### [MODIFY] [ArithmeticMathTest.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonTest/kotlin/com/syntona/dataflow/modules/ArithmeticMathTest.kt)
- Update test cases referencing `"p2f"` or old jack names to use `"pitchToHz"`.

#### [MODIFY] [ModuleRegistryTest.kt](file:///Users/phil/Work/syntona-kc/shared/src/commonTest/kotlin/com/syntona/dataflow/registry/ModuleRegistryTest.kt)
- Verify new module names and alias resolution tests.

#### [MODIFY] [BundledFilesValidationTest.kt](file:///Users/phil/Work/syntona-kc/shared/src/jvmTest/kotlin/com/syntona/dataflow/editor/BundledFilesValidationTest.kt)
- Run `testAllBundledJsonPatchesDeserializeCleanly` to verify all bundled JSON patches in tutorials, examples, instruments, sounds, and help load without error.

---

## Verification Plan

### Automated Tests
Execute via `./gradlew --no-daemon jvmTest`:
- `com.syntona.dataflow.editor.BundledFilesValidationTest`
- `com.syntona.dataflow.modules.*`
- `com.syntona.dataflow.registry.ModuleRegistryTest`
