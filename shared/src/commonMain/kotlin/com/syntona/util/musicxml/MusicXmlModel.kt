package com.syntona.util.musicxml

/** Document-level MusicXML header metadata (`<work>` and `<identification>`). */
data class MusicXmlMetadata(
    val title: String = "Untitled",
    val composer: String = "Anonymous",
    val date: String? = null,
    val software: String = "Syntona",
)

/** Top-level `<score-partwise>` MusicXML document containing metadata and ordered parts. */
data class MusicXmlScore(
    val metadata: MusicXmlMetadata = MusicXmlMetadata(),
    val parts: List<MusicXmlPart>,
)

/** Single `<score-part>` and its corresponding `<part>` measure sequence. */
data class MusicXmlPart(
    val id: String,
    val name: String,
    val midiChannel: Int? = null,
    val staves: Int = 1,
    val measures: List<MusicXmlMeasure>,
)

/** One `<measure>` inside a [MusicXmlPart]. */
data class MusicXmlMeasure(
    val number: Int,
    val attributes: MusicXmlMeasureAttributes? = null,
    val elements: List<MusicXmlMeasureElement> = emptyList(),
    val barlineRight: MusicXmlBarStyle? = null,
)

/** Measure `<attributes>` block specifying divisions, key, time, staff count, and clef changes. */
data class MusicXmlMeasureAttributes(
    val divisions: Int? = null,
    val key: MusicXmlKey? = null,
    val time: MusicXmlTime? = null,
    val staves: Int? = null,
    val clefs: List<MusicXmlClef> = emptyList(),
)

/** Key signature (`<key><fifths>...</fifths><mode>...</mode></key>`). */
data class MusicXmlKey(
    val fifths: Int,
    val mode: String = "major",
)

/** Time signature (`<time><beats>...</beats><beat-type>...</beat-type></time>`). */
data class MusicXmlTime(
    val beats: Int,
    val beatType: Int,
)

/** Staff clef (`<clef number="..."><sign>...</sign><line>...</line></clef>`). */
data class MusicXmlClef(
    val number: Int? = null,
    val sign: String,
    val line: Int,
    val octaveChange: Int? = null,
)

/** Written notehead / rest duration type (`<type>`). */
enum class MusicXmlNoteType(val xmlName: String) {
    WHOLE("whole"),
    HALF("half"),
    QUARTER("quarter"),
    EIGHTH("eighth"),
    SIXTEENTH("16th"),
    THIRTY_SECOND("32nd"),
}

/** Displayed accidental (`<accidental>`). */
enum class MusicXmlAccidental(val xmlName: String) {
    FLAT("flat"),
    NATURAL("natural"),
    SHARP("sharp"),
    DOUBLE_SHARP("double-sharp"),
    FLAT_FLAT("flat-flat"),
}

/** Beam state (`<beam number="N">...</beam>`). */
enum class MusicXmlBeamType(val xmlName: String) {
    BEGIN("begin"),
    CONTINUE("continue"),
    END("end"),
    FORWARD_HOOK("forward hook"),
    BACKWARD_HOOK("backward hook"),
}

/** Start or stop marker for `<tie>` and `<tied>` elements. */
enum class MusicXmlTieType(val xmlName: String) {
    START("start"),
    STOP("stop"),
}

/** Visual placement (`above` or `below`) on directions. */
enum class MusicXmlPlacement(val xmlName: String) {
    ABOVE("above"),
    BELOW("below"),
}

/** Built-in MusicXML dynamic marking tags inside `<dynamics>`. */
enum class MusicXmlDynamic(val xmlName: String) {
    PPPP("pppp"),
    PPP("ppp"),
    PP("pp"),
    P("p"),
    MP("mp"),
    MF("mf"),
    F("f"),
    FF("ff"),
    FFF("fff"),
    FFFF("ffff"),
    FP("fp"),
    SF("sf"),
    SFZ("sfz"),
}

/** Measure-ending `<bar-style>` value. */
enum class MusicXmlBarStyle(val xmlName: String) {
    LIGHT_HEAVY("light-heavy"),
}

/** Tuplet scaling ratio (`<time-modification>`). */
data class MusicXmlTimeModification(
    val actualNotes: Int,
    val normalNotes: Int,
)

/** Tuplet bracket/number notation inside `<notations><tuplet .../></notations>`. */
data class MusicXmlTupletNotation(
    val type: MusicXmlTieType,
    val number: Int = 1,
    val bracket: Boolean? = null,
    val showNumber: String? = null,
)

/** Dynamic marking attached inside a `<note><notations><dynamics>` block. */
data class MusicXmlInlineDynamic(
    val dynamic: MusicXmlDynamic,
    val placement: MusicXmlPlacement? = null,
)

/** Diatonic pitch spelling (`<pitch><step>...</step><alter>...</alter><octave>...</octave></pitch>`). */
data class MusicXmlPitch(
    val step: Char,
    val alter: Int = 0,
    val octave: Int,
)

/** Ordered music event or inline attribute change inside a `<measure>`. */
sealed interface MusicXmlMeasureElement {
    /** Mid-measure `<attributes>` update (e.g., mid-bar clef change). */
    data class AttributesChange(
        val attributes: MusicXmlMeasureAttributes,
    ) : MusicXmlMeasureElement

    /** Metronome/tempo `<direction>` (`<metronome>` + `<sound tempo="..."/>`). */
    data class TempoDirection(
        val bpm: Int,
        val beatUnit: MusicXmlNoteType = MusicXmlNoteType.QUARTER,
        val placement: MusicXmlPlacement = MusicXmlPlacement.ABOVE,
    ) : MusicXmlMeasureElement

    /** Dynamic marking `<direction>` (`<direction-type><dynamics><mf/></dynamics></direction-type>`). */
    data class DynamicDirection(
        val dynamic: MusicXmlDynamic,
        val placement: MusicXmlPlacement = MusicXmlPlacement.BELOW,
        val staff: Int? = null,
    ) : MusicXmlMeasureElement

    /** Moves the measure time cursor backward (used between upper and lower staves on a multi-staff part). */
    data class Backup(
        val duration: Int,
    ) : MusicXmlMeasureElement

    /** Moves the measure time cursor forward without drawing a rest. */
    data class Forward(
        val duration: Int,
        val voice: Int? = null,
        val staff: Int? = null,
    ) : MusicXmlMeasureElement

    /** Single `<note>` representing a pitched notehead, a chord notehead (`isChord = true`), or a rest (`pitch == null`). */
    data class Note(
        val isChord: Boolean = false,
        val pitch: MusicXmlPitch? = null,
        val isMeasureRest: Boolean = false,
        val duration: Int,
        val ties: List<MusicXmlTieType> = emptyList(),
        val voice: Int = 1,
        val type: MusicXmlNoteType? = null,
        val dots: Int = 0,
        val accidental: MusicXmlAccidental? = null,
        val timeModification: MusicXmlTimeModification? = null,
        val staff: Int? = null,
        val beams: Map<Int, MusicXmlBeamType> = emptyMap(),
        val tiedNotations: List<MusicXmlTieType> = emptyList(),
        val tupletNotations: List<MusicXmlTupletNotation> = emptyList(),
        val isStaccato: Boolean = false,
        val printObject: Boolean = true,
        val inlineDynamics: List<MusicXmlInlineDynamic> = emptyList(),
    ) : MusicXmlMeasureElement
}
