# SyntonaKC Design Document

This document outlines the architecture, data models, and UI components for the SyntonaKC music application.

## Goals

* Create music more easily.
* Avoid the mental constraints of track based DAWs.
* Allow high and low level composition. Integrate sound design and composition.
* Incorporate ideas from HMSL
* Support scripting by the user but not require it.
* Multi-modal inputs: MIDI, drawing, audio, code, gestures, editing, staff notation…
* Support for standards: MIDI 2.0, Music XML
* Support live performance as well as composition.

## Requirements

* Use Kotlin/Compose for WASM
* Use KSyn as audio synthesizer
* Web based or desktop
* Possible Android port for tablets or big-screen
* Leverage AI in development and execution
* Extensible modular architecture for future growth
* Support integration with a native VST3 host (via ethernet?) (Maybe)
* Allow control of DAW like Logic and capture for mastering.
* Simple integration with downloadable sounds. DLS2?
* Flexible tempo control, support polyrhythms, tempo hierarchy. Scaling or independent tempi.
* Support UMP/Ethernet

## Style Requirements

* There must be NO trailing whitespace in Kotlin code or MarkDown files.

## High Level Editors

Almost any editor can be the top level editor except maybe the gesture editor. Or maybe the Data Flow editor should be the top level.

### Hierarchy Editor

Parallel and Sequential Collection editor, show nested patches

#### Questions

Should startDelays and stopDelays be shown? On the one hand, I like that this editor is purely relational and allows unknown durations. An actual timeline requires fixed durations. But clearly that is a useful and ubiquitous view when scheduling musical events.

### Gesture Editors

* Capture shapes from a mouse or a knob, or microphone.
* XY input like Squiggle
* Y/Time like an envelope or other time based function.
* Input can be segmented or continuous. Internally always segmented.
* Multi-dimensional shapes? Like Shape Editor from HMSL?
* Very simple automation editing.
* Can operate on Shapes independent of time or meaning.

### Staff Notation Editor

* For composition with articulations.
* Use MIDI 2.0 orchestra profile.
* Support N-tuplets
* Standard or modernized mode, whatever that is.

### Track Editor

* Can be a leaf in the hierarchy. Or a top level Parallel collection.
* Time can be linear or relative.

### Wormholes

* Communication between any points of the composition.
* Maybe dangerous from a software architecture standpoint. Spaghetti code\!

### Data Flow

* Module data flow editor like original Syntona.
* Modules for composition
  * Delay \- add time to timestamp
  * Sync
  * AllDone \- wait for all inputs to be done, then reset
  * Merge timestamp from one port and message from another port

Add a module that can execute a script. (ECMA). Input and output ports are declared in the script and appear as connectable data flow ports. Specify whether the script runs at audio rate or compose/event time.

#### Wire Behavior

* If a Wire is clicked on then it should be deleted.
* If an OutputJack with no Wire is clicked then a rubber-band line should track the pointer.
  If the pointer is released over an InputJack then a connection should be made and a Wire should be drawn.
* If an InputJack with a Wire connected is clicked then the first connection should be disconnected from the InputJack
  and a rubber-band line to the connected OutputJack should be drawn.
  If the pointer is released over an InputJack then a connection should be made and a Wire should be drawn.

### Harmonic Progression Editor

Useful but maybe mentally constraining

### Audio Waveform Editor

Like Audacity with elements from ListenUp
Edit captured audio from other parts of Syntona.
Musique concrète techniques, rhythmic splicing, tempo integration

### Clocks and Coroutines

The graph traversal should take place within a Coroutine. This will separate it from the UI thread.
This will allow us to have autonomous event generators such as Clocks that will run in the DataFlow Coroutine.

Communication from the UI to the dataflow Coroutine should be through SyntonaCommands that are passed to the
dataflow Coroutine through a Kotlin Channel.

The dataflow graph traversal Coroutine should sleep while waiting for messages from the UI or
for scheduled events to become ready. This can be implemented using a Kotlin Channel for passing commands,
and the select<Unit> feature with onTimeout().

A SyntonaCommand has two methods, execute() and undo(). When a command is received it is executed
and placed in the undo stack. If the users asks for an UnDO then it pops commands off the stack
and calls undo().

SyntonaCommand subclasses can include:
* KickModule
* AddModule(bundle, type, x, y)
* PassMessage, set values by sending message, but what about Kickable jacks?
* ConnectJacksCommand()
* DisconnectJacksCommand()

Modules that will use the ScheduledQueue include:
* Clock - generates periodic kicks
* AddTime (Delay) - advances timestamp and schedules the output message

### Notes and Voice ALlocation

We need a way to pass audio signals into and out of a patch just like we did with regular NoteMessages.
We can define an "aInput" module that can be added to a patch.
That will result in a "aIn" jack being added to the patch module in the parent.
Similarly for aOutput.

Internally, the aInput module should instantiate a KSyn PassThrough unit generator.
This will act as a tie point where incoming signals will get summed and then distributed
to any jacks connected within the patch.

## Common Elements

It should be possible to navigate within the hierarchy

### Composables needs specific ports

High level messages are all timestamped.

#### Inputs

* Start \- begin processing with a timestamp
* Pause \- stop but do not reset time
* Resume \- from current time
* Stop \- stop

Or are pause/resume global?

#### Outputs

* Done \- when collection has finished last repetition

### Common Iconography

* Open for Editing
* Close Editor
* Full Screen
* Back (up the hierarchy? or back like a browser?
* Drag an alias
* Select for Copy/Paste
* Rename

## API

The term Composable conflicts with Kotlin “@Composable” attribute.

Is there a difference between time within a composition and ongoing real-time in a performance?

#### Playable Interface

* `play(time: Timestamp)`
* `chase(time: Timestamp) // chase quickly to specified time, for play cursor`
* `stop(time)`
* `openEditor()`
* `closeEditor()`
* `freeze()`
* `unfreeze()`
* `save(context) // for save to file or copy/paste`
* `load(context)`
* `addChild(playable, time, modifier)`
* `insertChildBefore(playable, other, modifier)`
* `removeChild()`
* `getChildren(): List<Playable>`
* `getParent(): Playable`
* `createAlias()`
* `getAudioOutputs() // for mixing`
* `getAudioInputs()`
* `getDisplayable() // something to show in a higher level editor when closed   (getComposable()?)`
* `isDirty()`

Can this be broken into smaller interfaces?

## User Journeys

User Intention
No UI defined yet

1. Define an overall structure of A-B-A’. The A’ section is an alias of the A section so that changes made to A are reflected in A’. But A’ is not the same as A because of Modifiers that are applied.
2. Specify an arc of intensity across the piece with a slow ramp up and a rapid fall at the end.
3. Define a key of D for the A and B sections. Transpose the A’ section by a whole tone to E.
4. Define a short leitmotif for the entire piece using the staff notation editor.
5. Define a second leitmotif for the B section only.
6. Design an abstract droney sound using modules that introduces the pieces and fades out as the A section begins.
7. Create a rhythmic pattern for the A section using a drum pattern editor. The Drum sounds are from a library of synthetic and sampled sounds.
8. Algorithmically generate melodies for the A section (and A’) based on the scored leitmotif. The algorithm uses the “intensity” gesture and the key as inputs. The generated notes are rendered into an editable staff notation section.
9. Algorithmically generate a harmonic accompaniment based on the melody.
10. Create a custom keyboard sound for the harmonies using a polyphonic data flow editor.
11. Add some interesting complex sounds triggered at specific moments in the timeline.
12. Record some short guitar parts that are processed using the data flow editor. The guitar parts are tied to points in the melody track and follow that point if measures are inserted in the melody.

Issues/Questions:

* If the melody changes then the harmony part may need to change. But the guitar parts may have been based on the harmonies. We may need a way to lock an algorithmic track, (or just render it).
* Think about the difference between a “live” algorithmic track that keeps changing and a “rendered” track that is frozen.
* What is the top level? A Hierarchy Editor, a Data Flow patch containing a hierarchy?

Playable vs Performable
Should there be a difference?

| Playable | Performable |
| ----- | ----- |
| Editable in non-real-time | Playable like an instrument (capture if in Record mode) |
| Only Playable | Can convert to Playable |

Editors supporting Performance/Recording

* Staff (transcribe MIDI or keys)
* Data Flow captures knobs turning, button presses, external MIDI?
* Audio recording
* Gesture Editors (relation between gestures and data flow automation?)

What is the Difference Between Syntona and other DAWs

| Logic, Bitwig | Syntona |
| ----- | ----- |
| $100s | Free |
| Desktop | Desktop or Web |
| Track based | composable hierarchy, tracks can be embedded in hierarchy |
| Editable | Scriptable |
| Commercially oriented | More experimental, research, pedagogy |
| Global tempo, key, etc | Hierarchy of Tempi, Keys, Time Signatures |
| Closed source | Open source |
|  | Samples and Sequences can be queued/triggered |
|  |  |

# Implementation Plan

1. Setup GitHub repo syntona-kc
2. Create multiplatform app project
3. Create package hierarchy with READMEs so I can merge the folders.
4. Define interfaces
5. Implement App Framework
6. Implement Hierarchy editor
7. Implement Save, Load (JSON)
8. Integrate simple dataflow editor and some units, Hierarchy Module
9. Add MultiTrack module with Audio
10. Add Sequence Tracks
11. Add a Track that can contain Playables aligned with audio and sequences
12. Implement piano roll editor
13. Implement staff notation editor
14. Implement waveform editor
15. Implement Relative Intonation Editor

### Architecture
- **Core Model:** Using `ScoreTime` (Long nanoseconds) for high-resolution scheduling.
- **Data Flow:** Modules connected via Jacks, passing `Message` objects.
- **UI:** Compose Multiplatform (Desktop, Android, Web).

### Data Types
- `ScoreTime`: Value class wrapping a Long.
- `Message`: Sealed class for graph communication.

### Modules & Graphs
(Details to be added)
