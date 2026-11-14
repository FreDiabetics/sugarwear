# Remove remaining G7 Lombok and migrate small helpers

User-approved: remove remaining Lombok dependencies and migrate the next Java helper classes incrementally; cryptographic algorithms and protocol behavior stay unchanged.

1. Baseline tests/checks and capture javap instructions of Calc/ZKP, KeyPair, DSAChallenger, AESwrapper and SHA256 before mechanical changes.
2. Add Java-baseline tests for coordinate encoding, buffered digest boundaries, KeyPair constructor/getter compatibility, Packet fields/constructor and authentication exception identity.
3. Remove Lombok from retained Java crypto implementations mechanically: explicit constructors/getters and the same final local types. Do not change provider selection, randomness, formulas, constants, modes, catch behavior or state transitions. Keep Packet Java for this increment, replacing annotations only.
4. Migrate JECPoint (coordinate serialization), Digest (buffer wrapper retaining SHA256), and AuthenticationMismatchException individually to Kotlin. Preserve packages, constructors, methods and legacy null/alias behavior. Run module references after each.
5. Remove compileOnly/annotationProcessor Lombok dependencies once no usages remain. Clean uncached module tests, static/architecture checks, dependency reports and before/after crypto-instruction comparison. Record any genuine instruction difference and resolve it before claiming equivalent implementation.

Scope: current main checkout with existing uncommitted migrations preserved. No installation, pinned-submodule update, BLE change or replacement cryptography. Characterization tests target the actual old implementations before translation.

Completed:
- Added seven helper compatibility tests and passed them against the Java baseline before replacement. They cover coordinate bytes/getter identity/null semantics, digest chunk snapshots/repeated finalization/destination boundaries, key/packet constructors and getters, frame boundaries, and exception type/message.
- Replaced all remaining Lombok annotations and val declarations with explicit identical Java constructs. Retained the algorithms and existing provider/error behavior in Calc/ZKP, KeyPair, DSAChallenger, AESwrapper and SHA256.
- Migrated JECPoint, Digest and AuthenticationMismatchException separately, running the complete module tests after each. Packet remains Java with explicit constructor/getters; no cryptographic provider or hash implementation was substituted.
- Removed both Lombok dependency declarations. Source/build search finds no remaining Lombok usages in the module; compileClasspath has no Lombok and annotationProcessor reports no dependencies.
- Final clean uncached gate: 97 tests, zero failures/errors/skips; ktlintCheck, Detekt and verifyArchitecture passed, all 17 tasks executed.
- Captured javap disassembly before and after the changes for six retained crypto classes (Calc, Calc$ZKP, KeyPair, DSAChallenger, AESwrapper, SHA256). All 47 method instruction sequences match after normalizing constant-pool reference numbers and whitespace and ignoring method declaration order. This is an instruction comparison, not a claim that complete class files/metadata are binary-identical. Constructor field initialization and random-exponent generation remain in the same instruction order.
- No physical sensor tests, device installation or production BLE integration performed. This increment removes Lombok; it does not claim the whole G7 module is Kotlin-only.
