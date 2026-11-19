# Master phase 4 — retired G7 history transport

## Findings and correction

- SugarWear history is intentionally local, yet the shared protocol still depended on
  the complete G7 module for an obsolete Watch-to-Mobile batch/ack bridge.
- Mobile decoded those batches only to discard them, clear an already retired store,
  acknowledge the discarded IDs, and emit diagnostics.
- The batch/ack DTOs, four unused paths, codecs, receiver branch, clearing-only class,
  legacy refresh entry point, tests, and both unnecessary G7 Gradle dependencies were
  removed.
- The one-time migration still clears the old DataStore directly, so an installed
  legacy payload cannot reappear. Active setup and collector-local SugarWear history
  remain unchanged.

## Evidence

- Repository-wide reachability search finds no retired batch/ack/sync symbols.
- Protocol, Mobile, Wear, and SugarWear tests passed.
- Mobile, Wear, and SugarWear debug APKs assembled successfully.
- Protocol Detekt and Ktlint passed; combined verification completed 331 tasks.
