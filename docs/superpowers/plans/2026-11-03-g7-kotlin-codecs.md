# G7 Kotlin codecs and message migration

User-approved scope: step 3, replacing the legacy hex parser and small message/helper classes while retaining the prior authentication references. No new cryptography, transport timing, session schema or active BLE integration.

Use the existing main checkout as requested by the established workflow. Preserve the untracked step-1/2 files and audit. Baseline module tests, ktlintCheck, Detekt and verifyArchitecture are green (cached tests).

1. Add characterization tests against Java before replacing it: malformed and Unicode hex, Java trim semantics, nulls, array ownership/length errors, auth/sign message padding and lengths, signed status/certificate fields, lazy packet buffers. Preserve exact reference vectors from the previous step.
2. Capture a Java-engine parser fingerprint before migration; tests compare the migrated code with the fixed baseline rather than a newly generated expected result. Preserve Java entry points through JvmStatic/JvmField and constructor overloads.
3. Replace Util, ByteArrayHashMap, BaseMessage, AuthChallengeTxMessage, AuthRequestTxMessage2, AuthStatusRxMessage, CertInfoRxMessage and SignChallengeTxMessage individually. Run reference/codec tests after each change. Existing cryptographic classes, Plugin, Config and Context remain unchanged.
4. CertInfoTxMessage has a dependency on Plugin's Lombok-generated getContext. Verify whether the current mixed compiler can resolve this getter before including it; do not introduce reflection or a new compiler dependency merely to expand this leaf migration. If unavailable, retain this Java leaf until Plugin migration and record the reason.
5. Update source provenance paths, run clean module tests and all applicable checks, review the complete migration, and report precise parity evidence and limits. Do not update the pinned SugarWear submodule or install unrequested device builds.

Rulings: this is behavioral preservation, not parser hardening. Invalid legacy hex digits produce arithmetic byte values in even-length strings; odd/empty/null input returns null. Keep those contracts at the compatibility entry point without a broad catch. Mutable legacy message fields stay compatible; defensive payload ownership remains provided by the new typed adapter.

Execution ledger:
- Added 12 edge-case characterization tests and ran them successfully against Java before any replacement. Added a fixed pre-migration Java fingerprint over all 16,384 ASCII character pairs, plus two compatibility regressions found during review (JVM byte descriptor and buffer state after overflow): 15 added tests in total.
- Migrated eight classes individually, with the module test suite green after each replacement. SecureRandom generation, AES, EC, SHA, receiver slots, persisted state, Plugin and Context remain unchanged. Utility construction is now private because all project consumers use static methods; static Java entry points remain available.
- Attempted CertInfoTxMessage migration and verified with a clean build that Kotlin cannot resolve Lombok-generated Plugin.getContext. Reverted that experiment byte-for-byte. This class remains Java until the Plugin/Lombok boundary is migrated; no reflection, compiler plugin or duplicate engine introduced.
- Kotlin translations retain upstream attribution and LICENSES/xDrip-libkeks.md now includes their paths.
- Clean uncached full module build, tests, ktlintCheck, Detekt and verifyArchitecture passed; 85 tests, zero skipped/failures/errors. Source diff confirms all cryptographic engine files and CertInfoTxMessage unchanged. No hardware tests or installation requested/performed.

Follow-up: the user subsequently authorized removing Plugin's Lombok boundary. CertInfoTxMessage has now been migrated under `2026-11-03-g7-certificate-message.md`; the deferral above describes the earlier step, not the current final state.
