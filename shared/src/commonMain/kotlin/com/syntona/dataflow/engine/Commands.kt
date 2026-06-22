package com.syntona.dataflow.engine

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.OutputJack

class KickJackCommand(
    private val jack: InputJack,
    private val time: ScoreTime
) : SyntonaCommand {
    override val isUndoable = false
    override fun execute() {
        jack.receive(Message.FloatValue(time, 0.0))
    }
    override fun undo() {}
}

class SendOutputCommand(
    private val output: OutputJack,
    private val message: Message
) : SyntonaCommand {
    override val isUndoable = false
    override fun execute() {
        output.send(message)
    }
    override fun undo() {}
}

class SetValueCommand(
    private val jack: InputJack,
    private val value: Double,
    private val time: ScoreTime
) : SyntonaCommand {
    override val isUndoable = false
    override fun execute() {
        jack.receive(Message.FloatValue(time, value))
    }
    override fun undo() {}
}
