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

## Advanced Features

* Add advanced editing such as retrograde inversion, transposition chromatic and in-key.
* Allow editing of contours and other parameters in time with the notes.
* Add editing note parameters and articulations.

## Design Considerations

Should note duration be added to the Syntona Voice Message?

A staccato note sounds different right from the start. It is not just a regular note that ends early.

Pizzicato is required. Should articulations be implemented using:
* a new parameter in the ChannelVoiceMessage
* a separate output
* a separate channel

