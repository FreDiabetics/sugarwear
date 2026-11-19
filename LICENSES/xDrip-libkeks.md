# xDrip+ G7 authentication (`libkeks`)

The sources below are derived from `NightscoutFoundation/xDrip`, commit
`66eb3a17063a21b8dff344719e5e72a7decbc1a6`:

- `dexcom-g7/src/main/kotlin/jamorham/keks/**`
- `dexcom-g7/src/main/kotlin/jamorham/libkeks/**`
- `dexcom-g7/src/main/kotlin/com/eveningoutpost/dexdrip/plugin/IPluginDA.kt`

Original author notices (`JamOrHam`) are retained in the source. Sugarlicious
integrates the formerly Android-library-based code into a JVM module and wraps
it with its own typed collector API. The upstream project is licensed under
GNU GPL v3; GPL v3 code may be combined with this GNU AGPL v3 project under
section 13 of GPL v3. See the root `LICENSE` and the upstream source at
<https://github.com/NightscoutFoundation/xDrip>.

The default G7 certificate material is the public one-time setup material from
the xDrip project documentation. It is isolated from the collector state machine
so updated public material can be supplied without rewriting the BLE flow.

The local `jamorham.keks.util.Log` suppresses upstream raw debug messages because
they can contain authentication packets. Crypto failure diagnostics include only
an operation identifier and exception class; messages, keys, packets and stack
traces are not forwarded. Cancellation is propagated.

The completed Kotlin language migration retains the upstream algorithms,
provider classes, random sources, nibble-swapped hex convention, byte order and
signed fields. The state machine emits typed AuthStep results, with the original
array API retained only for transport compatibility. Original author attribution
is retained, including the full original MIT notice in SHA256.kt.
