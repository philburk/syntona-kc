package com.syntona.dataflow.modules

import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyNotesModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = KeyNotesModule()
        assertEquals("keyNotes", module.name)
        assertEquals("12345678", module.row1Keys)
        assertEquals("qwertyui", module.row2Keys)
        assertEquals("asdfghjk", module.row3Keys)
        assertEquals("zxcvbnm,", module.row4Keys)

        val output = module.outputs.find { it.name == "out" }
        assertNotNull(output, "KeyNotesModule should have an output named 'out'")

        val amplitude = module.inputs.find { it.name == "amplitude" }
        assertNotNull(amplitude, "KeyNotesModule should have an input named 'amplitude'")
        assertEquals(0.0, amplitude.min)
        assertEquals(1.0, amplitude.max)
        assertEquals(0.8, amplitude.getDoubleValue())

        val offset = module.inputs.find { it.name == "offset" }
        assertNotNull(offset, "KeyNotesModule should have an input named 'offset'")
        assertEquals(0.0, offset.min)
        assertEquals(80.0, offset.max)
        assertEquals(48.0, offset.getDoubleValue())
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
            val input = addVoiceInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                lastMessage = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyNotesModule.output, receiver.input)

        // Default offset is 48.
        // Row 4 (zxcvbnm,): offset + 0
        // Row 3 (asdfghjk): offset + 12
        // Row 2 (qwertyui): offset + 24
        // Row 1 (12345678): offset + 36

        // 1. Press 'z' (ASCII 122). Row 4 index 0 -> 48 + 0 + 0 = 48.
        val processedZ = graphState.handleKeyStroke(122, isDown = true)
        assertTrue(processedZ, "Keystroke 'z' should be processed")
        val zMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(zMsg)
        assertEquals(Message.VoiceOpcode.NOTE_ON, zMsg.opcode)
        assertEquals(48, zMsg.tag)
        assertEquals(48.0, zMsg.value)

        // 2. Release 'z' (ASCII 122).
        val releasedZ = graphState.handleKeyStroke(122, isDown = false)
        assertTrue(releasedZ, "Keystroke release 'z' should be processed")
        val zReleaseMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(zReleaseMsg)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, zReleaseMsg.opcode)
        assertEquals(48, zReleaseMsg.tag)
        assertEquals(48.0, zReleaseMsg.value)

        // 3. Press 'x' (ASCII 120). Row 4 index 1 -> 48 + 0 + 2 = 50.
        val processedX = graphState.handleKeyStroke(120, isDown = true)
        assertTrue(processedX, "Keystroke 'x' should be processed")
        val xMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(xMsg)
        assertEquals(Message.VoiceOpcode.NOTE_ON, xMsg.opcode)
        assertEquals(50, xMsg.tag)

        // 4. Press ',' (ASCII 44). Row 4 index 7 (octave note) -> 48 + 0 + 12 = 60.
        val processedComma = graphState.handleKeyStroke(44, isDown = true)
        assertTrue(processedComma, "Keystroke ',' should be processed")
        val commaMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(commaMsg)
        assertEquals(60, commaMsg.tag)

        // 5. Press 'a' (ASCII 97). Row 3 index 0 -> 48 + 12 + 0 = 60.
        // Verifies the user's issue with 'a' is resolved and connects to the octave below!
        val processedA = graphState.handleKeyStroke(97, isDown = true)
        assertTrue(processedA, "Keystroke 'a' should be processed")
        val aMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(aMsg)
        assertEquals(60, aMsg.tag)

        // 6. Press 's' (ASCII 115). Row 3 index 1 -> 48 + 12 + 2 = 62.
        val processedS = graphState.handleKeyStroke(115, isDown = true)
        assertTrue(processedS, "Keystroke 's' should be processed")
        val sMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(sMsg)
        assertEquals(62, sMsg.tag)

        // 7. Press 'k' (ASCII 107). Row 3 index 7 -> 48 + 12 + 12 = 72.
        val processedK = graphState.handleKeyStroke(107, isDown = true)
        assertTrue(processedK, "Keystroke 'k' should be processed")
        val kMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(kMsg)
        assertEquals(72, kMsg.tag)

        // 8. Press 'q' (ASCII 113). Row 2 index 0 -> 48 + 24 + 0 = 72.
        val processedQ = graphState.handleKeyStroke(113, isDown = true)
        assertTrue(processedQ, "Keystroke 'q' should be processed")
        val qMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(qMsg)
        assertEquals(72, qMsg.tag)

        // 9. Press 'i' (ASCII 105). Row 2 index 7 -> 48 + 24 + 12 = 84.
        val processedI = graphState.handleKeyStroke(105, isDown = true)
        assertTrue(processedI, "Keystroke 'i' should be processed")
        val iMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(iMsg)
        assertEquals(84, iMsg.tag)

        // 10. Press '1' (ASCII 49). Row 1 index 0 -> 48 + 36 + 0 = 84.
        val processed1 = graphState.handleKeyStroke(49, isDown = true)
        assertTrue(processed1, "Keystroke '1' should be processed")
        val oneMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(oneMsg)
        assertEquals(84, oneMsg.tag)

        // 11. Press '2' (ASCII 50). Row 1 index 1 -> 48 + 36 + 2 = 86.
        val processed2 = graphState.handleKeyStroke(50, isDown = true)
        assertTrue(processed2, "Keystroke '2' should be processed")
        val twoMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(twoMsg)
        assertEquals(86, twoMsg.tag)

        // 12. Press '8' (ASCII 56). Row 1 index 7 -> 48 + 36 + 12 = 96.
        val processed8 = graphState.handleKeyStroke(56, isDown = true)
        assertTrue(processed8, "Keystroke '8' should be processed")
        val eightMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(eightMsg)
        assertEquals(96, eightMsg.tag)
    }

    @Test
    fun testOffsetJackSetsPitchAndDiatonicTransposition() {
        val graphState = GraphState()
        val keyNotesModule = KeyNotesModule()
        graphState.addModule(keyNotesModule, androidx.compose.ui.geometry.Offset.Zero)

        var lastMessage: Message? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addVoiceInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                lastMessage = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyNotesModule.output, receiver.input)

        // Set offset to 60 (Middle C diatonic scale)
        keyNotesModule.offsetIn.setValue(60.0)
        graphState.handleKeyStroke(122, isDown = true) // 'z'
        assertEquals(60, (lastMessage as? Message.VoiceMessage)?.tag)
        graphState.handleKeyStroke(122, isDown = false)

        // Set offset to 61 (C# diatonic scale)
        keyNotesModule.offsetIn.setValue(61.0)
        graphState.handleKeyStroke(122, isDown = true) // 'z'
        assertEquals(61, (lastMessage as? Message.VoiceMessage)?.tag)
        graphState.handleKeyStroke(122, isDown = false)
        graphState.handleKeyStroke(120, isDown = true) // 'x' -> 61 + 2 = 63 (D#)
        assertEquals(63, (lastMessage as? Message.VoiceMessage)?.tag)
        graphState.handleKeyStroke(120, isDown = false)

        // Set offset to 0.0 (lowest possible)
        keyNotesModule.offsetIn.setValue(0.0)
        graphState.handleKeyStroke(122, isDown = true)
        assertEquals(0, (lastMessage as? Message.VoiceMessage)?.tag)
        graphState.handleKeyStroke(122, isDown = false)

        // Out of range values are clipped to the jack's 0 to 80 range
        keyNotesModule.offsetIn.setValue(99.0)
        graphState.handleKeyStroke(122, isDown = true)
        assertEquals(80, (lastMessage as? Message.VoiceMessage)?.tag)
    }

    @Test
    fun testHighOffsetDropsTopNoteWithoutDroppingRow() {
        val graphState = GraphState()
        val keyNotesModule = KeyNotesModule()
        graphState.addModule(keyNotesModule, androidx.compose.ui.geometry.Offset.Zero)

        var lastMessage: Message? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addVoiceInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                lastMessage = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyNotesModule.output, receiver.input)

        // At maximum offset 80:
        // Row 1 keys:
        // '1' (index 0) -> 80 + 36 = 116 (plays)
        // '7' (index 6) -> 80 + 36 + 11 = 127 (plays)
        // '8' (index 7) -> 80 + 36 + 12 = 128 (> 127, dropped!)
        keyNotesModule.offsetIn.setValue(80.0)

        // 1. Press '1'
        val processed1 = graphState.handleKeyStroke(49, isDown = true)
        assertTrue(processed1)
        assertEquals(116, (lastMessage as? Message.VoiceMessage)?.tag)

        // 2. Press '7'
        val processed7 = graphState.handleKeyStroke(55, isDown = true)
        assertTrue(processed7)
        assertEquals(127, (lastMessage as? Message.VoiceMessage)?.tag)

        // 3. Press '8' -> pitch would be 128 > 127, must be dropped!
        lastMessage = null
        val processed8 = graphState.handleKeyStroke(56, isDown = true)
        assertTrue(processed8, "Keystroke should still be handled so it does not trigger app shortcuts")
        assertNull(lastMessage, "No NOTE_ON should be sent for pitch above 127")

        // 4. Release '8' -> should not send NOTE_OFF
        lastMessage = null
        graphState.handleKeyStroke(56, isDown = false)
        assertNull(lastMessage, "No NOTE_OFF should be sent for dropped key")

        // 5. Release '7' -> should send NOTE_OFF for 127
        graphState.handleKeyStroke(55, isDown = false)
        val release7 = lastMessage as? Message.VoiceMessage
        assertNotNull(release7)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, release7.opcode)
        assertEquals(127, release7.tag)
    }

    @Test
    fun testNoteReleasedAtTheOffsetItWasStruck() {
        val graphState = GraphState()
        val keyNotesModule = KeyNotesModule()
        graphState.addModule(keyNotesModule, androidx.compose.ui.geometry.Offset.Zero)

        var lastMessage: Message? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addVoiceInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                lastMessage = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyNotesModule.output, receiver.input)

        graphState.handleKeyStroke(122, isDown = true)
        assertEquals(48, (lastMessage as? Message.VoiceMessage)?.tag)

        // Moving the offset while the key is held must not strand the sounding note.
        keyNotesModule.offsetIn.setValue(60.0)
        graphState.handleKeyStroke(122, isDown = false)
        val release = lastMessage as? Message.VoiceMessage
        assertNotNull(release)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, release.opcode)
        assertEquals(48, release.tag)
    }

    @Test
    fun testAmplitudeJackSetsExtra() {
        val graphState = GraphState()
        val keyNotesModule = KeyNotesModule()
        graphState.addModule(keyNotesModule, androidx.compose.ui.geometry.Offset.Zero)

        var lastMessage: Message? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addVoiceInput("in", isKickable = true)
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
            val input = addVoiceInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                lastMessage = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyNotesModule.output, receiver.input)

        // 1. Press 'z' with Shift key down -> MIDI note 72 (48 + 0 + 24)
        val processedZShift = graphState.handleKeyStroke(122, isDown = true, isShiftPressed = true)
        assertTrue(processedZShift)
        val zMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(zMsg)
        assertEquals(72, zMsg.tag)

        // 2. Release 'z' with Shift key released -> NOTE_OFF still at MIDI note 72
        val releasedZNoShift = graphState.handleKeyStroke(122, isDown = false, isShiftPressed = false)
        assertTrue(releasedZNoShift)
        val zReleaseMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(zReleaseMsg)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, zReleaseMsg.opcode)
        assertEquals(72, zReleaseMsg.tag)

        // 3. Press 'x' with Shift key released -> MIDI note 50 (48 + 2)
        val processedXNoShift = graphState.handleKeyStroke(120, isDown = true, isShiftPressed = false)
        assertTrue(processedXNoShift)
        val xMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(xMsg)
        assertEquals(50, xMsg.tag)

        // 4. Release 'x' with Shift key down -> NOTE_OFF still at MIDI note 50
        val releasedXShift = graphState.handleKeyStroke(120, isDown = false, isShiftPressed = true)
        assertTrue(releasedXShift)
        val xReleaseMsg = lastMessage as? Message.VoiceMessage
        assertNotNull(xReleaseMsg)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, xReleaseMsg.opcode)
        assertEquals(50, xReleaseMsg.tag)
    }

    @Test
    fun testSerializationAndDeserialization() {
        val graphState = GraphState()
        val module = KeyNotesModule()
        module.row1Keys = "87654321"
        module.row2Keys = "iuytrewq"
        module.row3Keys = "kjhgfdsa"
        module.row4Keys = ",mnbvcxz"
        module.offsetIn.setValue(52.0)
        module.amplitudeIn.setValue(0.6)
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        // Serialize
        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("\"row1Keys\": \"87654321\""), "Serialized string should contain row1Keys")
        assertTrue(jsonStr.contains("\"row2Keys\": \"iuytrewq\""), "Serialized string should contain row2Keys")
        assertTrue(jsonStr.contains("\"row3Keys\": \"kjhgfdsa\""), "Serialized string should contain row3Keys")
        assertTrue(jsonStr.contains("\"row4Keys\": \",mnbvcxz\""), "Serialized string should contain row4Keys")

        // Deserialize
        val newGraphState = GraphState()
        GraphSerializer.deserialize(newGraphState, jsonStr)
        val deserializedModule = newGraphState.modules.find { it.name == "keyNotes" } as? KeyNotesModule
        assertNotNull(deserializedModule)
        assertEquals("87654321", deserializedModule.row1Keys)
        assertEquals("iuytrewq", deserializedModule.row2Keys)
        assertEquals("kjhgfdsa", deserializedModule.row3Keys)
        assertEquals(",mnbvcxz", deserializedModule.row4Keys)
        assertEquals(52.0, deserializedModule.offsetIn.getDoubleValue())
        assertEquals(0.6, deserializedModule.amplitudeIn.getDoubleValue())
    }

    @Test
    fun testBackwardCompatibilityDeserialization() {
        // Old JSON format using "mappingString"
        // Old letter strings are ignored and default to the new diatonic rows.
        val oldJson = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "core.input.keyNotes",
                  "name": "keyNotes",
                  "x": 0.0,
                  "y": 0.0,
                  "jacks": {
                    "octave": {
                      "v": 5.0
                    }
                  },
                  "mappingString": "zsxdcvgbhnjm,q2w3er5t6y7ui9o0p",
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
        assertEquals("12345678", module.row1Keys)
        assertEquals("qwertyui", module.row2Keys)
        assertEquals("asdfghjk", module.row3Keys)
        assertEquals("zxcvbnm,", module.row4Keys)
        // The old octave 5.0 becomes offset 60.0 (5 * 12)
        assertEquals(60.0, module.offsetIn.getDoubleValue())
    }

    @Test
    fun testLegacyStartingNoteBecomesOffset() {
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

        assertEquals(48.0, loadWithStartingNote(48).offsetIn.getDoubleValue())
        assertEquals(36.0, loadWithStartingNote(36).offsetIn.getDoubleValue())
        // An offset below min is clipped to 0.0
        assertEquals(0.0, loadWithStartingNote(-10).offsetIn.getDoubleValue())
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
