# Renaming

## Massive renaming on August 30, 2026

Let's change the names of some modules and some jacks.
Try to maintain backwards compatibility for the old JSON files.

Change the ksyn modules that have an 'a' prefix to have a '\~' suffix.
The rule is that we use the tilda to distinguish audio from regular modules
when the name is ambiguous.
* For example, change "a+" to "+~", and "aMin" to "min~".
* Also change "ksyn.effects.delay" to "ksyn.effects.delay~"
* and "ksyn.control.select" to "ksyn.control.select~",
* and "aRanger" to "range~",
* and "ap2f" to "pitchToHz~".

Also change some control rate module names.

Change "choose4" to "select4", "choose8" to "select8", "choose16" to "select16".

* Change "p2f" to "pitchToHz".
* Change "DAHDSR" to "envDAHDSR".
* Change "ckDiv" to "clockDiv".
* Change "latchZC" to "latchZeroX".
* Change "aInput" to "input~".
* Change "aOutput" to "output~".
* Change "loadKick" to "onLoad".
* Change "recSeq" to "recursiveSeq".
* Change "arm" to "oneShot".
* Change "gate" to "pass".

### Also change some jack names.

* Change the RandomModule "value" jack to "out".
* Change CounterModule "inc" jack to "kick".
* Change samplePlayer.rate to samplePlayer.speed
* Change sampleReader.rate to sampleReader.speed
* Change sampleReader.loop to sampleReader.looping and make it an IntegerJack.
* Change any jack named "rate(hz)" to "rateHz".
* Change any jack named "delay(ms)" to "delayMs".

Change any jack named "input*" to "in*". Same for output.
For KSyn units coerce ports named "input*" to "in*".

After completing these changes, make sure all the
example, tutorial and help resources still load without error.
I think there is a unit test for that.

