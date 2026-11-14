# Health Connect contract

Sugarlicious uses Health Connect only after the user grants each requested data type.

## Data written by Sugarlicious

- Current and locally retained CGM history are written as `BloodGlucoseRecord` values with interstitial-fluid specimen source.
- Every value receives a stable `clientRecordId` derived from source and measurement time plus a monotonic record version. Repeated delivery therefore updates or deduplicates the same Health Connect record instead of creating uncontrolled duplicates.
- The first successful connection backfills up to 24 hours from Sugarlicious' bounded local history. Later source updates and the hourly worker send the current/new values again idempotently.
- Sugarlicious does not invent or mirror insulin, basal, IOB, COB, predictions, imported activity, or imported nutrition. Health Connect 1.1 has no matching insulin-delivery/IOB/COB record type.

## Data read into the local overview

With the corresponding user permissions, Sugarlicious reads a 24-hour local snapshot of activity, heart rate and HRV, steps, calories, distance, elevation, floors, workouts, sleep, hydration, nutrition, body measurements, blood pressure, blood glucose, oxygen saturation, respiratory rate, and VO2 max.

Imported Health Connect records remain read-only and are never written back as Sugarlicious records.

## Status and failure handling

The Settings screen reports the blood-glucose write permission, the number of granted read permissions, the last confirmed glucose timestamp, and stable diagnostic codes. Permission loss and insert failures are no longer silently ignored.
