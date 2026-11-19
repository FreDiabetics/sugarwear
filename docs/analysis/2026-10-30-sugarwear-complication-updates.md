# SugarWear public complication refresh

## Cause
The local reading database notified observers and DirectToWatchPreferences, but the latter only requested Vigil bitmap updates. The eight normal SugarWear text/ranged providers had no standalone event fanout. An active native watchface could retain cached data even while SugarWear itself had a newer reading.

## Change
- Fan out existing reading/lifecycle notifications to the eight public providers using the existing active-instance registry.
- Standalone SugarWear requests resolve watch-direct data explicitly; Sugarlicious retains its existing canonical resolver path.
- Reuse existing Vigil minute-clock receiver for age/freshness updates, requesting only acknowledged active providers. No new timer, scan, radio operation or collector retry is introduced.
- Mark all twelve Vigil internal bitmap/action services hidden in native pickers while keeping them exported, enabled and usable by their default watchface bindings.

## Verification
Focused provider-dispatch, lifecycle-minute and merged-manifest contract tests cover these paths. Physical picker/cache behavior must be distinguished from code/build validation; no ordinary SugarWear provider was active in the initial device registry, so an existing selected provider cannot establish delivery proof.

Merged-manifest, dispatch and receiver tests passed; affected Mobile/Wear builds, ktlint, Detekt and Lint passed. SugarWear and Sugarlicious Wear installed on SM-L705F at 01:38-01:39. User selected a normal SugarWear complication and confirmed `funktioniert`. Crash buffer was empty after installation.
