# G7 pairing budget and candidate recovery

**Goal:** Stop abandoned/unpaired collection without breaking recovery for an authenticated sensor; let explicit setup move past a different unreachable G7 instead of occupying its connection window.

**Architecture:** Keep the canonical collector, credential store, session manager and Android BLE owner. Initial setup has one persisted absolute budget, never renewed by alarms, process restoration or UI entry. Temporary per-candidate cooldown is local to discovery and never changes permanent exclusions or authenticates a peer. Existing known-sensor reconnect behavior remains intact.

**Spec:** User requests from 2026-10-27; overnight scan report still requires current device evidence. Captured live evidence from the preceding night shows an addressless setup selecting a different G7 than the main phone's active Dexcom connection, then four GATT-133 failures before authentication. Raw device data stays under ignored build reports.

## Tasks

- [ ] Inspect current device state/history when Galaxy is reachable; establish whether collection continued overnight or was re-enabled. Preserve history, code and existing Mobile brand edits.
- [ ] Write failing policy/runtime regressions for expired and unconfigured initial collection, automatic re-entry, explicit restart and authenticated recovery. Preserve a single absolute pairing deadline; stop service/scans/alarms and surface timeout until explicit user action.
- [ ] Write failing candidate recovery tests. Give unreachable, unauthenticated discovery candidates a bounded cooldown and continue discovery within the existing overall deadline. Keep cryptographic validation and known-address reconnect policy unchanged.
- [ ] Show truthful preparing/scanning/connecting/retrying/failed states instead of one indefinite search animation. Test only changed presentation/lifecycle behavior.
- [ ] Run affected tests, build and static checks; one final independent review. Install on the intended watch without initiating pairing, then observe a user-initiated attempt and require a validated sensor value before claiming hardware success.

## Decisions

- A configured, previously authenticated sensor may reconnect while temporarily disconnected. Unpaired setup may scan only after explicit Connect and only within its original finite budget.
- No fabricated sensor identity, copied phone credential, automatic pairing, Bluetooth reset or history deletion.
- No new permanent exclusions for transient radio errors. A valid candidate must become eligible again after cooldown.
- The four existing Mobile brand changes are authorized preceding work and must not be discarded or silently mixed into collector logic.
