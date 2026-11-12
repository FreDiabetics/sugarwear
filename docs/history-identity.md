# Historical identity after explicit unlink

Explicit unlink still removes Bluetooth ownership, credentials and the active sensor/session.
Glucose history remains, including its provenance. `g7_history_identity` contains only a SHA-256
address fingerprint, reconstructed sensor-start time and historical stream aliases. It contains
no Bluetooth address, pairing code or key and is never a discovery/reconnect target.

An alias is registered only after successful hardware authentication and a validated live packet.
Both the address fingerprint and sensor start (within 90 seconds of reconstruction jitter) must
match a single existing historical stream. Different hardware/start times remain independent;
conflicting identities are explicit errors rather than an inferred merge.
An established local session keeps its verified historical binding across watch wall-clock
corrections; only discovery of a new local session uses the reconstructed-start tolerance.

Database writes and session-scoped predecessor/anchor queries use that canonical historical
identity. Returned predecessors retain the caller's runtime identity for validated delta mapping.
Before publishing the live packet, the collector resolves the existing history anchor, avoiding
a new 24-hour bootstrap merely because local setup generated another session identifier.
Authentication, GATT packet encoding and BLE retry/timeout constants are unchanged.

The November 11 repair is a device-specific maintenance operation against private snapshots.
Only newly downloaded BACKFILL copies verified against the protected pre-unlink originals are
eligible. Original rows are protected by a full-row comparison. All remaining measurement fields
are compared against the latest pre-maintenance snapshot; only canonical provenance changes.
Copies are removed transactionally off-device, validated, backed up and transferred with the app
stopped. No generic cross-sensor timestamp/value deduplication or automatic destructive migration
is performed on future installations.
