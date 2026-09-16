# Extend TimeLine and BeatLine modules with Tracks

Note, in this document, when I refer to a "timeLine" I am also referring to a "beatLine".

Organizing music into multiple parallel tracks, like in Logic Pro, is a useful model.
But unlike in a traditional DAW, the Syntona timeLine will be in the context of the dataflow model.
We can add several track types to the timeLine module.

## Adding Tracks

A "+Track" button will appear in the toolbar of the timeline.
When I click it I will get
## Common Track Features

Almost every track will have an "in" and an "out" jack of different types.
At the left side of each track will be four buttons "M", "S", "R" and "I".
They will behave like the Mute, Solo, Record, and Monitor buttons in Logic Pro.

Each track can have recorded sections that look like the sections on a DAW.
I can drag each section by its header.
I can also drag the right edge. At the top of the right edge I can add copies of the original section.
At the bottom of the left edge I can change the duration of the section.

When working in the beatLine the dragging operations of the regions
and the region durations should snap to the smallest visible beat subdivisions.

## Patch Track
This track is backed by a single patch with intrinsic "start" and "stop" input jacks.
The user can add a regular "output" module to the patch, which will appear at the right edge of the timeline.
Or they might add an audio "output~" or "output2~".
When I "Edit" this track the patch window opens and I can see the "start" and "stop" and output jacks.
The Mute button on the track will disable new start messages being sent to the patch
and will send a stop message to the patch. If unmuted while the time indicator is over an
active region for the patch then a start message will be sent to the patch.

## Message Track

A message track records event-type messages with double precision values.
They are recorded and can play through the "out" jack at the right of the track.
These might be messages from a fader or an algorithm.
When editing this track the user is shown an array of columns where each column height
shows the value of the message. I can drag the mouse over the columns to change the values.
The Mute button on the track will disable the outgoing messages.

## MIDI Track

A MIDI track is similar to the Message Track but records and plays back VoiceMessages like from a MIDI port.
When I edit that track I get a PianoRoll view of the MIDI NoteOn and NoteOff messages.
The Mute button on the track will disable the outgoing messages except for NOTE_OFF.

## Audio Track

The audio track has an audio in and out jack. It records audio into a SegmentedFloatArray.
We will worry about the editing later.
When we save a patch containing a timeLine with audio tracks, the audio will be saved in a WAV file
so that the JSON file will not be huge. The audio will be saved in a folder called "audio"
next to the .sntn file being saved.

## Design Issues

