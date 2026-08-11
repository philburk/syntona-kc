package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The pack and unpack modules in the voices bundle.
 *
 * Outputs are observed by connecting a recording module to each jack, the way a patch does,
 * rather than by intercepting sends: [OutputJack] deliberately has no hook for that, and going
 * through a real connection also covers the wiring. All receivers share one log, so the order
 * the jacks fire in is part of what is recorded - it matters, because a downstream module is
 * kicked by whichever jack fires last and must see the other values already settled.
 */
class VoiceUnpackModulesTest {

    private val testTime = ScoreTime(100L)

    /** Records every value it is sent, tagged with its own name, into a shared log. */
    private class RecordingReceiver(
        name: String,
        private val log: MutableList<Pair<String, Double>>
    ) : Module(name) {
        val input = addInput("in", isKickable = true)

        override fun execute(triggerMessage: Message) {
            if (triggerMessage is Message.FloatValue) {
                log.add(name to triggerMessage.value)
            }
        }
    }

    /** Connects a recorder to each named jack, all writing to one shared log. */
    private fun record(vararg jacks: Pair<String, OutputJack>): MutableList<Pair<String, Double>> {
        val log = mutableListOf<Pair<String, Double>>()
        for ((name, jack) in jacks) {
            jack.connect(RecordingReceiver(name, log).input)
        }
        return log
    }

    /** Feeds a message in through the module's own input jack, as a wire would. */
    private fun Module.sendIn(msg: Message) {
        (inputs.first { it.name == "in" }).receive(msg)
    }

    private fun voiceMessage(
        opcode: Message.VoiceOpcode,
        tag: Int,
        value: Double,
        extra: Double
    ) = Message.VoiceMessage(testTime, opcode, tag, value, extra)

    @Test
    fun testModuleRegistryRegistration() {
        val modulesToTest = listOf(
            "packControl", "packProgram",
            "unpackNoteOn", "unpackNoteOff", "unpackControl", "unpackBend", "unpackProgram"
        )
        for (name in modulesToTest) {
            val creator = ModuleRegistry.findCreator(name)
            assertNotNull(creator, "Module '$name' should be registered in ModuleRegistry")
            val instance = creator()
            assertEquals(name, instance.name)
        }
    }

    @Test
    fun testPackControlModule() {
        val pack = PackControlModule()
        pack.indexIn.setValue(7.0)

        val sent = mutableListOf<Message.VoiceMessage>()
        pack.output.connect(object : Module("sink") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                (triggerMessage as? Message.VoiceMessage)?.let { sent.add(it) }
            }
        }.input)

        // valueIn is the kickable jack, so setting it both supplies the value and fires the send.
        pack.valueIn.setValue(0.75)

        assertEquals(1, sent.size)
        val msg = sent.single()
        assertEquals(Message.VoiceOpcode.CONTROL_CHANGE, msg.opcode)
        assertEquals(-1, msg.tag, "packControl addresses all notes; it has no tag input")
        assertEquals(0.75, msg.value)
        assertEquals(7.0, msg.extra, "the controller index travels in extra")
    }

    @Test
    fun testPackProgramModule() {
        val pack = PackProgramModule()

        val sent = mutableListOf<Message.VoiceMessage>()
        pack.output.connect(object : Module("sink") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                (triggerMessage as? Message.VoiceMessage)?.let { sent.add(it) }
            }
        }.input)

        pack.programIn.setValue(42.0)

        assertEquals(1, sent.size)
        val msg = sent.single()
        assertEquals(Message.VoiceOpcode.PROGRAM_CHANGE, msg.opcode)
        assertEquals(-1, msg.tag, "packProgram addresses all notes; it has no tag input")
        assertEquals(42.0, msg.value)
        assertEquals(0.0, msg.extra)
    }

    @Test
    fun testUnpackNoteOnOrderAndFiltering() {
        val unpacker = UnpackNoteOnModule()
        val log = record(
            "pitch" to unpacker.pitch,
            "tag" to unpacker.tag,
            "amplitude" to unpacker.amplitude
        )

        unpacker.sendIn(voiceMessage(Message.VoiceOpcode.NOTE_OFF, tag = 60, value = 60.0, extra = 0.5))
        assertTrue(log.isEmpty(), "unpackNoteOn should ignore NOTE_OFF messages")

        unpacker.sendIn(voiceMessage(Message.VoiceOpcode.NOTE_ON, tag = 60, value = 60.5, extra = 0.8))
        assertEquals(
            listOf("amplitude" to 0.8, "tag" to 60.0, "pitch" to 60.5),
            log,
            "outputs should fire bottom-up: amplitude, tag, pitch"
        )
    }

    @Test
    fun testUnpackNoteOffOrderAndFiltering() {
        val unpacker = UnpackNoteOffModule()
        val log = record(
            "pitch" to unpacker.pitch,
            "tag" to unpacker.tag,
            "amplitude" to unpacker.amplitude
        )

        unpacker.sendIn(voiceMessage(Message.VoiceOpcode.NOTE_ON, tag = 64, value = 64.0, extra = 0.9))
        assertTrue(log.isEmpty(), "unpackNoteOff should ignore NOTE_ON messages")

        unpacker.sendIn(voiceMessage(Message.VoiceOpcode.NOTE_OFF, tag = 64, value = 64.0, extra = 0.0))
        assertEquals(
            listOf("amplitude" to 0.0, "tag" to 64.0, "pitch" to 64.0),
            log,
            "outputs should fire bottom-up: amplitude, tag, pitch"
        )
    }

    @Test
    fun testUnpackControlOrderAndFiltering() {
        val unpacker = UnpackControlModule()
        val log = record(
            "value" to unpacker.value,
            "index" to unpacker.index
        )

        unpacker.sendIn(voiceMessage(Message.VoiceOpcode.NOTE_ON, tag = 1, value = 60.0, extra = 0.5))
        assertTrue(log.isEmpty(), "unpackControl should ignore NOTE_ON messages")

        unpacker.sendIn(voiceMessage(Message.VoiceOpcode.CONTROL_CHANGE, tag = -1, value = 0.5, extra = 74.0))
        assertEquals(
            listOf("index" to 74.0, "value" to 0.5),
            log,
            "outputs should fire bottom-up: index, value"
        )
    }

    @Test
    fun testUnpackBendOrderAndFiltering() {
        val unpacker = UnpackBendModule()
        val log = record(
            "bend" to unpacker.bend,
            "tag" to unpacker.tag
        )

        unpacker.sendIn(voiceMessage(Message.VoiceOpcode.CONTROL_CHANGE, tag = -1, value = 0.5, extra = 74.0))
        assertTrue(log.isEmpty(), "unpackBend should ignore CONTROL_CHANGE messages")

        unpacker.sendIn(voiceMessage(Message.VoiceOpcode.PITCH_BEND, tag = 3, value = 1.5, extra = 0.0))
        assertEquals(
            listOf("tag" to 3.0, "bend" to 1.5),
            log,
            "outputs should fire bottom-up: tag, bend"
        )
    }

    @Test
    fun testUnpackProgramOrderAndFiltering() {
        val unpacker = UnpackProgramModule()
        val log = record("program" to unpacker.program)

        unpacker.sendIn(voiceMessage(Message.VoiceOpcode.NOTE_ON, tag = 1, value = 60.0, extra = 0.5))
        assertTrue(log.isEmpty(), "unpackProgram should ignore NOTE_ON messages")

        unpacker.sendIn(voiceMessage(Message.VoiceOpcode.PROGRAM_CHANGE, tag = 0, value = 12.0, extra = 0.0))
        assertEquals(listOf("program" to 12.0), log)
    }

    @Test
    fun testUnpackersIgnoreNonVoiceMessages() {
        val unpacker = UnpackNoteOnModule()
        val log = record(
            "pitch" to unpacker.pitch,
            "tag" to unpacker.tag,
            "amplitude" to unpacker.amplitude
        )

        // A plain number reaching a voice jack must not be unpacked as if it were a note.
        unpacker.sendIn(Message.FloatValue(testTime, 60.0))
        assertTrue(log.isEmpty(), "unpackNoteOn should ignore messages that are not VoiceMessages")
    }
}
