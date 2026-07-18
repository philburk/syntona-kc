# Prompts for Claude Code

## Add Scope module
```
The ksyn-compose package has a new Oscilloscope.
There are examples of it being used in ChebyshevSongScreen.kt.

Add a ScopeModule to Syntona that uses a ScopeProbe with two channels.
The interior of the module should be filled with a 200 pixel high and 300 pixel wide scope view.
Put the "scope" module in a new "ksyn/view" bundle.
```

## DataQueue

Add new modules for the KSyn VariableRateMonoReader and VariableRateMonoWriter.
They should be called "monoRdr" and "monoWtr" and placed in a new "ksyn/data" bundle.
Both unit generators have a UnitDataQueuePort which will require a new type of Syntona jack.
The data queue should be colored in yellow.

Also add a module called "segEnv" that contains a SegmentedEnvelope class.
It should also be placed in the new "ksyn/data" bundle.
It should have three Button jacks called "queueOn", "queueOff" and "queue".
It should also have an "output" jack.
When a button is pressed it should send a message that contains the SegmentedEnvelope and an opcode.
When the message is received at the "dataQueue" jack on the monoRdr module it should call the corresponding
queueOn(envelope), queueOff(envelope) or queue(envelope) methods on the VariableRateMonoReader dataQueue port.

The segmented envelope should be initialized as follows.

    val envData = doubleArrayOf(
        0.02, 0.9, // duration,value pair 0, "attack"
        0.10, 0.5, // pair 1, "decay"
        0.50, 0.0  // pair 2, "release"
        )
    envelope = SegmentedEnvelope( envData )
    // Hang at end of decay segment to provide a "sustain" segment.
    // Note End point is one past the last index.
    envelope.sustainBegin = 2
    envelope.sustainEnd = 2

When the user clicks on the segEnv module header it should open a
window that contains a ksyn-compose SegmentedEnvelopeEditor for editing the envelope.

## beatClock

Add a new module called "beatClock". It is similar to the "clock" module except it has
a "tempo" input which is in units of beats/minute. The beatClock also has the option
of outputing message every beat or every half beat or at other divisions including
3/8, 1/4, 3/16, 1/8, 1/16, 1/32, 1/3, 1/6, 1/12, 1/5, 1/10.
These divisions can be selected using a menu in the center of the module. The current division is visible.
When generating the outputs by dividing the master clock, carry over the
remainders so that beatClock modules set to different divisions will not drift relative to each other.
