# Export Instrument Source Code

## Overview

We want to use Syntona as an instrument design tool.
We want to be able to design and test instrument and other sounds
in Syntona and them export them as KSyn and JSyn or JSyn source.

KSyn instruments will be written in Kotlin and use the KSyn API.
JSyn instruments will be written in Java and use the JSyn API.

The UI will have an Export item in the File menu that can be used to export patches.
It will have these submenus:
* Source code for KSyn...
* Source code for JSyn...

The menu will be disabled if the patch is not in front or if the patch is a root patch.

## Details

If exporting a polyPatch then write the source code for a class that
subclasses Circuit and implements PitchedVoice.

If exporting a regular patch that then just subclass the Circuit
and add ports for any AudioInputModules or AudioInputModules.

For any KSyn module in the patch, figure out the underlying KSyn UnitGen and
create an instance of that unit in the circuit.

In the init{} method connect the modules as they are connected in the Syntona patch.
Also set the min/value/max settings for the port based on the Syntona jack settings.
Set the isValueAdded() flag if set in Syntona.

Look at the KSyn circuit com.softsynth.ksyn.instruments.MinimalSawVoice.
It is an example of what would be generated if we exported the Syntona
resource "instruments/minimalSaw".

If there are non-KSyn modules in the patch then display a warning and ask the user if they want to proceed anyway.
If proceeding then ignore the non-KSyn modules.
You can always ignore knob and fader modules without giving a warning.

Note that Syntona uses KSyn for its synthesis engine. But KSyn is a fairly
direct port of JSyn to Kotlin. So the modules should be equivalent.
Example JSyn instruments can be found in "jsyn/src/main/java/com/jsyn/instruments".
Note that JSyn instruments are based on UnitVoice, which used a frequency port instead of a pitch port.
So JSyn instruments do not need a PitchToFrequency unitgen.
JSyn instruments also cannot do pitch modulation, which is why KSyn switched to pitch based control.
