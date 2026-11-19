# Master phase 11 — lifecycle and energy audit

## Root causes corrected

- The retired Watch-history acknowledgement design still exposed an `unsynced` provider path, an exported acknowledgement receiver, repository methods and a database index despite having no production sender.
- Mobile's foreground notification listened to unrelated diagnostics changes and was rebuilt every minute together with time-sensitive widgets.

## Changes

- Removed the unused history sync manager, acknowledgement receiver, provider route and repository contract.
- Database version 7 removes the obsolete pending index while retaining compatibility with existing databases; new databases no longer create sync bookkeeping.
- Kept direct-sensor history durable and read-only through the existing canonical provider.
- Foreground notification refreshes only when a new glucose measurement revision arrives or its user-facing preferences change.
- Minute-aligned refresh remains limited to widgets whose age and graph edge are inherently time-sensitive.
- Audited active BLE loops, bond polling, GATT callback waits, collector WakeLocks, recovery alarms, Health Connect work and Activity refresh loops. The retained loops are lifecycle-cancelled, timeout-bounded or platform-scheduled; no new polling was added.

## Verification

- Dexcom/G7 gate: 194 tasks succeeded (`test`, G7 Watch assembly/lint, Detekt and ktlint).
- Mobile gate: 229 tasks succeeded (`testDebugUnitTest`, assembly/lint, Detekt and ktlint).
- Regression coverage verifies notification identity depends on the glucose measurement timestamp rather than diagnostics or receipt-only changes.
- The known raw MP4 parser diagnostics remain unchanged as required by the master specification; they are not Lint findings.
