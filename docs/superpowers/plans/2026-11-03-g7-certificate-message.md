# G7 certificate message and explicit Java accessors

User-approved follow-up: remove the Lombok dependency in Plugin and migrate CertInfoTxMessage to Kotlin, keeping cryptography and protocol state transitions unchanged.

1. Add Java-baseline tests for certificate frame bytes, selected certificate lengths, selector narrowing and Context accessor identity/null semantics. Baseline all existing module tests/checks.
2. Replace Plugin's generated getContext with an identical explicit accessor; replace its lombok.val locals with their existing concrete types. Remove unused Lombok imports from Plugin.
3. Kotlin CertInfoTxMessage also needs Context's Lombok-generated part getters. Replace only its six trivial part accessors with explicit identical methods, preserving volatile fields, aliases and null behavior. No state-machine or crypto changes.
4. Replace the Java certificate message with Kotlin using the existing BaseMessage, same package/class, byte opcode and static Java entry points. Do not change the module's Lombok dependency: other Java classes still need it.
5. Run clean uncached tests, ktlint, Detekt, architecture checks; review diff against the pre-change baseline. No installation or shared-submodule update in this task.

Worktree policy: continue in the user's existing main checkout, preserving previous uncommitted work. Baseline checks green; tests cached. No device operations.

Completed:
- Five additional compatibility tests passed against the old Java implementation before changes, covering exact six-byte frames, little-endian lengths, selector narrowing, opcode JVM type, missing material, Context identity and nullable array aliases.
- Plugin is now Lombok-free: an explicit identical getContext and concrete final local types replace Getter/val. Context is also Lombok-free after replacing only six trivial accessors. Its volatile fields and all state/password/reset logic are untouched.
- CertInfoTxMessage is Kotlin; public byte opcode, constructor and three static Java entry points are retained. Kotlin clean compilation now resolves both Plugin and Context without a Lombok compiler plugin.
- Clean uncached gate passed: 90 tests, no failures/errors/skipped tests; ktlintCheck, Detekt and verifyArchitecture green. All 17 tasks executed. javap confirms the expected Java field/method signatures. Source diff confirms Calc, Config, Curve, KeyPair, DSAChallenger, Packet, AESwrapper and libkeks cryptography unchanged.
- Lombok remains a module dependency for other existing Java classes; removing it globally is outside this change. No device tests/installation or submodule update performed.

Follow-up: the user subsequently authorized removing the remaining Lombok dependencies. That work is completed under `2026-11-03-g7-remove-lombok.md`; the module dependency statement above records the earlier step.
