**Module Specification: Stepper**

**Overview**
The Stepper is a 0-based, bounded iterator designed for sequencing and logic routing within the Syntona dataflow graph. It utilizes an internal "Run/Dormant" state machine to eliminate race conditions and double-triggering when chaining modules or sharing master clocks.

**Input Jacks**

* **Start:** Wakes the module. Sets the internal state to "Running", instantly sets the index to `0`, and immediately outputs `0` from the Index jack.
* **Kick:** The main clock input. Advances the stepper. This input is entirely ignored if the module is in the "Dormant" state.
* **Length:** Determines the sequence length (formerly "N"). The stepper counts from `0` up to `Length-1`.
* **Loop (Optional UI Toggle or Jack):** A boolean state determining whether the Stepper repeats or stops upon completion.

**Output Jacks**

* **Index:** Outputs the current integer step (`0` through `N-1`) into the dataflow graph.
* **Done:** Outputs a discrete kick message exactly when the final step has completed its rhythmic duration.

**Execution Logic & State Machine**

1. **Initialization:** The module begins in a "Dormant" state. Master clock pulses arriving at the `Kick` jack are ignored.
2. **Activation:** A trigger at the `Start` jack acts as the first beat. The module switches to "Running" and instantly outputs `0` on the `Index` jack.
3. **Iteration:** While "Running", each subsequent `Kick` trigger increments the internal counter and outputs the new integer on the `Index` jack.
4. **Completion (The Nth Kick):** When the Stepper is currently outputting `N-1`, it waits for the next `Kick` to provide the final step with its full rhythmic duration. Upon receiving that *next* `Kick`, it fires the `Done` jack.
5. **End-of-Sequence Behavior:**
* *If Looping is Disabled:* Upon firing `Done`, the module instantly reverts to the "Dormant" state. It will ignore all future `Kick` triggers until a new `Start` trigger is received.
* *If Looping is Enabled:* Upon firing `Done`, the module instantly rolls the index back to `0`, outputs `0` on the `Index` jack, and remains in the "Running" state.