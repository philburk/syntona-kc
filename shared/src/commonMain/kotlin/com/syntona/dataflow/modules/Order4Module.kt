package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * Sends one incoming message, a value or a VoiceMessage, to four outputs one after another.
 *
 * The outputs read out4, out3, out2, out1 from top to bottom, the opposite of other modules,
 * because the message goes out of the bottom one first and then up. That gives a defined order
 * for driving inputs that are not kickable, where the order the values arrive in decides the
 * result: send the values that have to be in place first from the lower outputs.
 */
class Order4Module : Module("order4") {
    val input = addUniversalInput("in", isKickable = true)

    // Declared top to bottom, so out1 is the lowest jack on the module
    val out4 = addUniversalOutput("out4")
    val out3 = addUniversalOutput("out3")
    val out2 = addUniversalOutput("out2")
    val out1 = addUniversalOutput("out1")

    /** Bottom first, then upwards. */
    val orderedOutputs = listOf(out1, out2, out3, out4)

    override fun execute(triggerMessage: Message) {
        if (input.isKicked) {
            orderedOutputs.forEach { it.send(triggerMessage) }
        }
    }
}
