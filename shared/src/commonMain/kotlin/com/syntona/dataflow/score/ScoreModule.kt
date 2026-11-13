package com.syntona.dataflow.score

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.KickJackCommand
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.PianoRollTiming
import kotlin.math.roundToLong

/**
 * Staff notation score module under `music.compose`.
 * Displays a stave with key signature, time signature, notes, and rests rendered via SMuFL (Leland)
 * and outputs timed [Message.VoiceMessage] events on [output].
 */
class ScoreModule : Module("score"), EngineAware {
    val start = addCommandInput("start")
    val stop = addCommandInput("stop")
    val tempo = addInput("tempo").also {
        it.min = PianoRollTiming.MIN_TEMPO
        it.max = PianoRollTiming.MAX_TEMPO
        it.setValue(PianoRollTiming.DEFAULT_TEMPO)
    }
    val output = addVoiceOutput("out")

    val scoreData = ScoreData()

    var windowX: Float? = null
    var windowY: Float? = null
    var windowWidth: Float? = null
    var windowHeight: Float? = null

    var isPlaying by mutableStateOf(false)
        private set

    var activeItemIndex by mutableStateOf<Int?>(null)
        private set

    private var engine: DataFlowEngine? = null
    private val scheduledEvents = mutableListOf<ScheduledEvent>()
    private val soundingNotes = mutableMapOf<Int, Double>() // tag -> pitch

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun execute(triggerMessage: Message) {
        val now = triggerMessage.time
        when {
            start.isKicked -> startPlayback(now)
            stop.isKicked -> stopPlayback(now)
        }
    }

    fun requestPlayToggle() {
        val e = engine ?: return
        val jack = if (isPlaying) stop else start
        e.post(KickJackCommand(jack, ScoreClock.now()))
    }

    private fun startPlayback(now: ScoreTime) {
        if (isPlaying) stopPlayback(now)
        val e = engine ?: return

        val tempoVal = tempo.getDoubleValue(PianoRollTiming.DEFAULT_TEMPO).coerceAtLeast(1.0)
        val nsPerTick = PianoRollTiming.nanosPerTick(tempoVal)
        val items = scoreData.measures.flatMap { it.items }
        if (items.isEmpty()) return

        isPlaying = true
        var tickCursor = 0L
        var globalIdx = 0

        for (item in items) {
            val itemIdx = globalIdx++
            val itemTicks = item.totalTicks.toLong()
            val onTime = now + ScoreTime((tickCursor * nsPerTick).roundToLong())
            val offTime = now + ScoreTime(((tickCursor + itemTicks) * nsPerTick).roundToLong())
            val durSec = (offTime.nanoseconds - onTime.nanoseconds).coerceAtLeast(0L) / 1_000_000_000.0

            scheduledEvents.add(e.schedule(onTime) {
                activeItemIndex = itemIdx
                if (item is ScoreItem.Note) {
                    val pitch = item.pitch.midiPitch.toDouble()
                    output.send(
                        Message.VoiceMessage(
                            time = onTime,
                            opcode = Message.VoiceOpcode.NOTE,
                            tag = itemIdx,
                            value = pitch,
                            extra = item.amplitude,
                            duration = durSec,
                        )
                    )
                    soundingNotes[itemIdx] = pitch
                }
            })
            if (item is ScoreItem.Note) {
                scheduledEvents.add(e.schedule(offTime) {
                    soundingNotes.remove(itemIdx)
                })
            }
            tickCursor += itemTicks
        }

        val endTime = now + ScoreTime((tickCursor * nsPerTick).roundToLong()) + ScoreTime(1L)
        scheduledEvents.add(e.schedule(endTime) {
            activeItemIndex = null
            isPlaying = false
            scheduledEvents.clear()
        })
    }

    private fun stopPlayback(now: ScoreTime) {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        for ((tag, pitch) in soundingNotes) {
            output.send(
                Message.VoiceMessage(
                    time = now,
                    opcode = Message.VoiceOpcode.NOTE_OFF,
                    tag = tag,
                    value = pitch,
                    extra = 0.0,
                )
            )
        }
        soundingNotes.clear()
        activeItemIndex = null
        isPlaying = false
    }

    override fun teardown() {
        stopPlayback(ScoreClock.now())
        super.teardown()
    }
}
