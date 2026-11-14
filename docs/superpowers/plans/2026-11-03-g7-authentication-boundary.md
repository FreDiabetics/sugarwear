# G7 authentication reference tests and typed boundary

Scope approved by user: migration steps 1 and 2 only. Keep Java crypto, persisted state, BLE timing and the active collector unchanged. Work in the existing repository, preserving the user's main-only workflow and untracked audit/instructions.

1. Characterize the real Java engine with synthetic fixtures: nibble-swapped hex, endian conventions, AES/SHA vectors, stored-key authentication, fresh exchange, bond and completion signals. Random tokens/proofs are checked by structure and verification, not forced to deterministic production randomness.
2. Write tests for a Kotlin authentication interface, explicit actions/errors and defensive packet ownership. Run before implementation to demonstrate the missing API.
3. Add G7Authentication.kt (contract/value types) and LegacyG7Authentication.kt (adapter to the existing G7AuthenticationSession). Decode old array signals only at this boundary; no inference of timeouts from null. Preserve extra-data-before-command ordering and completion command bytes. Do not wire into AndroidG7Ble yet.
4. Run module tests, architecture checks, Kotlin formatting/static checks, and inspect the diff for any Java or active consumer changes. Document coverage limits and show the concrete code to the user.

Error policy: distinguish invalid output lengths, unknown legacy failure, rejected input and the existing explicit authentication mismatch. No CryptoTimeout result without an actual clock/deadline owner. False input responses mean no next action, not proven success or failure. No logging of secret packets/keys.

Execution ledger: source and dependency audit reconfirmed; only AGENTS.md and the prior audit report were untracked at start. No device interaction is part of this task.

Completed:
- Added 15 Java-engine reference tests and 17 typed-boundary tests. New API tests first failed compilation because the contract/adapter did not yet exist; implementation followed. Existing-engine characterization tests intentionally describe current behavior. One initial fixture assumption about the alternate-slot wire byte was corrected against the actual Java implementation: persistence 0x02 selects wire end byte 0x01, not 0x03.
- Fixed synthetic scalars 1/2 and 3/4 establish the shared-key known answer 178641C6B47728F1D1068EA7D14B14A0; derivation independently checked from the EC shared-point formula and JCA SHA256. This is public synthetic test material, not sensor credentials. Proof randomness remains production-random; proofs are verified rather than compared byte-for-byte.
- Preserved extra-before-auth ordering, completion/rebond command payloads, defensive ownership and redacted rendering. Generic legacy null remains an explicitly unknown failure. No fabricated timeout and no broad exception/cancellation catch.
- Final relevant gate: 70 module tests (38 existing plus 32 new), ktlintCheck, Detekt and verifyArchitecture passed. Independent read-only review reported no blocking findings. All previously tracked source files, including Java and the active consumer, remain unchanged.

Limit: no physical sensor test and no complete real-sensor certificate/bonding transcript corpus in this step. The tests establish a migration baseline, not a mathematical guarantee of all possible protocol executions. The active collector has not been switched to the additive typed adapter; that integration is a separate change.
