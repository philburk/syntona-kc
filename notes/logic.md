
**Module Specifications: Logic & Routing (Event Domain)**

**1. Boolean Logic Modules**
**Modules:** `and`, `or`, `xor`, `not`

* **Design Philosophy:** Plain English names (not `&&` or `||`) targeted at computer musicians. These operate strictly on event/message payloads, evaluating numerical values as booleans.
* **Truth Evaluation:** Any payload <=0 is evaluated as `FALSE`. Any payload >0 is evaluated as `TRUE`. Outputs are always `1` (TRUE) or `0` (FALSE).
* **AND / OR / XOR:**
* **Inputs:** `A`, `B`.
* **Output:** `out`.
* **Behavior:** Evaluates the logical state of the inputs and outputs a `1` or `0` when a new message triggers evaluation.


* **NOT:**
* **Input:** `in`.
* **Output:** `out`.
* **Behavior:** Inverts the boolean state of the incoming message (outputs `1` if input is `0`, outputs `0` if input is non-zero).



---

**2. Branch Modules (1-to-N Demultiplexers)**
**Variants:** `branch4`, `branch8`, `branch16`

* **Purpose:** Routes a single incoming event stream to one of several specific outputs based on a control index.
* **Domain:** Event/Message domain only (handles standard messages and composite `VoiceEvents`). Does not process continuous audio signals.
* **Inputs:**
* `index`: A non-kickable input (0-based integer). Updating this input changes the active route but *does not* trigger an output.
* `in`: The main data input.


* **Outputs:** `out0`, `out1`, ..., up to `outN-1`.
* **Behavior:** When a message arrives at the `in` jack, the module checks the current value of the `index` state. It immediately forwards the unmodified message out of the corresponding `out[index]` jack. If the index is out of bounds (e.g., negative or $\ge N$), the message is dropped.

---

**3. Choose Modules (N-to-1 Multiplexers)**
**Variants:** `Choose4`, `Choose8`, `Choose16`

* **Purpose:** Listens to one of several incoming event streams and passes it to a single output, based on a control index.
* **Domain:** Event/Message domain only (handles standard messages and composite `VoiceEvents`). Does not process continuous audio signals.
* **Inputs:**
* `index`: A non-kickable input (0-based integer). Updating this input selects the active input but *does not* trigger an output.
* `in0`, `in1`, ..., up to `inN-1`: The data inputs.


* **Output:** `out`.
* **Behavior:** The module only listens to the input jack matching the current `index` state. When a message arrives at `in[index]`, it is forwarded unmodified to `out`. Any messages arriving on the unselected input jacks are ignored/dropped.

---

**4. Gate Module**
**Module:** `Gate`

* **Purpose:** A boolean pass-through switch for discrete messages. Visually and conceptually signals "mute/unmute" intent to the user, acting as a simplified subset of a Branch module.
* **Domain:** Event/Message domain only.
* **Inputs:**
* `enable`: A non-kickable boolean/integer input. `0` = Closed, Non-zero = Open.
* `in`: The main data input.


* **Output:** `out`.
* **Behavior:** When a message arrives at the `in` jack, the module checks the `control` state. If `control` is Open (TRUE/1), the message passes through to `out`. If `control` is Closed (FALSE/0), the message is entirely blocked and dropped. Under the hood, this can share the exact same execution logic as a `Branch2` where `out1` simply doesn't exist.