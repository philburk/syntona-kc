package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class LinkModule : Module("link") {
    val command = addCommandInput("command")

    var filePath by mutableStateOf("")
    var buttonText by mutableStateOf("Load")

    companion object {
        var onTriggerLoad: ((String) -> Unit)? = null
    }

    fun triggerLoad() {
        if (filePath.isNotEmpty()) {
            onTriggerLoad?.invoke(filePath)
        }
    }

    override fun execute(triggerMessage: Message) {
        triggerLoad()
    }
}
