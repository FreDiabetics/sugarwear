# Dexcom Share timestamp decoding

Device diagnosis: successful authentication and HTTP array response with 288 records, but zero decoded readings. The decoder required `/Date(epoch)/`, while current Share responses can use `Date(epoch)` without the slash delimiters.

Fix: accept both wrappers, keep the epoch milliseconds unchanged even when a timezone suffix is present, and retain the existing value, age and canonical quality validation. A regression fixture covers a current reading plus historical backfill in the slashless format; the original slash-delimited fixture remains.

Safe diagnostics record only response/decoded/accepted counts and relative newest age. No credentials, session tokens or raw responses are persisted in diagnostics.

Hardware verified on the main SM-S948B: records=288, parsed=288, accepted=288, latest age 4 minutes. Canonical display source DEXCOM_SHARE, retained CGM history 289, therapy history 307, insulin/carbs/basal fields all present. Raw AndroidAPS and Broadcast snapshots remain stored separately.

Source-transition regression coverage: retained CGM graph history with no enabled current source, retained historical therapy, independent current therapy fields, new source appended, repeated composition idempotent. Current source remains absent when none is valid; archived readings are never promoted to a fresh value.

Follow-up live read: 288/288 decoded and accepted again, newest age 0 minutes; confirms a fresh subsequent measurement cycle. Source and transition tests passed, source/mobile lint passed. Header follow-up keeps neutral title/back icons and pure compact-title fade at constant local position and scale.
