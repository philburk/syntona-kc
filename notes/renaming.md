# Renaming

## Massive renaming on August 29, 2026

We ware changing the names of some modules and some jacks.
Try to maintain backwards compatibility for the old JSON files.

Change the ksyn modules that have an 'a' prefix to have a '\~' suffix.
The rule is that we use the tilda to distinguish audio from regular modules
when the name is ambiguous.
For example, change "a+" to "+~", and "aMin" to "min~".
Also change "ksyn.effects.delay" to "ksyn.effects.delay~"
and "ksyn.control.select" to "ksyn.control.select~",
and "aRanger" to "range~",
and "ap2f" to "pitchToHz~".

Also change some control rate module names.
Change "p2f" to "pitchToHz".

Instead of SchmidtTrigger, use KSyn's new SchmittTrigger.

Change "choose4" to "select4", "choose8" to "select8", "choose16" to "select16".

### Also change some jack names.

Change the Randome "value" jack to "out".

