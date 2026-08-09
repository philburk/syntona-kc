package com.syntona.dataflow.modules

import com.syntona.dataflow.model.InputJack

/** Semitones in an octave, the factor that turns the octave jack into a MIDI pitch. */
const val SEMITONES_PER_OCTAVE = 12

/** Octave the playable keyboards power up on, so the lowest key sounds at pitch 60. */
const val DEFAULT_KEY_OCTAVE = 5

/** Lowest and highest octave the octave jack accepts. */
const val MIN_KEY_OCTAVE = 2.0
const val MAX_KEY_OCTAVE = 8.0

/** Amplitude the keys power up on. */
const val DEFAULT_KEY_AMPLITUDE = 0.8

/** Pitch the lowest key sounded at before the octave jack existed, for migrating older patches. */
const val LEGACY_KEY_BASE_PITCH = 60.0

/**
 * The modules you play notes on by hand: keyNotes and the on-screen keyboards.
 *
 * They share the same two controls - how loud a struck key sounds, and which octave the lowest
 * key sits in - and [GraphSerializer][com.syntona.dataflow.editor.GraphSerializer] migrates
 * patches saved before those jacks existed the same way for both, so the pair is named here
 * rather than duplicated.
 */
interface PlayableKeys {
    /** Amplitude of a struck key, 0.0 to 1.0, sent as the VoiceMessage's extra field. */
    val amplitudeIn: InputJack

    /** Octave of the lowest key. That key sounds at `12 * octave`. */
    val octaveIn: InputJack
}

/** Amplitude to send with the next note. */
fun PlayableKeys.currentAmplitude(): Double = amplitudeIn.getClippedDoubleValue()

/** MIDI pitch of the lowest key, which every key index counts up from. */
fun PlayableKeys.basePitch(): Int = octaveIn.getClippedIntValue() * SEMITONES_PER_OCTAVE
