package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.AddTimeModule
import com.syntona.dataflow.modules.KnobModule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A jack's min, max, kickable flag and value all have to survive save and load.
 *
 * These fields are easy to drop by "optimizing" the JSON, because a field missing on load means
 * "keep the module's own default" - and modules set their own defaults, which are not the ones
 * [com.syntona.dataflow.model.InputJack] starts with.
 */
class JackRoundTripTest {

    private fun roundTrip(module: Module): Module {
        val before = GraphState()
        before.addModule(module, Offset(10f, 20f))
        val json = GraphSerializer.serialize(before, before.modules.toList(), 1f, 0f, 0f, 800f, 600f)
        val after = GraphState()
        GraphSerializer.deserialize(after, json)
        return after.modules.first()
    }

    @Test
    fun jackMaxSurvivesEvenWhenItMatchesTheBaseClassDefault() {
        // A knob's own default max is 1.0, so 100.0 is a real edit, not a default to be skipped.
        val knob = KnobModule()
        knob.inputs.first { it.name == "in" }.max = 100.0
        val back = roundTrip(knob)
        assertEquals(100.0, back.inputs.first { it.name == "in" }.max)
    }

    @Test
    fun jackMinSurvivesEvenWhenItMatchesTheBaseClassDefault() {
        val addTime = AddTimeModule()
        val delay = addTime.inputs.first { it.name != "in" }
        delay.min = 0.0
        delay.max = 100.0
        val back = roundTrip(addTime)
        val backDelay = back.inputs.first { it.name == delay.name }
        assertEquals(0.0, backDelay.min)
        assertEquals(100.0, backDelay.max)
    }

    @Test
    fun turningOffKickableSurvives() {
        // addTime's "in" is kickable by default, so false is the value that needs writing down.
        val addTime = AddTimeModule()
        addTime.inputs.first { it.name == "in" }.isKickable = false
        val back = roundTrip(addTime)
        assertEquals(false, back.inputs.first { it.name == "in" }.isKickable)
    }

    @Test
    fun jackValueKeepsItsPrecision() {
        val knob = KnobModule()
        knob.inputs.first { it.name == "in" }.setValue(0.123456)
        val back = roundTrip(knob)
        assertEquals(0.123456, back.inputs.first { it.name == "in" }.getValue())
    }

    @Test
    fun anUntouchedKickableJackIsNotGivenAValue() {
        // Writing "v" for a kickable jack makes loading the patch fire that module.
        val addTime = AddTimeModule()
        val state = GraphState()
        state.addModule(addTime, Offset.Zero)
        val json = GraphSerializer.serialize(state, state.modules.toList(), 1f, 0f, 0f, 800f, 600f)
        val inJack = GraphSerializer.minifyJson(json).substringAfter("\"in\":{").substringBefore("}")
        assertEquals(false, inJack.contains("\"v\""), "unexpected value in: $inJack")
    }

    @Test
    fun anUntouchedInputModuleIsNotGivenAValue() {
        // Writing "v" for an untouched InputModule (such as event inputs "on" and "off")
        // marks hasValue = true on load and can inadvertently trigger modules on connection.
        val inMod = com.syntona.dataflow.modules.InputModule()
        inMod.name = "on"
        val state = GraphState()
        state.addModule(inMod, Offset.Zero)
        val json = GraphSerializer.serialize(state, state.modules.toList(), 1f, 0f, 0f, 800f, 600f)
        val onJack = GraphSerializer.minifyJson(json).substringAfter("\"on\":{").substringBefore("}")
        assertEquals(false, onJack.contains("\"v\""), "unexpected value in: $onJack")
    }

    @Test
    fun inputModuleJackPropertiesSurvivePatchSerialization() {
        val patch = com.syntona.dataflow.modules.PatchModule("myPatch")
        val inMod = com.syntona.dataflow.modules.InputModule()
        inMod.name = "mcRatio"
        patch.graphState.addModule(inMod, Offset(10f, 20f))

        val patchJack = inMod.patchJack
        kotlin.test.assertNotNull(patchJack)
        patchJack.setValue(2.0)
        patchJack.min = 0.0
        patchJack.max = 10.0

        val serialized = GraphSerializer.serializePatch(patch)
        val loadedPatch = com.syntona.dataflow.modules.PatchModule("loaded")
        GraphSerializer.deserialize(loadedPatch.graphState, serialized)

        val loadedInMod = loadedPatch.graphState.modules.filterIsInstance<com.syntona.dataflow.modules.InputModule>().firstOrNull()
        kotlin.test.assertNotNull(loadedInMod)
        assertEquals("mcRatio", loadedInMod.name)
        val loadedJack = loadedInMod.patchJack
        kotlin.test.assertNotNull(loadedJack)
        assertEquals(2.0, loadedJack.getValue())
        assertEquals(0.0, loadedJack.min)
        assertEquals(10.0, loadedJack.max)
    }

    @Test
    fun audioInputModuleJackPropertiesSurvivePatchSerialization() {
        val patch = com.syntona.dataflow.modules.PatchModule("myPatch")
        val aInMod = com.syntona.dataflow.modules.AudioInputModule()
        aInMod.name = "audioMod"
        patch.graphState.addModule(aInMod, Offset(10f, 20f))

        val patchJack = aInMod.patchJack
        kotlin.test.assertNotNull(patchJack)
        patchJack.setValue(0.75)
        patchJack.min = 0.1
        patchJack.max = 0.9
        patchJack.isValueAdded = true

        val serialized = GraphSerializer.serializePatch(patch)
        val loadedPatch = com.syntona.dataflow.modules.PatchModule("loaded")
        GraphSerializer.deserialize(loadedPatch.graphState, serialized)

        val loadedAInMod = loadedPatch.graphState.modules.filterIsInstance<com.syntona.dataflow.modules.AudioInputModule>().firstOrNull()
        kotlin.test.assertNotNull(loadedAInMod)
        assertEquals("audioMod", loadedAInMod.name)
        val loadedJack = loadedAInMod.patchJack
        kotlin.test.assertNotNull(loadedJack)
        assertEquals(0.75, loadedJack.getValue(), 1e-4)
        assertEquals(0.1, loadedJack.min, 1e-4)
        assertEquals(0.9, loadedJack.max, 1e-4)
        assertEquals(true, loadedJack.isValueAdded)
    }

    @Test
    fun testMinGreaterThanMaxHandling() {
        val add = com.syntona.dataflow.modules.AddModule()
        val jackA = add.inputA
        jackA.min = 300.0
        jackA.max = 100.0
        jackA.setValue(200.0)

        // getClippedDoubleValue and getClippedIntValue should not throw IllegalArgumentException
        val clipped = jackA.getClippedDoubleValue()
        assertEquals(200.0, clipped)
        assertEquals(200, jackA.getClippedIntValue())
    }

    @Test
    fun testSerializationIncludesVersionAndVersionCode() {
        val state = GraphState()
        state.addModule(com.syntona.dataflow.modules.ClockModule(), Offset.Zero)

        val json = GraphSerializer.serialize(state, state.modules.toList())
        val parsed = JsonParser(json).parse() as JsonValue.Obj

        val version = (parsed.map["version"] as? JsonValue.Str)?.value
        val versionCode = (parsed.map["versionCode"] as? JsonValue.Num)?.value?.toInt()

        assertEquals(com.syntona.SyntonaVersion.toString(), version)
        assertEquals(com.syntona.SyntonaVersion.code, versionCode)

        val patch = com.syntona.dataflow.modules.PatchModule("testPatch")
        patch.graphState.addModule(com.syntona.dataflow.modules.CounterModule(), Offset.Zero)
        val patchJson = GraphSerializer.serializePatch(patch)
        val parsedPatch = JsonParser(patchJson).parse() as JsonValue.Obj

        assertEquals(com.syntona.SyntonaVersion.toString(), (parsedPatch.map["version"] as? JsonValue.Str)?.value)
        assertEquals(com.syntona.SyntonaVersion.code, (parsedPatch.map["versionCode"] as? JsonValue.Num)?.value?.toInt())
    }

    @Test
    fun jackIntegerSurvivesRoundTrip() {
        val knob = KnobModule()
        val jack = knob.inputs.first { it.name == "in" }
        assertEquals(false, jack.isInteger)
        jack.isInteger = true

        val back = roundTrip(knob)
        assertEquals(true, back.inputs.first { it.name == "in" }.isInteger)
    }

    @Test
    fun convertedModulesHaveIntegerByDefaultAndSurviveRoundTrip() {
        val keyboard = com.syntona.dataflow.modules.Keyboard25Module()
        assertEquals(true, keyboard.octaveIn.isInteger)

        val back = roundTrip(keyboard) as com.syntona.dataflow.modules.Keyboard25Module
        assertEquals(true, back.octaveIn.isInteger)
    }

    @Test
    fun omittedIntegerKeepsModuleDefault() {
        // When JSON has no "integer" key, deserializer must not overwrite the module's default.
        val state = GraphState()
        // Craft JSON without "integer" field for keyboard25 and knob
        val jsonWithoutInteger = """
            {
              "version": "${com.syntona.SyntonaVersion}",
              "modules": [
                {
                  "type": "music.input.keyboard25",
                  "name": "keyboard25",
                  "jacks": {
                    "octave": { "v": 4.0, "min": 2.0, "max": 8.0, "kickable": false }
                  }
                },
                {
                  "type": "core.input.knob",
                  "name": "knob",
                  "jacks": {
                    "in": { "v": 0.5, "min": 0.0, "max": 1.0, "kickable": false }
                  }
                }
              ],
              "connections": []
            }
        """.trimIndent()

        GraphSerializer.deserialize(state, jsonWithoutInteger)
        val loadedKeyboard = state.modules.first { it is com.syntona.dataflow.modules.Keyboard25Module } as com.syntona.dataflow.modules.Keyboard25Module
        val loadedKnob = state.modules.first { it is KnobModule } as KnobModule

        assertEquals(true, loadedKeyboard.octaveIn.isInteger, "keyboard25.octave should remain true by default")
        assertEquals(false, loadedKnob.inputs.first { it.name == "in" }.isInteger, "knob.in should remain false by default")
    }

    @Test
    fun jackExponentialSurvivesRoundTrip() {
        val knob = KnobModule()
        val jack = knob.inputs.first { it.name == "in" }
        assertEquals(false, jack.isExponential)
        jack.isExponential = true

        val back = roundTrip(knob)
        assertEquals(true, back.inputs.first { it.name == "in" }.isExponential)
    }

    @Test
    fun omittedExponentialKeepsModuleDefault() {
        val state = GraphState()
        val jsonWithoutExp = """
            {
              "version": "${com.syntona.SyntonaVersion}",
              "modules": [
                {
                  "type": "core.input.knob",
                  "name": "knob",
                  "jacks": {
                    "in": { "v": 0.5, "min": 0.0, "max": 1.0, "kickable": false }
                  }
                }
              ],
              "connections": []
            }
        """.trimIndent()

        GraphSerializer.deserialize(state, jsonWithoutExp)
        val loadedKnob = state.modules.first { it is KnobModule } as KnobModule
        assertEquals(false, loadedKnob.inputs.first { it.name == "in" }.isExponential)
    }

    @Test
    fun autoDisableVoiceSurvivesSaveAndLoad() {
        val dahdsr = com.syntona.dataflow.modules.UnitGeneratorModule("envDAHDSR", com.softsynth.ksyn.unitgen.EnvelopeDAHDSR())
        dahdsr.autoDisableVoice = com.syntona.dataflow.model.AutoDisableVoiceMode.YES
        val backDahdsr = roundTrip(dahdsr) as com.syntona.dataflow.modules.UnitGeneratorModule
        assertEquals(com.syntona.dataflow.model.AutoDisableVoiceMode.YES, backDahdsr.autoDisableVoice)

        val envSeg = com.syntona.dataflow.modules.EnvSegModule()
        envSeg.autoDisableVoice = com.syntona.dataflow.model.AutoDisableVoiceMode.NO
        val backEnvSeg = roundTrip(envSeg) as com.syntona.dataflow.modules.EnvSegModule
        assertEquals(com.syntona.dataflow.model.AutoDisableVoiceMode.NO, backEnvSeg.autoDisableVoice)
    }

    @Test
    fun audioInputJackValueAddedSurvivesSaveAndLoad() {
        val filter = com.syntona.dataflow.modules.UnitGeneratorModule(
            "lowPass",
            com.softsynth.ksyn.unitgen.FilterLowPass()
        )
        val freqJack = filter.inputs.first { it.name == "frequency" } as com.syntona.dataflow.model.AudioInputJack
        assertEquals(false, freqJack.isValueAdded)
        freqJack.isValueAdded = true
        val backFilter = roundTrip(filter) as com.syntona.dataflow.modules.UnitGeneratorModule
        val backFreqJack = backFilter.inputs.first { it.name == "frequency" } as com.syntona.dataflow.model.AudioInputJack
        assertEquals(true, backFreqJack.isValueAdded)
    }
}

