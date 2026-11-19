# G7 core Kotlin migration result

## Scope delivered

The final twelve Java production classes have been translated to Kotlin:

| Former Java class | Responsibility |
|---|---|
| IPluginDA | Legacy transport compatibility contract |
| Plugin | Authentication state machine |
| Context | Session state and key/certificate references |
| Config | Original protocol/configuration byte material |
| Curve | secp256r1 parameters and random exponent generation |
| Packet | Fixed-width protocol packet serialization |
| KeyPair | Original BouncyCastle key generation/import adapters |
| DSAChallenger | Original provider SPI signing adapter |
| Calc | Unchanged J-PAKE formulas and challenge calculations |
| AESwrapper | Existing AES/ECB/NoPadding adapter |
| Log | Secret-suppressing compatibility diagnostics |
| SHA256 | Existing synchronized SHA implementation |

There are zero Java source files under dexcom-g7/src. Third-party libraries and
Android/JVM APIs have not been rewritten. Source authorship and SHA256's full
original MIT notice are retained in the Kotlin sources and LICENSES.

## Typed state machine and compatibility

Plugin.nextStep() is now the canonical transition API and returns AuthStep.Send,
Complete, BondRequired or Failed. Named phases replace private integer phase
identifiers. G7AuthenticationSession exposes nextStep, and the typed facade uses
it directly. Plugin.aNext only encodes typed results into the established array
shape for the existing BLE caller; it is not a second state machine.

Extra-channel writes precede authentication-channel writes. Construction order
also remains significant: the auth token is generated before the round-3 proof,
as in the Java expression evaluation order. Certificate commands, payloads,
signature framing, completion/rebond commands, receiver slots and persistence
channels are preserved. Legacy channel 7's shared accumulator-clear policy is
retained rather than silently redesigned.

No AndroidG7Ble source or transport timing was changed. This work introduces no
new sleep, timeout, retry schedule or Bluetooth operation. It is not a hardware
timing measurement.

## Crypto invariants

- Curve: secp256r1; same BouncyCastle curve parameters.
- Random sources: existing SecureRandom initialization and BC generator/provider
  choices retained. No production deterministic RNG or test seed introduced.
- Signing: exact original SignatureSpi.ecDSA256 superclass, SHA256/ECDSA and
  unsigned fixed-width 32-byte r plus 32-byte s retained.
- AES: Cipher.getInstance("AES/ECB/NoPadding") retains default provider selection.
- Key import/generation: original KeyFactorySpi.EC and KeyPairGeneratorSpi.EC.
- SHA256: same constants, integer wraparound, padding, endian format and shared
  working arrays under the SHA256 class monitor.
- Configuration fingerprint and legacy nibble-swapped hex parser retained.

Generic failures for missing password, key generation and impossible output
size now use IllegalStateException with explanatory messages (and retained cause
for key generation), instead of generic RuntimeException. These are invalid
state/failure diagnostics, not changes to valid protocol bytes. One scoped
VariableNaming exception preserves the public legacy Context.KeyB JVM field.
Crypto diagnostics expose only operation and exception class, never raw
exception text/stack, pairing material or packets. Cancellation is propagated.

## Evidence

Seven new characterization tests first passed against the Java engine. Fixed
peer packets/configuration fingerprint were captured before replacement, using
only synthetic scalars/password. See src/test/resources/auth-reference/README.md.

Final gate, fresh with --no-build-cache --rerun-tasks:

    gradlew.bat :dexcom-g7:clean :dexcom-g7:test :dexcom-g7:ktlintCheck :dexcom-g7:detekt verifyArchitecture :app-wear:compileDebugKotlin --no-build-cache --rerun-tasks

Result: BUILD SUCCESSFUL, 170 tasks executed. All 111 module tests passed, zero
failures/errors/skips. Java compilation reports NO-SOURCE. The Android Wear
consumer compiles successfully. No rule configuration, test exclusion or
baseline suppression was added to obtain passing checks.

Tests cover the prior 97 cases plus fixed Java peer replay, the certificate
branch including signature verification, configuration fingerprint, SHA
boundaries and concurrent calls, key import/failure cases, direct typed core
transitions/write order, redacted diagnostics and cancellation identity.

Independent read-only comparison against the saved pre-migration Java source
snapshot reported no actionable migration finding. This supplements the tests;
it does not establish correctness of every possible protocol input.

## Limits and release state

No device installation, physical sensor test, battery measurement or long-term
reconnection test was performed. The standalone SugarWear repository's pinned
shared dependency has not been advanced. Existing malformed-frame risks noted
in the earlier audit were not redesigned during this language migration.

Changes remain in the working checkout; no commit, push or release was requested
in this step. Kotlin source parity for tested cases is established; universal
protocol equivalence or clinical production readiness is not claimed.
