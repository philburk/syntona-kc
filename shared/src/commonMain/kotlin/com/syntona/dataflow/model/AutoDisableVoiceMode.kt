package com.syntona.dataflow.model

import com.softsynth.ksyn.unitgen.SupportsAutoDisable

/**
 * Determines whether a module should switch its polyPatch voice off when it finishes,
 * for example when an envelope or sample ends or a string becomes quiet.
 */
enum class AutoDisableVoiceMode {
    AUTO,
    YES,
    NO
}

/**
 * A module whose KSyn unit may switch its polyPatch voice off when it finishes, to save CPU.
 *
 * Anything that offers the choice, saves it, or applies it to a voice checks for this interface
 * rather than for particular module classes, so a new module only has to implement it.
 */
interface AutoDisableVoiceModule {
    /** The unit that can switch the voice off, or null if this module's unit cannot. */
    val autoDisableUnit: SupportsAutoDisable?

    var autoDisableVoice: AutoDisableVoiceMode
}

/** The module's [AutoDisableVoiceModule.autoDisableUnit], or null if it has none. */
val Module.autoDisableUnit: SupportsAutoDisable?
    get() = (this as? AutoDisableVoiceModule)?.autoDisableUnit

/** True if this module can switch its polyPatch voice off, so the choice should be offered. */
val Module.supportsAutoDisableVoice: Boolean
    get() = autoDisableUnit != null

/** The module's auto-disable choice, or null if it cannot switch its voice off. */
var Module.autoDisableVoiceMode: AutoDisableVoiceMode?
    get() = if (supportsAutoDisableVoice) (this as AutoDisableVoiceModule).autoDisableVoice else null
    set(value) {
        if (value != null && supportsAutoDisableVoice) (this as AutoDisableVoiceModule).autoDisableVoice = value
    }

/** The default for a module: off, so a voice is only switched off where the user asked for it. */
fun defaultAutoDisableVoiceMode(unit: Any?): AutoDisableVoiceMode =
    if (unit is SupportsAutoDisable) AutoDisableVoiceMode.NO else AutoDisableVoiceMode.AUTO
