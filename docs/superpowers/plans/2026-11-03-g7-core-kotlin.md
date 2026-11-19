# Final G7 core Kotlin migration

Approved scope: migrate all twelve remaining Java classes, use AuthStep in the state machine, preserve algorithms/providers/randomness/wire sequence and keep BLE transport/timing untouched. No new crypto algorithm or provider substitution; no installation.

1. Capture fixed synthetic peer round packets and config fingerprint from the current Java engine before replacement. Add reference tests for replay, digest boundaries, key import and certificate states. Retain the 97 existing tests.
2. Translate Config, Curve, Context and Packet with compatible Java fields/getters/statics; preserve endian/signed/alias contracts. Translate IPluginDA only as compatibility API.
3. Translate AESwrapper, KeyPair, DSAChallenger and SHA256 without changing algorithms, provider classes, SecureRandom initialization or class-level hash lock. Preserve provenance. Crypto failures remain explicit at the compatibility boundary with sanitized diagnostics and cancellation propagation.
4. Translate Plugin with named phases and an AuthStep-returning transition method. aNext becomes only a legacy output adapter. Expose typed steps through G7AuthenticationSession and use them directly in the existing typed facade. No AndroidG7Ble edits.
5. Run clean uncached full tests, ktlintCheck, Detekt and architecture checks; assert no production Java files remain. Validate Android consumer compilation and review coverage/limits. Do not claim hardware or universal protocol proof from unit tests.

Work in the existing main checkout, preserving prior uncommitted changes. Current runtime still uses the compatibility session API; the direct typed API must remain byte-equivalent. Existing receiver slots, certificate channels, chunk order and delays are frozen.

Completed: all twelve classes migrated, no Java source remains. The 97 prior tests
and 14 additional tests pass (111 total). Final clean uncached gate including
app-wear compilation passed, 170 tasks executed. Read-only review found no
actionable migration issue. Details, documented failure-diagnostic changes and
hardware/release limits: docs/audits/2026-11-03-g7-core-kotlin-result.md.
