# Staff Notation Editor

## Goals
* Add a somewhat traditional staff notation editor to the Syntona dataflow graph.
* Be able to compose complex pieces and control Syntona synthesizer voices.
* Add UI features missing from the Notion app from Presonus.

## Basic Design

Add a staff notation module similar to the Piano Roll editor except it uses traditional staff notation.

Support multiple instruments. Each instrument has its own voice output jack.

Provide feedback for the user when moving notes.
* Play the note when it moves.
* Display the note name and octave, eg. "F#4".

Can be used as an alternative display mode for a MIDI track in a timeline. PianoRoll/Staff/Events

## Implementation Stages

1. Add a note "on/off" or "play" or just NOTE with a duration field. Can also use the duration to implement ramps for control changes.
1. Single staff with a key signature and time signature, basic notes and rests. Display pitch while editing. Play all simultaneous notes.
1. Different clefs
1. Add ties and beams, repeating measure bars.
1. Add support for multiple instruments.
1. Add M2-123-UM compatible articulations.

## Design Considerations

Should note duration be added to the Syntona Voice Message?

A staccato note sounds different right from the start. It is not just a regular note that ends early.

Pizzicato is required. Should articulations be implemented using:
* a new parameter in the ChannelVoiceMessage
* a separate output
* a separate channel

What are the new articulations defined for the Orchestra Profile in MIDI 2.0?

## Advanced Features

* Add advanced editing such as retrograde inversion, transposition chromatic and in-key.
* Allow editing of contours and other parameters in time with the notes.
* Add editing note parameters and articulations.
* Measure lines for multiple instruments can be synced or not to support polyrhythms or independent accelerandi.
* Per Note Controller editing for MIDI 2.0.
