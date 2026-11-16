package com.syntona.util.musicxml

import kotlin.math.roundToInt

/**
 * Pure-Kotlin reader parsing MusicXML `<score-partwise>` documents into [MusicXmlScore].
 * Unsupported tags, layouts, and articulations are safely ignored.
 */
object MusicXmlReader {

    /** Parses a UTF-8 MusicXML `<score-partwise>` string into [MusicXmlScore]. */
    fun read(xml: String): MusicXmlScore {
        val root = XmlParser.parse(xml)
        if (root.name != "score-partwise") {
            throw IllegalArgumentException(
                "Expected <score-partwise> MusicXML root element, found <${root.name}>"
            )
        }

        val metadata = parseMetadata(root)
        val partDescriptors = parsePartList(root.child("part-list"))
        val descriptorById = partDescriptors.associateBy { it.id }

        val partElements = root.childrenNamed("part")
        val parts = mutableListOf<MusicXmlPart>()
        for ((idx, partEl) in partElements.withIndex()) {
            val id = partEl.attributes["id"]?.takeIf { it.isNotBlank() } ?: "P${idx + 1}"
            val desc = descriptorById[id]
            val parsedMeasures = partEl.childrenNamed("measure").mapIndexed { mIdx, mEl ->
                parseMeasure(mEl, defaultNumber = mIdx + 1)
            }
            val inferredStaves = parsedMeasures
                .flatMap { m ->
                    buildList {
                        m.attributes?.staves?.let { add(it) }
                        m.attributes?.clefs?.forEach { it.number?.let(::add) }
                        m.elements.forEach { el ->
                            when (el) {
                                is MusicXmlMeasureElement.Note -> el.staff?.let(::add)
                                is MusicXmlMeasureElement.AttributesChange -> {
                                    el.attributes.staves?.let(::add)
                                    el.attributes.clefs.forEach { it.number?.let(::add) }
                                }
                                else -> {}
                            }
                        }
                    }
                }
                .maxOrNull()
                ?: 1

            parts.add(
                MusicXmlPart(
                    id = id,
                    name = desc?.name ?: "Instrument ${idx + 1}",
                    midiChannel = desc?.midiChannel,
                    staves = inferredStaves.coerceAtLeast(1),
                    measures = parsedMeasures,
                )
            )
        }

        return MusicXmlScore(metadata = metadata, parts = parts)
    }

    private data class PartDescriptor(
        val id: String,
        val name: String,
        val midiChannel: Int?,
    )

    private fun parseMetadata(root: XmlElement): MusicXmlMetadata {
        val workTitle = root.child("work")?.childText("work-title")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: root.childText("movement-title")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Untitled"

        val identification = root.child("identification")
        val creators = identification?.childrenNamed("creator").orEmpty()
        val composer = (creators.firstOrNull { it.attributes["type"] == "composer" } ?: creators.firstOrNull())
            ?.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Anonymous"

        val encoding = identification?.child("encoding")
        val date = encoding?.childText("encoding-date")?.trim()?.takeIf { it.isNotEmpty() }
        val software = encoding?.childText("software")?.trim()?.takeIf { it.isNotEmpty() } ?: "Syntona"

        return MusicXmlMetadata(
            title = workTitle,
            composer = composer,
            date = date,
            software = software,
        )
    }

    private fun parsePartList(partListEl: XmlElement?): List<PartDescriptor> {
        if (partListEl == null) return emptyList()
        return partListEl.childrenNamed("score-part").mapIndexed { idx, sp ->
            val id = sp.attributes["id"]?.takeIf { it.isNotBlank() } ?: "P${idx + 1}"
            val name = sp.childText("part-name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: sp.child("score-instrument")?.childText("instrument-name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: sp.childText("part-abbreviation")?.trim()?.takeIf { it.isNotEmpty() }
                ?: id
            val midiChannel = sp.child("midi-instrument")?.childText("midi-channel")?.trim()?.toIntOrNull()
            PartDescriptor(id = id, name = name, midiChannel = midiChannel)
        }
    }

    private fun parseMeasure(measureEl: XmlElement, defaultNumber: Int): MusicXmlMeasure {
        val number = measureEl.attributes["number"]?.trim()?.toIntOrNull() ?: defaultNumber
        var openingAttrs: MusicXmlMeasureAttributes? = null
        var seenMusicContent = false
        val elements = mutableListOf<MusicXmlMeasureElement>()
        var barlineRight: MusicXmlBarStyle? = null

        for (child in measureEl.children) {
            when (child.name) {
                "attributes" -> {
                    val attrs = parseAttributes(child)
                    if (attrs != null) {
                        if (!seenMusicContent) {
                            openingAttrs = mergeAttributes(openingAttrs, attrs)
                        } else {
                            elements.add(MusicXmlMeasureElement.AttributesChange(attrs))
                        }
                    }
                }
                "direction" -> {
                    elements.addAll(parseDirections(child))
                }
                "backup" -> {
                    seenMusicContent = true
                    val dur = parseIntRounded(child.childText("duration")) ?: 0
                    if (dur > 0) {
                        elements.add(MusicXmlMeasureElement.Backup(dur))
                    }
                }
                "forward" -> {
                    seenMusicContent = true
                    val dur = parseIntRounded(child.childText("duration")) ?: 0
                    val voice = child.childText("voice")?.trim()?.toIntOrNull()
                    val staff = child.childText("staff")?.trim()?.toIntOrNull()
                    if (dur > 0) {
                        elements.add(MusicXmlMeasureElement.Forward(dur, voice, staff))
                    }
                }
                "note" -> {
                    seenMusicContent = true
                    parseNote(child)?.let { elements.add(it) }
                }
                "barline" -> {
                    val loc = child.attributes["location"]
                    if (loc == null || loc == "right") {
                        if (child.childText("bar-style")?.trim() == MusicXmlBarStyle.LIGHT_HEAVY.xmlName) {
                            barlineRight = MusicXmlBarStyle.LIGHT_HEAVY
                        }
                    }
                }
            }
        }

        return MusicXmlMeasure(
            number = number,
            attributes = openingAttrs,
            elements = elements,
            barlineRight = barlineRight,
        )
    }

    private fun mergeAttributes(
        first: MusicXmlMeasureAttributes?,
        second: MusicXmlMeasureAttributes,
    ): MusicXmlMeasureAttributes {
        if (first == null) return second
        return MusicXmlMeasureAttributes(
            divisions = second.divisions ?: first.divisions,
            key = second.key ?: first.key,
            time = second.time ?: first.time,
            staves = second.staves ?: first.staves,
            clefs = first.clefs + second.clefs,
        )
    }

    private fun parseAttributes(attrsEl: XmlElement): MusicXmlMeasureAttributes? {
        val divisions = parseIntRounded(attrsEl.childText("divisions"))
        val keyEl = attrsEl.child("key")
        val key = keyEl?.childText("fifths")?.trim()?.toIntOrNull()?.let { fifths ->
            val mode = keyEl.childText("mode")?.trim()?.takeIf { it.isNotEmpty() } ?: "major"
            MusicXmlKey(fifths = fifths, mode = mode)
        }
        val timeEl = attrsEl.child("time")
        val time = if (timeEl != null) {
            val beats = timeEl.childText("beats")?.trim()?.toIntOrNull()
            val beatType = timeEl.childText("beat-type")?.trim()?.toIntOrNull()
            if (beats != null && beatType != null) MusicXmlTime(beats, beatType) else null
        } else {
            null
        }
        val staves = attrsEl.childText("staves")?.trim()?.toIntOrNull()
        val clefs = attrsEl.childrenNamed("clef").mapNotNull { clefEl ->
            val sign = clefEl.childText("sign")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val line = clefEl.childText("line")?.trim()?.toIntOrNull()
                ?: when (sign.uppercase()) {
                    "G" -> 2
                    "F" -> 4
                    "C" -> 3
                    else -> 2
                }
            val number = clefEl.attributes["number"]?.trim()?.toIntOrNull()
            val octaveChange = clefEl.childText("clef-octave-change")?.trim()?.toIntOrNull()
            MusicXmlClef(number = number, sign = sign, line = line, octaveChange = octaveChange)
        }

        val hasAny = divisions != null || key != null || time != null || staves != null || clefs.isNotEmpty()
        return if (hasAny) MusicXmlMeasureAttributes(divisions, key, time, staves, clefs) else null
    }

    private fun parseDirections(dirEl: XmlElement): List<MusicXmlMeasureElement> {
        val out = mutableListOf<MusicXmlMeasureElement>()
        val placement = when (dirEl.attributes["placement"]?.lowercase()) {
            "above" -> MusicXmlPlacement.ABOVE
            "below" -> MusicXmlPlacement.BELOW
            else -> null
        }
        val staff = dirEl.childText("staff")?.trim()?.toIntOrNull()
        val soundTempo = parseIntRounded(dirEl.child("sound")?.attributes?.get("tempo"))
        var emittedTempo = false

        for (dirType in dirEl.childrenNamed("direction-type")) {
            dirType.child("metronome")?.let { metroEl ->
                val beatUnit = parseNoteType(metroEl.childText("beat-unit")) ?: MusicXmlNoteType.QUARTER
                val bpm = parseIntRounded(metroEl.childText("per-minute")) ?: soundTempo
                if (bpm != null && bpm > 0) {
                    out.add(
                        MusicXmlMeasureElement.TempoDirection(
                            bpm = bpm,
                            beatUnit = beatUnit,
                            placement = placement ?: MusicXmlPlacement.ABOVE,
                        )
                    )
                    emittedTempo = true
                }
            }
            dirType.child("dynamics")?.let { dynEl ->
                for (child in dynEl.children) {
                    parseDynamicTag(child.name)?.let { dyn ->
                        out.add(
                            MusicXmlMeasureElement.DynamicDirection(
                                dynamic = dyn,
                                placement = placement ?: MusicXmlPlacement.BELOW,
                                staff = staff,
                            )
                        )
                    }
                }
            }
        }

        if (!emittedTempo && soundTempo != null && soundTempo > 0) {
            out.add(
                MusicXmlMeasureElement.TempoDirection(
                    bpm = soundTempo,
                    beatUnit = MusicXmlNoteType.QUARTER,
                    placement = placement ?: MusicXmlPlacement.ABOVE,
                )
            )
        }
        return out
    }

    private fun parseNote(noteEl: XmlElement): MusicXmlMeasureElement.Note? {
        // Ignore grace notes / cue notes that carry zero rhythmic duration in Syntona's timeline.
        if (noteEl.hasChild("grace")) return null

        val duration = parseIntRounded(noteEl.childText("duration")) ?: return null
        val isChord = noteEl.hasChild("chord")
        val printObject = noteEl.attributes["print-object"]?.lowercase() != "no"
        val restEl = noteEl.child("rest")
        val pitchEl = noteEl.child("pitch")

        val pitch: MusicXmlPitch?
        val isMeasureRest: Boolean
        when {
            restEl != null -> {
                pitch = null
                isMeasureRest = restEl.attributes["measure"]?.lowercase() == "yes"
            }
            pitchEl != null -> {
                val stepStr = pitchEl.childText("step")?.trim()?.uppercase()
                val step = stepStr?.firstOrNull()?.takeIf { it in 'A'..'G' } ?: return null
                val alter = parseIntRounded(pitchEl.childText("alter")) ?: 0
                val octave = pitchEl.childText("octave")?.trim()?.toIntOrNull() ?: return null
                pitch = MusicXmlPitch(step = step, alter = alter, octave = octave)
                isMeasureRest = false
            }
            else -> return null
        }

        val ties = noteEl.childrenNamed("tie").mapNotNull { parseTieType(it.attributes["type"]) }
        val voice = noteEl.childText("voice")?.trim()?.toIntOrNull() ?: 1
        val type = parseNoteType(noteEl.childText("type"))
        val dots = noteEl.childrenNamed("dot").size
        val accidental = parseAccidental(noteEl.childText("accidental"))
        val timeModification = noteEl.child("time-modification")?.let { tm ->
            val actual = tm.childText("actual-notes")?.trim()?.toIntOrNull()
            val normal = tm.childText("normal-notes")?.trim()?.toIntOrNull()
            if (actual != null && actual >= 2 && normal != null && normal >= 1) {
                MusicXmlTimeModification(actual, normal)
            } else {
                null
            }
        }
        val staff = noteEl.childText("staff")?.trim()?.toIntOrNull()

        val beams = linkedMapOf<Int, MusicXmlBeamType>()
        for (beamEl in noteEl.childrenNamed("beam")) {
            val number = beamEl.attributes["number"]?.trim()?.toIntOrNull() ?: 1
            parseBeamType(beamEl.text)?.let { beams[number] = it }
        }

        val tiedNotations = mutableListOf<MusicXmlTieType>()
        val tupletNotations = mutableListOf<MusicXmlTupletNotation>()
        var isStaccato = false
        val inlineDynamics = mutableListOf<MusicXmlInlineDynamic>()

        for (notationsEl in noteEl.childrenNamed("notations")) {
            for (tiedEl in notationsEl.childrenNamed("tied")) {
                parseTieType(tiedEl.attributes["type"])?.let { tiedNotations.add(it) }
            }
            for (tupletEl in notationsEl.childrenNamed("tuplet")) {
                val tType = parseTieType(tupletEl.attributes["type"]) ?: continue
                val num = tupletEl.attributes["number"]?.trim()?.toIntOrNull() ?: 1
                val bracket = when (tupletEl.attributes["bracket"]?.lowercase()) {
                    "yes" -> true
                    "no" -> false
                    else -> null
                }
                val showNumber = tupletEl.attributes["show-number"]?.trim()?.takeIf { it.isNotEmpty() }
                tupletNotations.add(
                    MusicXmlTupletNotation(
                        type = tType,
                        number = num,
                        bracket = bracket,
                        showNumber = showNumber,
                    )
                )
            }
            notationsEl.child("articulations")?.let { artEl ->
                if (artEl.hasChild("staccato")) {
                    isStaccato = true
                }
            }
            for (dynEl in notationsEl.childrenNamed("dynamics")) {
                val dynPlacement = when {
                    dynEl.attributes["placement"]?.lowercase() == "above" -> MusicXmlPlacement.ABOVE
                    dynEl.attributes["placement"]?.lowercase() == "below" -> MusicXmlPlacement.BELOW
                    else -> {
                        val defaultY = dynEl.attributes["default-y"]?.toDoubleOrNull()
                        if (defaultY != null && defaultY > 0.0) MusicXmlPlacement.ABOVE else MusicXmlPlacement.BELOW
                    }
                }
                for (child in dynEl.children) {
                    parseDynamicTag(child.name)?.let { dyn ->
                        inlineDynamics.add(MusicXmlInlineDynamic(dynamic = dyn, placement = dynPlacement))
                    }
                }
            }
        }

        return MusicXmlMeasureElement.Note(
            isChord = isChord,
            pitch = pitch,
            isMeasureRest = isMeasureRest,
            duration = duration,
            ties = ties,
            voice = voice,
            type = type,
            dots = dots,
            accidental = accidental,
            timeModification = timeModification,
            staff = staff,
            beams = beams,
            tiedNotations = tiedNotations,
            tupletNotations = tupletNotations,
            isStaccato = isStaccato,
            printObject = printObject,
            inlineDynamics = inlineDynamics,
        )
    }

    private fun parseIntRounded(raw: String?): Int? =
        raw?.trim()?.toDoubleOrNull()?.roundToInt()

    private fun parseNoteType(raw: String?): MusicXmlNoteType? {
        val norm = raw?.trim()?.lowercase() ?: return null
        return MusicXmlNoteType.entries.firstOrNull { it.xmlName == norm }
    }

    private fun parseAccidental(raw: String?): MusicXmlAccidental? {
        val norm = raw?.trim()?.lowercase() ?: return null
        return MusicXmlAccidental.entries.firstOrNull { it.xmlName == norm }
    }

    private fun parseBeamType(raw: String?): MusicXmlBeamType? {
        val norm = raw?.trim()?.lowercase() ?: return null
        return MusicXmlBeamType.entries.firstOrNull { it.xmlName == norm }
    }

    private fun parseTieType(raw: String?): MusicXmlTieType? {
        val norm = raw?.trim()?.lowercase() ?: return null
        return MusicXmlTieType.entries.firstOrNull { it.xmlName == norm }
    }

    private fun parseDynamicTag(raw: String): MusicXmlDynamic? {
        val norm = raw.trim().lowercase()
        return MusicXmlDynamic.entries.firstOrNull { it.xmlName == norm }
    }
}
