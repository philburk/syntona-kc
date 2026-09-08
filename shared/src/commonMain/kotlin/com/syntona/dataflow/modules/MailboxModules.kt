package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.MailboxManager

class SendModule : Module("send") {
    val inJack = addUniversalInput("in", isKickable = true)

    override var name: String
        get() = super.name
        set(value) {
            val old = super.name
            if (old != value) {
                super.name = value
                MailboxManager.onModuleRenamed(this, old, value)
            }
        }

    init {
        name = "global"
    }

    override fun execute(triggerMessage: Message) {
        if (inJack.isKicked) {
            MailboxManager.send(name, triggerMessage)
        }
    }

    override fun teardown() {
        MailboxManager.unregisterSend(this, name)
        super.teardown()
    }
}

class ReceiveModule : Module("receive") {
    val outJack = addUniversalOutput("out")

    override var name: String
        get() = super.name
        set(value) {
            val old = super.name
            if (old != value) {
                super.name = value
                MailboxManager.onModuleRenamed(this, old, value)
            }
        }

    init {
        name = "global"
    }

    fun receiveFromMailbox(msg: Message) {
        outJack.send(msg)
    }

    override fun execute(triggerMessage: Message) {
        // No input jacks to trigger execution natively
    }

    override fun teardown() {
        MailboxManager.unregisterReceive(this, name)
        super.teardown()
    }
}
