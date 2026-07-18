package com.syntona.dataflow.model

import com.syntona.dataflow.modules.SendModule
import com.syntona.dataflow.modules.ReceiveModule

class Mailbox(val name: String) {
    val senders = mutableSetOf<SendModule>()
    val receivers = mutableSetOf<ReceiveModule>()

    fun send(msg: Message) {
        // Since messages are immutable, we safely pass the same instance to all receivers
        receivers.forEach { receiver ->
            receiver.receiveFromMailbox(msg)
        }
    }
}

object MailboxManager {
    private val mailboxes = mutableMapOf<String, Mailbox>()

    fun getOrCreateMailbox(name: String): Mailbox {
        return mailboxes.getOrPut(name) { Mailbox(name) }
    }

    fun registerSend(module: SendModule, name: String) {
        getOrCreateMailbox(name).senders.add(module)
    }

    fun unregisterSend(module: SendModule, name: String) {
        val mailbox = mailboxes[name] ?: return
        mailbox.senders.remove(module)
        checkCleanup(name)
    }

    fun registerReceive(module: ReceiveModule, name: String) {
        getOrCreateMailbox(name).receivers.add(module)
    }

    fun unregisterReceive(module: ReceiveModule, name: String) {
        val mailbox = mailboxes[name] ?: return
        mailbox.receivers.remove(module)
        checkCleanup(name)
    }

    fun onModuleRenamed(module: Module, oldName: String, newName: String) {
        if (oldName == newName) return
        when (module) {
            is SendModule -> {
                unregisterSend(module, oldName)
                registerSend(module, newName)
            }
            is ReceiveModule -> {
                unregisterReceive(module, oldName)
                registerReceive(module, newName)
            }
        }
    }

    fun send(mailboxName: String, msg: Message) {
        mailboxes[mailboxName]?.send(msg)
    }

    private fun checkCleanup(name: String) {
        val mailbox = mailboxes[name] ?: return
        if (mailbox.senders.isEmpty() && mailbox.receivers.isEmpty()) {
            mailboxes.remove(name)
        }
    }

    // Helpers for verification and testing
    fun getMailbox(name: String): Mailbox? = mailboxes[name]
    fun getAllMailboxes(): Map<String, Mailbox> = mailboxes
    fun clear() {
        mailboxes.clear()
    }
}
