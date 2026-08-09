package com.syntona.dataflow.modules

import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyNotesModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = KeyNotesModule()
        assertEquals("keyNotes", module.name)
        assertEquals("zsxdcvgbhnjm,", module.bottomOctaveKeys)
        assertEquals("q2w3er5t6y7u8io", module.topOctaveKeys)

        val output = module.outputs.find { it.name == "out" }
        assertNotNull(output, "KeyNotesModule should have an output named 'out'")

        val amplitude = module.inputs.find { it.name == "amplitude" }
        assertNotNull(amplitude, "KeyNotesModule should have an input named 'amplitude'")
        assertEquals(0.0, amplitude.min)
        assertEquals(1.0, amplitude.max)
        assertEquals(0.8, amplitude.getDoubleValue())

        val octave = module.inputs.find { it.name == "octave" }
        assertNotNull(octave, "KeyNotesModule should have an input named 'octave'")
        assertEquals(2.0, octave.min)
        assertEquals(8.0, octave.max)
        assertEquals(5.0, octave.getDoubleValue())
    }

    @Test
    fun testModuleRegistryCreator() {
        val creator = ModuleRegistry.findCreator("keyNotes")
        assertNotNull(creator, "KeyNotesModule creator should be registered in ModuleRegistry under 'keyNotes'")
        val module = creator()
        assertTrue(module is KeyNotesModule, "Created module should be an instance of KeyNotesModule")
    }

    @Test
    fun testKeystrokeToVoiceMessageConversion() {
        val graphState = GraphState()
        val keyNotesModule = KeyNotesModule()
        graphState.addModule(keyNotesModule, androidx.compose.ui.geometry.Offset.Zero)

        var lastMessage: Message? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                lastMessage = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyNotesModule.output, receiver.input)

        // Pitches count up from the default octave of 5, so the lowest key sounds at 60.

        // 1. Press 'z' (ASCII 122). Should match bottom octave index 0 (middle C, MIDI 60).
        val processedZ = graphState.handleKeyStroke(122, isDown = true)
        assertTrue(processedZ, "Keystroke 'z' should be processed")
        val zMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(zMsg)
        assertEquals(Message.VoiceOpcode.NOTE_ON, zMsg.opcode)
        assertEquals(60, zMsg.tag)
        assertEquals(60.0, zMsg.value)

        // 2. Release 'z' (ASCII 122).
        val releasedZ = graphState.handleKeyStroke(122, isDown = false)
        assertTrue(releasedZ, "Keystroke release 'z' should be processed")
        val zReleaseMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(zReleaseMsg)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, zReleaseMsg.opcode)
        assertEquals(60, zReleaseMsg.tag)
        assertEquals(60.0, zReleaseMsg.value)

        // 3. Press 's' (ASCII 115). Should match bottom octave index 1 (MIDI 61).
        val processedS = graphState.handleKeyStroke(115, isDown = true)
        assertTrue(processedS, "Keystroke 's' should be processed")
        val sMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(sMsg)
        assertEquals(Message.VoiceOpcode.NOTE_ON, sMsg.opcode)
        assertEquals(61, sMsg.tag)

        // 4. Press ',' (ASCII 44). Should match bottom octave index 12 (MIDI 72).
        val processedComma = graphState.handleKeyStroke(44, isDown = true)
        assertTrue(processedComma, "Keystroke ',' should be processed")
        val commaMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(commaMsg)
        assertEquals(72, commaMsg.tag)

        // 5. Press 'q' (ASCII 113). Should match top octave index 0 (MIDI 72).
        val processedQ = graphState.handleKeyStroke(113, isDown = true)
        assertTrue(processedQ, "Keystroke 'q' should be processed")
        val qMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(qMsg)
        assertEquals(72, qMsg.tag)

        // 6. Press '2' (ASCII 50). Should match top octave index 1 (MIDI 73).
        val processed2 = graphState.handleKeyStroke(50, isDown = true)
        assertTrue(processed2, "Keystroke '2' should be processed")
        val twoMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(twoMsg)
        assertEquals(73, twoMsg.tag)

        // 7. Press 'o' (ASCII 111). Should match top octave index 14 (MIDI 86).
        val processedO = graphState.handleKeyStroke(111, isDown = true)
        assertTrue(processedO, "Keystroke 'o' should be processed")
        val oMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(oMsg)
        assertEquals(86, oMsg.tag)
    }

    @Test
    fun testOctaveJackSetsPitch() {
        val graphState = GraphState()
        val keyNotesModule = KeyNotesModule()
        graphState.addModule(keyNotesModule, androidx.compose.ui.geometry.Offset.Zero)

        var lastMessage: Message? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                lastMessage = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyNotesModule.output, receiver.input)

        keyNotesModule.octaveIn.setValue(4.0)
        graphState.handleKeyStroke(122, isDown = true)
        assertEquals(48, (lastMessage as? Message.VoiceMessage)?.tag)
        graphState.handleKeyStroke(122, isDown = false)

        keyNotesModule.octaveIn.setValue(2.0)
        graphState.handleKeyStroke(122, isDown = true)
        assertEquals(24, (lastMessage as? Message.VoiceMessage)?.tag)
        graphState.handleKeyStroke(122, isDown = false)

        // Out of range values are clipped to the jack's 2 to 8 range rather than transposing
        // somewhere unplayable.
        keyNotesModule.octaveIn.setValue(99.0)
        graphState.handleKeyStroke(122, isDown = true)
        assertEquals(96, (lastMessage as? Message.VoiceMessage)?.tag)
    }

    @Test
    fun testNoteReleasedAtTheOctaveItWasStruck() {
        val graphState = GraphState()
        val keyNotesModule = KeyNotesModule()
        graphState.addModule(keyNotesModule, androidx.compose.ui.geometry.Offset.Zero)

        var lastMessage: Message? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                lastMessage = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyNotesModule.output, receiver.input)

        graphState.handleKeyStroke(122, isDown = true)
        assertEquals(60, (lastMessage as? Message.VoiceMessage)?.tag)

        // Moving the octave while the key is held must not strand the sounding note.
        keyNotesModule.octaveIn.setValue(6.0)
        graphState.handleKeyStroke(122, isDown = false)
        val release = lastMessage as? Message.VoiceMessage
        assertNotNull(release)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, release.opcode)
        assertEquals(60, release.tag)
    }

    @Test
    fun testAmplitudeJackSetsExtra() {
        val graphState = GraphState()
        val keyNotesModule = KeyNotesModule()
        graphState.addModule(keyNotesModule, androidx.compose.ui.geometry.Offset.Zero)

        var lastMessage: Message? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                lastMessage = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyNotesModule.output, receiver.input)

        keyNotesModule.amplitudeIn.setValue(0.75)
        graphState.handleKeyStroke(122, isDown = true)
        assertEquals(0.75, (lastMessage as? Message.VoiceMessage)?.extra)
        graphState.handleKeyStroke(122, isDown = false)

        // Above the jack's maximum the amplitude is clipped, not passed through.
        keyNotesModule.amplitudeIn.setValue(4.0)
        graphState.handleKeyStroke(122, isDown = true)
        assertEquals(1.0, (lastMessage as? Message.VoiceMessage)?.extra)
    }

    @Test
    fun testShiftKeyTransposition() {
        val graphState = GraphState()
        val keyNotesModule = KeyNotesModule()
        graphState.addModule(keyNotesModule, androidx.compose.ui.geometry.Offset.Zero)

        var lastMessage: Message? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                lastMessage = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyNotesModule.output, receiver.input)

        // 1. Press 'z' with Shift key down -> MIDI note 84 (60 + 24)
        val processedZShift = graphState.handleKeyStroke(122, isDown = true, isShiftPressed = true)
        assertTrue(processedZShift)
        val zMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(zMsg)
        assertEquals(84, zMsg.tag)

        // 2. Release 'z' with Shift key released -> NOTE_OFF still at MIDI note 84
        val releasedZNoShift = graphState.handleKeyStroke(122, isDown = false, isShiftPressed = false)
        assertTrue(releasedZNoShift)
        val zReleaseMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(zReleaseMsg)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, zReleaseMsg.opcode)
        assertEquals(84, zReleaseMsg.tag)

        // 3. Press 'x' with Shift key released -> MIDI note 62
        val processedXNoShift = graphState.handleKeyStroke(120, isDown = true, isShiftPressed = false)
        assertTrue(processedXNoShift)
        val xMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(xMsg)
        assertEquals(62, xMsg.tag)

        // 4. Release 'x' with Shift key down -> NOTE_OFF still at MIDI note 62
        val releasedXShift = graphState.handleKeyStroke(120, isDown = false, isShiftPressed = true)
        assertTrue(releasedXShift)
        val xReleaseMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(xReleaseMsg)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, xReleaseMsg.opcode)
        assertEquals(62, xReleaseMsg.tag)
    }

    @Test
    fun testSerializationAndDeserialization() {
        val graphState = GraphState()
        val module = KeyNotesModule()
        module.bottomOctaveKeys = "abc"
        module.topOctaveKeys = "xyz"
        module.octaveIn.setValue(4.0)
        module.amplitudeIn.setValue(0.6)
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        // Serialize
        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("\"bottomOctaveKeys\": \"abc\""), "Serialized string should contain bottomOctaveKeys")
        assertTrue(jsonStr.contains("\"topOctaveKeys\": \"xyz\""), "Serialized string should contain topOctaveKeys")

        // Deserialize
        val newGraphState = GraphState()
        GraphSerializer.deserialize(newGraphState, jsonStr)
        val deserializedModule = newGraphState.modules.find { it.name == "keyNotes" } as? KeyNotesModule
        assertNotNull(deserializedModule)
        assertEquals("abc", deserializedModule.bottomOctaveKeys)
        assertEquals("xyz", deserializedModule.topOctaveKeys)
        assertEquals(4.0, deserializedModule.octaveIn.getDoubleValue())
        assertEquals(0.6, deserializedModule.amplitudeIn.getDoubleValue())
    }

    @Test
    fun testBackwardCompatibilityDeserialization() {
        // Old JSON format using "mappingString"
        val oldJson = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "core.input.keyNotes",
                  "name": "keyNotes",
                  "x": 0.0,
                  "y": 0.0,
                  "jacks": {},
                  "mappingString": "zsxdcvgbhnjm,q2w3er5t6y7u8io",
                  "startingNote": 60
                }
              ],
              "connections": []
            }
        """.trimIndent()

        val graphState = GraphState()
        GraphSerializer.deserialize(graphState, oldJson)
        val module = graphState.modules.find { it.name == "keyNotes" } as? KeyNotesModule
        assertNotNull(module)
        assertEquals("zsxdcvgbhnjm,", module.bottomOctaveKeys)
        assertEquals("q2w3er5t6y7u8io", module.topOctaveKeys)
        // The old absolute startingNote becomes the equivalent octave, so the patch plays the
        // pitches it always did.
        assertEquals(5.0, module.octaveIn.getDoubleValue())
    }

    @Test
    fun testLegacyStartingNoteBecomesOctave() {
        fun loadWithStartingNote(startingNote: Int): KeyNotesModule {
            val json = """
                {
                  "modules": [
                    {
                      "id": 0,
                      "type": "music.input.keyNotes",
                      "name": "keyNotes",
                      "x": 0.0,
                      "y": 0.0,
                      "jacks": {},
                      "startingNote": $startingNote
                    }
                  ],
                  "connections": []
                }
            """.trimIndent()
            val state = GraphState()
            GraphSerializer.deserialize(state, json)
            return state.modules.first { it is KeyNotesModule } as KeyNotesModule
        }

        assertEquals(4.0, loadWithStartingNote(48).octaveIn.getDoubleValue())
        assertEquals(3.0, loadWithStartingNote(36).octaveIn.getDoubleValue())
        // An octave range the jack cannot reach is clipped rather than left out of range.
        assertEquals(2.0, loadWithStartingNote(0).octaveIn.getDoubleValue())
    }

    @Test
    fun testLegacyKeyboardKeepsItsOldPitch() {
        // keyboard13 had no octave of its own; its lowest key was hardwired to 60. That happens
        // to equal today's default octave, so this guards the migration against a future change
        // to that default rather than against the current one.
        val json = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "music.input.keyboard13",
                  "name": "keyboard13",
                  "x": 0.0,
                  "y": 0.0,
                  "jacks": {}
                }
              ],
              "connections": []
            }
        """.trimIndent()
        val state = GraphState()
        GraphSerializer.deserialize(state, json)
        val keyboard = state.modules.first { it is Keyboard13Module } as Keyboard13Module
        assertEquals(5.0, keyboard.octaveIn.getDoubleValue())
        assertEquals(5, keyboard.octave)
    }

    @Test
    fun testSavedKeyboardOctaveWins() {
        val state = GraphState()
        val keyboard = Keyboard25Module()
        keyboard.octaveIn.setValue(6.0)
        keyboard.amplitudeIn.setValue(0.9)
        state.addModule(keyboard, androidx.compose.ui.geometry.Offset.Zero)

        val reloaded = GraphState()
        GraphSerializer.deserialize(reloaded, GraphSerializer.serialize(state, state.modules))
        val loaded = reloaded.modules.first { it is Keyboard25Module } as Keyboard25Module
        assertEquals(6.0, loaded.octaveIn.getDoubleValue())
        assertEquals(6, loaded.octave)
        assertEquals(0.9, loaded.amplitudeIn.getDoubleValue())
    }
}
