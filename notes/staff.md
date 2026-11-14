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

### Phase ?

Add a "dot" button to the Notes/Rests palette. Pressing 'd' will toggle dot mode.
This can be used to make "dotted" notes, which have 3/2 the duration of an undotted noted.

Implement the = keyboard shortcut for changing the duration of selected notes.
Select some notes.
Press '=' key.
Press a duration key like 'q' and maybe 'd' for an augmentation dot.
The duration of the selected notes will change to the entered value.

### Phase ? - Clefs, Keys
Add other common clefs.
Add ties and beams, repeating measure bars.


### Phase ? - Copy/Paste
Add support for copying and pasting selected notes.
Allow copying and pasting between score modules.

### Phase ? - Multiple Instruments
Add support for multiple instruments.
Add new instruments with a "+" button.
Each instrument has its own voice output jack.

### Phase ? - Export
Add MusicXML support. This will allow us to export a score to Notion or other notation editors.

### Phase ?
Add score modifications for selected notes. Include transpose, retrograde,
increase duration 2*, decrease duration 0.5x,

### Phase ?
Add graphical scores, custom glyphs, textual scores.

### Phase ?
Add M2-123-UM compatible articulations.

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

* Add advanced editing such as retrograde inversion, transposition chromatic and in-key.
* Allow editing of contours and other parameters in time with the notes.
* Add editing note parameters and articulations.
* Measure lines for multiple instruments can be synced or not to support polyrhythms or independent accelerandi.
* Per Note Controller editing for MIDI 2.0.
* Loop repeatedly on a section and enter notes using live transcription. Ideally save comps like in Logic Pro.
