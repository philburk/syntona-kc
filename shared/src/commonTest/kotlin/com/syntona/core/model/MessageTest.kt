package com.syntona.core.model

import com.syntona.dataflow.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

import kotlin.jvm.JvmInline



class MessageTest {

    @Test
    fun floatMessage() {
        val m1 = Message.FloatValue(ScoreTime.Zero,123.456)
        assertTrue(m1 is Message.FloatValue)
    }
}
