package com.syntona.dataflow.modules

import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VuMeterModuleTest {

    @Test
    fun testVu1ModuleInitialization() {
        val vu1 = VuMeterModule(1)
        try {
            assertEquals("vu1", vu1.type)
            assertEquals("vu1", vu1.name)
            assertEquals(1, vu1.numChannels)
            assertFalse(vu1.isAllowedInPolyPatch)

            assertEquals(1, vu1.inputs.size)
            val jack = vu1.inputs[0]
            assertTrue(jack is AudioInputJack)
            assertEquals("in", jack.name)
            assertEquals(0.0, jack.min)
            assertEquals(1.0, jack.max)
            assertEquals(1, vu1.followers.size)
        } finally {
            vu1.teardown()
        }
    }

    @Test
    fun testVu2ModuleInitialization() {
        val vu2 = VuMeterModule(2)
        try {
            assertEquals("vu2", vu2.type)
            assertEquals("vu2", vu2.name)
            assertEquals(2, vu2.numChannels)
            assertFalse(vu2.isAllowedInPolyPatch)

            assertEquals(2, vu2.inputs.size)
            val jack0 = vu2.inputs[0]
            val jack1 = vu2.inputs[1]
            assertTrue(jack0 is AudioInputJack)
            assertTrue(jack1 is AudioInputJack)
            assertEquals("in0", jack0.name)
            assertEquals("in1", jack1.name)
            assertEquals(0.0, jack0.min)
            assertEquals(1.0, jack0.max)
            assertEquals(0.0, jack1.min)
            assertEquals(1.0, jack1.max)
            assertEquals(2, vu2.followers.size)
        } finally {
            vu2.teardown()
        }
    }

    @Test
    fun testJackMaxCanBeIncreased() {
        val vu1 = VuMeterModule(1)
        try {
            val jack = vu1.inputs[0]
            assertEquals(1.0, jack.max)
            jack.max = 2.5
            assertEquals(2.5, jack.max)
            jack.min = 0.5
            assertEquals(0.5, jack.min)
        } finally {
            vu1.teardown()
        }
    }

    @Test
    fun testModuleRegistryBundlePathAndCreator() {
        assertEquals("ksyn.view.vu1", ModuleRegistry.getBundlePath("vu1"))
        assertEquals("ksyn.view.vu2", ModuleRegistry.getBundlePath("vu2"))

        val creator1 = ModuleRegistry.findCreator("ksyn.view.vu1")
        assertNotNull(creator1, "Creator for ksyn.view.vu1 should exist")
        val mod1 = creator1()
        try {
            assertTrue(mod1 is VuMeterModule)
            assertEquals("vu1", mod1.type)
            assertEquals(1, mod1.numChannels)
        } finally {
            mod1.teardown()
        }

        val creator2 = ModuleRegistry.findCreator("ksyn.view.vu2")
        assertNotNull(creator2, "Creator for ksyn.view.vu2 should exist")
        val mod2 = creator2()
        try {
            assertTrue(mod2 is VuMeterModule)
            assertEquals("vu2", mod2.type)
            assertEquals(2, mod2.numChannels)
        } finally {
            mod2.teardown()
        }
    }

    @Test
    fun testPeakFollowerTracking() {
        val vu1 = VuMeterModule(1)
        try {
            val follower = vu1.followers[0]
            // Input buffer initially zero
            val inputs = follower.input.getValues()
            inputs[0] = 0.75f
            follower.generate()
            assertEquals(0.75f, follower.current.value, 0.001f)

            // A smaller input decays according to halfLife
            inputs[0] = 0.0f
            follower.generate()
            assertTrue(follower.current.value < 0.75f)
            assertTrue(follower.current.value > 0.0f)
        } finally {
            vu1.teardown()
        }
    }

    @Test
    fun testLogarithmicAmplitudeConversion() {
        // Zero or near-zero should be at bottom of scale (0.0)
        assertEquals(0f, amplitudeToGraphicFraction(0f, 1f))
        assertEquals(0f, amplitudeToGraphicFraction(0.0005f, 1f))

        // -60 dB floor: ratio = 0.001
        assertEquals(0f, amplitudeToGraphicFraction(0.001f, 1f), 0.001f)

        // -12 dB (start of orange region): 10^(-12/20) ≈ 0.25118864f
        // (-12 - (-60)) / 72 = 48/72 = 2/3 ≈ 0.6667f
        val ampMinus12dB = 10.0.pow(-12.0 / 20.0).toFloat()
        assertEquals(VU_METER_ORANGE_THRESHOLD_FRACTION, amplitudeToGraphicFraction(ampMinus12dB, 1f), 0.002f)

        // 0 dB / max (start of red / over-range region): ratio = 1.0
        // (0 - (-60)) / 72 = 60/72 = 5/6 ≈ 0.8333f
        assertEquals(VU_METER_RED_THRESHOLD_FRACTION, amplitudeToGraphicFraction(1f, 1f), 0.002f)

        // +6 dB (midway through over-range): 10^(6/20) ≈ 1.99526f
        // (6 - (-60)) / 72 = 66/72 ≈ 0.9167f
        val ampPlus6dB = 10.0.pow(6.0 / 20.0).toFloat()
        assertEquals(66f / 72f, amplitudeToGraphicFraction(ampPlus6dB, 1f), 0.002f)

        // +12 dB (top ceiling of meter): 10^(12/20) ≈ 3.98107f
        // (12 - (-60)) / 72 = 72/72 = 1.0f
        val ampPlus12dB = 10.0.pow(12.0 / 20.0).toFloat()
        assertEquals(1.0f, amplitudeToGraphicFraction(ampPlus12dB, 1f), 0.002f)

        // Scaled with max = 2.0
        assertEquals(VU_METER_RED_THRESHOLD_FRACTION, amplitudeToGraphicFraction(2f, 2f), 0.002f)
        assertEquals(1.0f, amplitudeToGraphicFraction(ampPlus12dB * 2f, 2f), 0.002f)

        // Over +12 dB (clamped to 1.0)
        assertEquals(1.0f, amplitudeToGraphicFraction(10f, 1f))
    }
}
