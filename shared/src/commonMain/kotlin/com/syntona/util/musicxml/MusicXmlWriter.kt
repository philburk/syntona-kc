package com.syntona.util.musicxml

/**
 * Pure-Kotlin serializer emitting MusicXML 3.1 (`<score-partwise>`) UTF-8 XML strings.
 * Follows standard MusicXML element ordering so exported scores open cleanly in Notion, MuseScore,
 * Finale, Dorico, and Sibelius.
 */
object MusicXmlWriter {

    /** Serializes [score] into a formatted MusicXML `<score-partwise>` document string. */
    fun write(score: MusicXmlScore): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 3.1 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">""")
        appendLine("""<score-partwise version="3.1">""")

        val meta = score.metadata
        if (meta.title.isNotEmpty()) {
            appendLine("\t<work>")
            appendLine("\t\t<work-title>${escapeText(meta.title)}</work-title>")
            appendLine("\t</work>")
        }

        val hasIdentification = meta.composer.isNotEmpty() || meta.software.isNotEmpty() || !meta.date.isNullOrBlank()
        if (hasIdentification) {
            appendLine("\t<identification>")
            if (meta.composer.isNotEmpty()) {
                appendLine("""		<creator type="composer">${escapeText(meta.composer)}</creator>""")
            }
            if (meta.software.isNotEmpty() || !meta.date.isNullOrBlank()) {
                appendLine("\t\t<encoding>")
                if (meta.software.isNotEmpty()) {
                    appendLine("\t\t\t<software>${escapeText(meta.software)}</software>")
                }
                if (!meta.date.isNullOrBlank()) {
                    appendLine("\t\t\t<encoding-date>${escapeText(meta.date)}</encoding-date>")
                }
                appendLine("\t\t</encoding>")
            }
            appendLine("\t</identification>")
        }

        appendLine("\t<part-list>")
        for (part in score.parts) {
            val safeId = escapeAttr(part.id)
            appendLine("""		<score-part id="$safeId">""")
            appendLine("\t\t\t<part-name>${escapeText(part.name)}</part-name>")
            appendLine("""			<score-instrument id="$safeId-I1">""")
            appendLine("\t\t\t\t<instrument-name>${escapeText(part.name)}</instrument-name>")
            appendLine("\t\t\t</score-instrument>")
            part.midiChannel?.let { channel ->
                appendLine("""			<midi-instrument id="$safeId-I1">""")
                appendLine("\t\t\t\t<midi-channel>$channel</midi-channel>")
                appendLine("\t\t\t</midi-instrument>")
            }
            appendLine("\t\t</score-part>")
        }
        appendLine("\t</part-list>")

        for (part in score.parts) {
            appendLine("""	<part id="${escapeAttr(part.id)}">""")
            for (measure in part.measures) {
                appendLine("""		<measure number="${measure.number}">""")
                measure.attributes?.let { appendAttributes(it) }
                for (element in measure.elements) {
                    when (element) {
                        is MusicXmlMeasureElement.AttributesChange -> appendAttributes(element.attributes)
                        is MusicXmlMeasureElement.TempoDirection -> appendTempoDirection(element)
                        is MusicXmlMeasureElement.DynamicDirection -> appendDynamicDirection(element)
                        is MusicXmlMeasureElement.Backup -> {
                            appendLine("\t\t\t<backup>")
                            appendLine("\t\t\t\t<duration>${element.duration}</duration>")
                            appendLine("\t\t\t</backup>")
                        }
                        is MusicXmlMeasureElement.Forward -> {
                            appendLine("\t\t\t<forward>")
                            appendLine("\t\t\t\t<duration>${element.duration}</duration>")
                            element.voice?.let { appendLine("\t\t\t\t<voice>$it</voice>") }
                            element.staff?.let { appendLine("\t\t\t\t<staff>$it</staff>") }
                            appendLine("\t\t\t</forward>")
                        }
                        is MusicXmlMeasureElement.Note -> appendNote(element)
                    }
                }
                measure.barlineRight?.let { style ->
                    appendLine("""			<barline location="right">""")
                    appendLine("\t\t\t\t<bar-style>${style.xmlName}</bar-style>")
                    appendLine("\t\t\t</barline>")
                }
                appendLine("\t\t</measure>")
            }
            appendLine("\t</part>")
        }

        append("</score-partwise>")
        appendLine()
    }

    private fun StringBuilder.appendAttributes(attrs: MusicXmlMeasureAttributes) {
        val hasContent = attrs.divisions != null ||
            attrs.key != null ||
            attrs.time != null ||
            attrs.staves != null ||
            attrs.clefs.isNotEmpty()
        if (!hasContent) return

        appendLine("\t\t\t<attributes>")
        attrs.divisions?.let { appendLine("\t\t\t\t<divisions>$it</divisions>") }
        attrs.key?.let { key ->
            appendLine("\t\t\t\t<key>")
            appendLine("\t\t\t\t\t<fifths>${key.fifths}</fifths>")
            appendLine("\t\t\t\t\t<mode>${escapeText(key.mode)}</mode>")
            appendLine("\t\t\t\t</key>")
        }
        attrs.time?.let { time ->
            appendLine("\t\t\t\t<time>")
            appendLine("\t\t\t\t\t<beats>${time.beats}</beats>")
            appendLine("\t\t\t\t\t<beat-type>${time.beatType}</beat-type>")
            appendLine("\t\t\t\t</time>")
        }
        attrs.staves?.let { appendLine("\t\t\t\t<staves>$it</staves>") }
        for (clef in attrs.clefs) {
            val numberAttr = clef.number?.let { """ number="$it"""" } ?: ""
            appendLine("\t\t\t\t<clef$numberAttr>")
            appendLine("\t\t\t\t\t<sign>${escapeText(clef.sign)}</sign>")
            appendLine("\t\t\t\t\t<line>${clef.line}</line>")
            clef.octaveChange?.let { appendLine("\t\t\t\t\t<clef-octave-change>$it</clef-octave-change>") }
            appendLine("\t\t\t\t</clef>")
        }
        appendLine("\t\t\t</attributes>")
    }

    private fun StringBuilder.appendTempoDirection(dir: MusicXmlMeasureElement.TempoDirection) {
        appendLine("""			<direction placement="${dir.placement.xmlName}">""")
        appendLine("\t\t\t\t<direction-type>")
        appendLine("\t\t\t\t\t<metronome>")
        appendLine("\t\t\t\t\t\t<beat-unit>${dir.beatUnit.xmlName}</beat-unit>")
        appendLine("\t\t\t\t\t\t<per-minute>${dir.bpm}</per-minute>")
        appendLine("\t\t\t\t\t</metronome>")
        appendLine("\t\t\t\t</direction-type>")
        appendLine("""				<sound tempo="${dir.bpm}"/>""")
        appendLine("\t\t\t</direction>")
    }

    private fun StringBuilder.appendDynamicDirection(dir: MusicXmlMeasureElement.DynamicDirection) {
        appendLine("""			<direction placement="${dir.placement.xmlName}">""")
        appendLine("\t\t\t\t<direction-type>")
        appendLine("\t\t\t\t\t<dynamics>")
        appendLine("\t\t\t\t\t\t<${dir.dynamic.xmlName}/>")
        appendLine("\t\t\t\t\t</dynamics>")
        appendLine("\t\t\t\t</direction-type>")
        dir.staff?.let { appendLine("\t\t\t\t<staff>$it</staff>") }
        appendLine("\t\t\t</direction>")
    }

    private fun StringBuilder.appendNote(note: MusicXmlMeasureElement.Note) {
        appendLine("\t\t\t<note>")
        if (note.isChord) {
            appendLine("\t\t\t\t<chord/>")
        }
        val pitch = note.pitch
        if (pitch != null) {
            appendLine("\t\t\t\t<pitch>")
            appendLine("\t\t\t\t\t<step>${pitch.step}</step>")
            if (pitch.alter != 0) {
                appendLine("\t\t\t\t\t<alter>${pitch.alter}</alter>")
            }
            appendLine("\t\t\t\t\t<octave>${pitch.octave}</octave>")
            appendLine("\t\t\t\t</pitch>")
        } else if (note.isMeasureRest) {
            appendLine("""				<rest measure="yes"/>""")
        } else {
            appendLine("\t\t\t\t<rest/>")
        }
        appendLine("\t\t\t\t<duration>${note.duration}</duration>")
        for (tie in note.ties) {
            appendLine("""				<tie type="${tie.xmlName}"/>""")
        }
        appendLine("\t\t\t\t<voice>${note.voice}</voice>")
        note.type?.let { appendLine("\t\t\t\t<type>${it.xmlName}</type>") }
        repeat(note.dots) {
            appendLine("\t\t\t\t<dot/>")
        }
        note.accidental?.let { appendLine("\t\t\t\t<accidental>${it.xmlName}</accidental>") }
        note.timeModification?.let { tm ->
            appendLine("\t\t\t\t<time-modification>")
            appendLine("\t\t\t\t\t<actual-notes>${tm.actualNotes}</actual-notes>")
            appendLine("\t\t\t\t\t<normal-notes>${tm.normalNotes}</normal-notes>")
            appendLine("\t\t\t\t</time-modification>")
        }
        note.staff?.let { appendLine("\t\t\t\t<staff>$it</staff>") }
        for (level in note.beams.keys.sorted()) {
            val beamType = note.beams.getValue(level)
            appendLine("""				<beam number="$level">${beamType.xmlName}</beam>""")
        }
        val hasNotations = note.tiedNotations.isNotEmpty() || note.tupletNotations.isNotEmpty() || note.isStaccato
        if (hasNotations) {
            appendLine("\t\t\t\t<notations>")
            for (tied in note.tiedNotations) {
                appendLine("""					<tied type="${tied.xmlName}"/>""")
            }
            for (tuplet in note.tupletNotations) {
                val bracketAttr = tuplet.bracket?.let { if (it) """ bracket="yes"""" else """ bracket="no"""" } ?: ""
                val showAttr = tuplet.showNumber?.let { """ show-number="${escapeAttr(it)}"""" } ?: ""
                appendLine("""					<tuplet type="${tuplet.type.xmlName}" number="${tuplet.number}"$bracketAttr$showAttr/>""")
            }
            if (note.isStaccato) {
                appendLine("\t\t\t\t\t<articulations>")
                appendLine("\t\t\t\t\t\t<staccato/>")
                appendLine("\t\t\t\t\t</articulations>")
            }
            appendLine("\t\t\t\t</notations>")
        }
        appendLine("\t\t\t</note>")
    }

    private fun escapeText(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(ch)
            }
        }
    }

    private fun escapeAttr(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
