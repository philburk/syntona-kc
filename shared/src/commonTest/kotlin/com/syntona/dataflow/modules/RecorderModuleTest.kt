package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import com.syntona.unitgen.Recorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecorderModuleTest {

    private fun kick(module: RecorderModule, name: String) {
        val jack = module.inputs.first { it.name == name }
        jack.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
    }

    @Test
    fun testModuleCreationAndPorts() {
        val module = RecorderModule()
        assertEquals("recorder", module.name)

        assertEquals(6, module.inputs.size)
        assertEquals("in", module.inputs[0].name)
        assertEquals("amplitude", module.inputs[1].name)
        assertEquals("record", module.inputs[2].name)
        assertEquals("play", module.inputs[3].name)
        assertEquals("pause", module.inputs[4].name)
        assertEquals("rewind", module.inputs[5].name)
        assertTrue(module.inputs[0] is AudioInputJack, "in should carry audio")
        assertTrue(module.inputs[1] is AudioInputJack, "amplitude should carry audio")

        assertEquals(1, module.outputs.size)
        assertEquals("out", module.outputs[0].name)
        assertTrue(module.outputs[0] is AudioOutputJack, "out should carry audio")
    }

    @Test
    fun testCommandJacksDriveTheTransport() {
        val module = RecorderModule()
        assertEquals(Recorder.Mode.PAUSED, module.unit.mode)

        kick(module, "record")
        assertEquals(Recorder.Mode.RECORDING, module.unit.mode)

        kick(module, "pause")
        assertEquals(Recorder.Mode.PAUSED, module.unit.mode)

        kick(module, "play")
        assertEquals(Recorder.Mode.PLAYING, module.unit.mode)

        kick(module, "pause")
        assertEquals(Recorder.Mode.PAUSED, module.unit.mode)
    }

    @Test
    fun testRewindReturnsCursorToStart() {
        val module = RecorderModule()
        kick(module, "record")
        module.unit.generate()
        assertTrue(module.unit.cursor > 0, "recording a block should advance the cursor")

        kick(module, "pause")
        kick(module, "rewind")
        assertEquals(0, module.unit.cursor)
    }

    @Test
    fun testPoolIsFilledAtConstructionSoTheFirstRecordDoesNotAllocate() {
        val module = RecorderModule()
        assertTrue(
            module.unit.recording.pooledSegmentCount > 0,
            "constructor should have primed the segment pool"
        )
        kick(module, "record")
        module.unit.generate()
        assertEquals(0, module.unit.recording.unpooledAllocations)
    }

    @Test
    fun testAmplitudeJackIsWiredToTheUnitsAmplitudePort() {
        val module = RecorderModule()
        val jack = module.inputs[1] as AudioInputJack
        assertTrue(
            jack.unitInputPort === module.unit.amplitude,
            "the amplitude jack should drive the recorder's amplitude port"
        )

        // Record a constant, then play it back through a reduced amplitude set via the jack.
        kick(module, "record")
        module.unit.generate()
        kick(module, "pause")
        module.unit.rewind()

        jack.receive(Message.FloatValue(ScoreTime.Zero, 0.5))
        assertEquals(0.5f, module.unit.amplitude.get(0))
    }

    @Test
    fun testRegisteredInRegistry() {
        val creator = ModuleRegistry.findCreator("recorder")
        assertNotNull(creator, "recorder should be registered")
        assertTrue(creator() is RecorderModule)
    }
}
