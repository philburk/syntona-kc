# Staff Notation Editor

## Goals
* Add a somewhat traditional staff notation editor to the Syntona dataflow graph.
* Be able to compose complex pieces and control Syntona synthesizer voices.
* Add UI features missing from the Notion app from Presonus.
* Incorporate multiple notational systems into a single document with synchronized playback.
* Include graphical and textual score elements.
* Export to MusicXML and MIDI file.
* Generate PDFs with combined staff, graphical and text notation.

## Basic Design

Add a staff notation module called "score" similar to the Piano Roll editor except it uses traditional staff notation.

Support multiple instruments. Add new instruments with a "+" button.
Each instrument has its own voice output jack.

UI for placing a note.
* When the user clicks down on an empty part of the staff, add a note with the current duration, eg. quarter note.
* Play the note and display the note name and pitch number, eg. "C#, 60"
* If the user drags up or down, change the pitch of the note play the new note, and update the name and value display.

UI for moving a note.
* If the user clicks down on a note, play the note, highlight the note and display the name and pitch value.
* If the user drags up or down, change the pitch of the note play the new note, and update the name and value display.

When in note entry mode, the mouse cursor should be a symbol for the note or rest of the current duration.

If a note of a different duration is placed on top of an existing note then the old note will be replaced.

If the notes in a measure exceed the duration of a measure then highlight those notes in red.
For example the fifth quarter note in a 4:4 measure would be red.

### Keyboard Shortcuts

ESC = enter selection mode

DELETE or BACKSPACE = delete selected items

Note or rest durations, hitting the key a second time toggles between notes and rests.
t = thirty-second
s = sixteenth
e = eighth
q = quarter
h = half
w = whole

1 = toggle staccato, implement as a short note, ie a quick on/off
2 = flat
3 = sharp
4 = natural

## Code Organization

The basic model should be shared with the Piano Roll editor and the TimelineModule MIDI track.
Can be used as an alternative display mode for a MIDI track in a timeline. PianoRoll/Staff/Events

We may want to start a new package for this staff
editor because it may get a lot bigger in the later phases.

The rendering should be through a shared drawing abstraction (ScoreDrawTarget):
If the staff renderer draws through a thin interface (lines, filled rects, cubic Beziers
for slurs/ties, and glyphs/text) implemented by both Compose DrawScope (screen)
and a PDF backend (export), the printed score matches the screen layout at infinite vector resolution.

## Implementation Stages

### Phase 1
Add "score" module under the music.compose bundle.
Display a single stave with a key signature and time signature, and a few notes with different
durations of notes and rests.
Draw glyphs using a SMuFL font.

### Phase 2
Add basic note entry and editing. Add or drag notes.
Use keyboard shortcuts from above to select note or rest durations.
Display pitch while editing.
Allow selection and deleting of notes and rests.

### Phase 3
Create a panel at the left edge of the score editor that contains buttons that cause palettes to popup.
Move the current selector buttons for the notes and rests into a popup palette called "Notes/Rests"
The palette should show the glyphs and their keyboard shortcuts.
Later we will add other palettes.

Add a horizontal scroll bar so that I can scroll to any measure of a long score.
This would be like the pianoRoll scroll bar.

### Phase 4
In selection mode, clicking in an empty measure, or between notes, will place a vertical cursor.

Add a MIDI Voice input jack to the score.
Add an icon that enables "step record" mode.

When a MIDI note is played into the score's input jack, insert that note
into the score at the cursor position with the current duration, eg. eighth note.

Pressing ESC will exit step record mode.

Pressing a note duration key, like 'q', will change the current duration
but leave us in step record mode.

### Phase 5 - Key Signatures
Add a palette for setting the key for a selected measure. If multiple measures selected,
set key for first measure and remove any key signatures from later selected measures.
Do not change any note pitch values when changing the key.
Name the button that opens the palette "Key".
There are no keyboard shortcuts for key signatures.

### Phase 6 - Chords
Add support for chords by allowing the user to add notes vertically aligned with existing notes.
When clicking on a note that is in a chord, try to display the name of the chord based on the notes and
the key signature, eg. "C#m7".

### Phase 7 - Time Signature
If the user clicks on a Time Signature, open a text box with the current time signature in a format like "3/4".
Let the user edit that signature and hit enter or click outside the text box.
If the user clears the text box then remove the time signature unless it is for the first measure.

Add some Time Signatures to the Keys Palette like 3/4 2/2 4/4 6/8 12/8 . (Any others?)
When clicked, insert or change the time signature of the first selected measure.

### Phase 8 - Augmented Dot

Add a "dot" button to the Notes/Rests palette. Pressing 'd' will toggle dot mode.
This can be used to make "dotted" notes, which have 3/2 the duration of an undotted noted.

Implement the '=' keyboard shortcut for changing the duration of selected notes.
Steps:
Select some notes.
Press '=' key.
Press a duration key like 'q' and maybe 'd' for an augmentation dot.
The duration of the selected notes will change to the entered value.

### Phase 9 - Clefs
Add other common clefs. Add a Clef palette that works similar to the Key Signature
editing except clefs can occur in the middle of a measure.

How Mid-Stave Clef Changes Work
Size: A mid-stave clef is drawn slightly smaller than the primary clef at the start
of a system (typically about 65–75% of full size) so it doesn't get confused with a system break.

Placement:
Before the barline: If the clef changes on beat 1 of a new measure, place the new
clef immediately before the barline at the end of the preceding measure.

Mid-measure: If the change happens partway through a bar, place the new clef
immediately before the first note that uses it.

Before key/time signatures: If a clef change coincides with a new key or
time signature at a double barline, the order is: Clef → Barline → Key Signature → Time Signature.

### Phase 10 - Copy/Paste
Add support for copying and pasting selected notes.
Allow copying and pasting between score modules.

### Phase 11 - Dynamics
Add a Dynamics palette that contains mf, ff and the other common dynamic marks.
Selecting one changes the cursor to that dynamic.
Clicking above or below a stave will add the dynamic to the score.
Notes following the dynamic will have the dynamic applied to
their amplitude in a musically appropriate way.

Keyboard shortcut:
Pressing 'p' will select pianissimo (one p). Pressing p again will select pp and so on up to pppp.
After that it will rotate back to mp.
Pressing 'f' will do the same for forte.
SHIFT+P will select mezzo-pianissimo (mp)
SHIFT+F will select mezzo-forte (mf)

To delete a dynamic marking, click on it select it then press DELETE or BACKSPACE.

Dynamics can be copied and pasted.

### Phase 12 - Play from Cursor

When Playback is started using the play button or space bar, play from the cursor position.
Add a Rewind button next to the Play button that positions the cursor at the beginning.
When playback is initiated from the "Start" jack, start at the beginning.

### Phase 13 - Beams

Add beams. Beams do not change the playback.

Note with flags should automatically be beamed together in a standard way.
But the user may want to override the automatic beaming.
Pressing SHIFT+B will enter beaming mode.
When the user clicks on a note it will be beamed with the following note.
If the user clicks on a note that is currently beamed then it will un-beam that note.

### Phase 14 - Reorganize

Move the Flat, Sharp and Natural buttons into the Notes palette under the category "Accidentals".
Move the Staccato button into the Notes palette under "Articulations".
Move the Beam button into the Notes palette under "Tools".

### Phase 15 - Ties

Ties will combine note durations and is often used to hold a note across a measure boundary.

Pressing SHIFT+T will enter Tie mode.
Click on a note with the Tie cursor to make it tie to the next note.

The UI behavior of tying and untying is similar to the beaming and un-beaming behavior.

### Phase 16 - Multiple Instruments
Add support for multiple instruments.
Add new instruments with a "+" button.
After clicking a +, a new instrument dialog will appear with some options.

One can enter a note range. The default will be the range of an 88 key piano.
This will help composers avoid scoring notes that are impossible to play on, for example, a violin.
Notes that are out of range can be entered but will display in red.

The note range will determine the default clef and whether one or two staves are needed.

One can enter an instrument type. This will determine the articulations available, for example pizzicato.
(We don't currently support pizzicato but will some day.)
Types are: plucked strings, bowed string, brass, woodwind, keyboards, percussive, electronic.

Presets are: violin, viola, cello, guitar, piano, trumpet

Each instrument should have its own voice output jack.

### Phase 17 - Tuplets

We need to be able to enter tuplets as an arbitrary N/M ratio.
Provide presets for common tuplets (3:2, 5:4, 6:4, 7:4, 9:8, 11:8).

Enter a tuplet by selecting the base duration, for example a quarter note, or an eighth rest.
Then press ALT+N to enter a triplet mode.
Use 2 for duplet, 3 for triplet, 5 for quintuplet, etc.
If ALT+N is entered then put up a small dialog and allow the user to Enter a N/M ratio.
Display as:   Fit |__| into |__|

When in tuplet entry mode display the tuplet bar with the current ratio.
The tuplet bar should be orange if it is not full.
New notes under the bar will be added to the tuplet.
When it is full, change the tuplet bar to black.

Allow one to enter tuplets in more than one measure at a time.

Engraving / Display Rules:
Number display: Show just the top number (3, 5, 11) by default for standard
ratios (3:2, 5:4, 6:4, 7:4, 9:8, 11:8), and display the full ratio (4:3, 5:3,
7:6) when the denominator isn't obvious from the meter.

Brackets: Omit the tuplet bracket when all notes in the tuplet are connected
by a single unbroken beam; draw the bracket whenever the tuplet contains rests,
mixed beams, or unbeamed quarter/half notes.

### Phase 18 - Transposition
I want to be able to transpose groups of notes across multiple staves.
UI:
Select a group of notes.
Press UP or DOWN arrow keys on keyboard to transpose the notes within the current key.

If the SHIFT key is pressed while UP or DOWN are pressed then transpose the notes by single semitones.
The notes may end up outside the current key.

### Phase 19 - MusicXML Export
Add a "File" menu at the far left of the Score toolbar.

Put all the MusicXML specific code in a com.syntona.util.musicxml package so we can copy it to other applications or libraries.
Try to avoid dependencies on the rest of Syntona in the MusicXML code.

Phase A - Add a File menu item: "Export MusicXML...".
This will allow us to export a score to Notion or other notation editors.
Default values are Title = "Untitled", Composer="Anonymous" and Date={todays date}.

Phase B - Add a File menu item: "Import MusicXML...".
This will allow us to import a score from Notion or other notation editors.
Features and articulations that are not supported should be ignored.

Do round trip testing by exporting a score and then importing it.

### Phase 20 - Edit Menu

Add an Edit menu next to the File menu in the Score.
Add an item in the Edit menu "Fill with Rests".
When selected, fill any measure that is not already full with rests
For example, if a 4:4 measure has 3 quarter notes, add a quarter note rest.

### Phase 21 - Editing Notes and Rests
When deleting a note, replace with a rest.
Leave the rest selected so that it can be easily deleted.
When deleting a rest, move the following note to the rest's position.

Add a Score Editor Edit menu option called "Auto Fill Rests" that can be toggled.
The default should be on.
When enabled, fill any measure that is not already full with rests.
When adding notes, keep the measure full of rests.
Start with smaller rests that align with the current position and then fill with larger rests.

### Phase 22 - Improve note alignment

Note alignment needs improvement. For example, if I have two staves. one with lots of sixteenth notes,
and one with quarter notes, the notes are not vertically aligned in time.
See the attached PNG screenshots.
The first one is from MuseScore, which does a good job.
The second is from Syntona, which has the layout problem.

Here is a video that describes the Blostein / Haken music justification algorithm.
https://youtu.be/0korQyrnH8o?si=qQjnxwsHgQl76BZZ

This paper suggests some improvements.
https://guido.grame.fr/papers/renz-spacing.pdf

There may be other algorithms that I don't know about.

### Phase 23 - Looping
Add a looping bar at the top for selecting one or more contiguous measures in a score.
This is similar to the looping feature in the timeline module.
Also add a "Loop" mode toggle button to the Score toolbar.
If loop mode is active, when the playback cursor hits the end of the looping section
it will rewind to the beginning of the looping section.

## TODO

### Phase ? - Measure Lines
Add support for typical measure lines.
Put the select buttons for measure lines into a palette called "Measure Lines".

### Phase ? - Real Time Entry
Add support for transcribing notes in real time into a stave while playing back on other staves.
Transcribe the notes into a score. Try to detect dotted notes, and triplets.
Quantize the note timing.
Provide a way to specify the audio latency, which must be subtracted from the recorded note timing.
(What if the performer is already compensating for the latency by playing the notes a little early?!)

Add a red "R" button that works like the "R" button in the TimelineModule.
Place it in the left control area of the instrument, near the instrument name.
Use that button to determine which stave will receive the incoming notes.
Only one stave can be selected at a time.

Allow editing and removal of notes while looping. This will allow the user to
play some notes in real time and then adjust them while continuing to loop.


### Phase ? - Articulations
Add support for articulations such as pizzicato, mute, sustain pedal, and
2 or 3 other very common articulations.
Add M2-123-UM compatible articulations.

### Phase ? - Document Name, Composer, Date and other fields

Allow the user to enter the score name, composer and a date.

### Phase ?
Add score modifications for selected notes. Include transpose, retrograde,
increase duration 2*, decrease duration 0.5x,

### Phase ?
Add graphical staves, custom glyphs, text.

### Phase ? - Save to PDF, All Together or as Parts HARDER THAN MusicXML

## TODO
Make sure the score editor window opens high enough to see the palettes.
Switch between Presonus and MuseScore key mappings.
Add staccato articulation to the tutorial.

## BUG


## Design Considerations

Pizzicato is required. Should articulations be implemented using:
* a new parameter in the ChannelVoiceMessage
* a separate output
* a separate channel

What are the new articulations defined for the Orchestra Profile in MIDI 2.0?

We could have a separate package for the MIDI 2.0 articulations in preparation for eventually
outputting MIDI 2.0 notes directly.
Also maybe some day we can get note durations back in the MIDI 2.0 standard.

## Advanced Features

* Loop repeatedly on a section and enter notes using live transcription. Ideally save comps like in Logic Pro.
* Add advanced editing such as retrograde inversion.
* Allow editing of contours and other parameters in time with the notes.
* Add editing note parameters and articulations.
* Measure lines for multiple instruments can be synced or not to support polyrhythms or independent accelerandi.
* Per Note Controller editing for MIDI 2.0.
