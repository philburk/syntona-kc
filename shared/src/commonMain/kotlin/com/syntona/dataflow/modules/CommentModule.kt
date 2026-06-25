package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class CommentModule : Module("comment") {
    var text by mutableStateOf("")
    var width by mutableStateOf(200f)
    var height by mutableStateOf(100f)

    override fun execute(triggerMessage: Message) {}
}
