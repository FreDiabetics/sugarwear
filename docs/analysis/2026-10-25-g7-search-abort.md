# G7 search termination, 2026-10-25

## Observed evidence (UTC)

Before deployment, the retained 192 attempts contained 147 fresh successes, 18 fallback-scan failures, 13 GATT connection failures, seven missing advertisements, three cancellations, three authentication failures and one interrupted process. The latest historical attempt ended at 00:50:08; the diagnostic stream ended at 00:50:09. There were no retained overnight collector attempts after this point. The persisted collector state was empty and the credential preference had zero entries before today's installation. The cause of that prior clearing is not established. No sensor credentials or state were removed during this investigation.

Yesterday's 0.7.1 builds were installed on the phone and watch first, including Wear, SugarWear and the three watchfaces. A subsequent fresh setup attempt (9150, 09:33:55) reached authentication and failed at Plugin.receivedResponse's challenge comparison. The new safe diagnostics identify Authentication/RequestAuth, not a scan cancellation or certificate failure. Generic G7-AUTH-204 converted this into a fatal error, and the session moved to USER_INTERVENTION_REQUIRED with no next reconnect.

Phone/Wear bridge retained records cover the intervening period, but their bounded buffers are not a complete historical archive. Transport acceptance and duplicate filtering do not prove a successful direct BLE session. Updates performed during this investigation must not be counted as spontaneous historical process failures.

## Root cause and change

A failed challenge proves that this peer has not authenticated; it does not prove that the entire discovery operation must stop. In discovery without a known address, the scanner accepts the first connectable G7 candidate. Another transmitting sensor can therefore terminate setup prematurely. The specific reason this peer mismatched (wrong candidate, code or exchange) remains unproven.

The exact KEKS mismatch now has a typed exception and a recoverable code, without inspecting or exposing secret-bearing exception messages. During initial discovery, the failed connection is closed, that candidate address is excluded for the current collection attempt and discovery continues within a single elapsed-time budget. No identity, shared key or glucose from the rejected candidate is accepted. Known-address collection retains its identity filter and normal bounded recovery. Other security/protocol errors remain fatal. The existing persisted pairing deadline still terminates unsuccessful setup; this does not create indefinite rapid retries.

Manual cycles without expected-window IDs also produced misleading ledger-ID-mismatch diagnostics. Only cycles with an actual expected-window ID now emit that error if lookup fails. This is a diagnostic correction, not evidence of a lost scheduled reading.

Reference: the upstream maintainer describes the same KEKS mismatch when an old/other G7 is encountered: https://github.com/NightscoutFoundation/xDrip/discussions/3294 . This supports candidate handling, but does not identify the physical peer in this reproduction.

## Acceptance

Focused authentication and pairing-screen regression checks, collector build and static checks are required. Hardware success requires observing the new search/recovery behavior and ultimately an authenticated fresh reading; compilation alone cannot establish sensor connectivity.

Validation completed: 29 focused Wear authentication/pairing UI tests and 38 dexcom-g7 tests passed; SugarWear debug assembly, Lint, Detekt and ktlint passed. Installation and live validation of this additional fix remain pending because the watch disconnected and its former debugging port closed. Yesterday's builds had already been installed successfully before that disconnection.

## Follow-up device evidence

The authentication recovery build was subsequently installed successfully. The next active attempt (9156) remained in SCANNING rather than ending with AUTH_FAILED. The system exit record for the immediately preceding visible app closure explicitly identifies PACKAGE UPDATED at 10:55:17 local time, matching this installation; no new SugarWear fatal exception was present in the crash buffer.

A separate discovery defect was confirmed by Bluetooth scanner statistics: the 10:49:25 scan spent 279078 of 279081 milliseconds suspended, delivering zero results. The implementation passed null filters. Android documents that unfiltered scans stop on screen-off. A later screen-on run received 2436 advertisements in 69 seconds, so absence of a connected sensor cannot be equated with a dead scanner.

Discovery now uses exact-address filters for known sensors and complete/short local-name DX-prefix filters for initial discovery (the module already requires API 33). Callback identity/connectability and cryptographic validation remain mandatory. This avoids the platform's unfiltered screen-off suspension without adding wake locks, display manipulation or polling. Tests cover all three supported Dexcom name families, shortened names, unrelated names and nameless exact-address reconnection.

Platform reference: https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner#startScan(java.util.List,android.bluetooth.le.ScanSettings,android.bluetooth.le.ScanCallback)

## Successful collection and full-detachment follow-up

On-device collection subsequently authenticated and stored consecutive fresh direct readings, sequence 232 and 233, with WAITING_FOR_NEXT_READING and no current error. The filtered scanner delivered actual G7 advertisements before successful collection. The additional full-detachment build was then installed without resetting the current association.

The existing full-detachment path removed only the address in the active state. Two older Dexcom bonds were still present on the watch, despite no stored shared session key. Explicit full detachment now includes all bonded Dexcom sensor addresses plus the active/authenticated address, stops the existing collector ownership/schedule path, and requires every bond-removal operation to succeed before reporting success and clearing the association. Before removal, those addresses are synchronously committed to an address-only exclusion list. Both discovery and direct connection reject retired addresses. No old session, secret or reconnect job is retained in this list. Historic measurement/diagnostic records remain non-active history. A running collector is not automatically unlinked or reset by installation.

Final affected-module validation: 37 Wear tests plus 38 dexcom-g7 tests passed; assembly, Lint, Detekt and ktlint passed, with zero Lint issues. Tests cover persisted exclusion, current plus legacy bond removal, credential/state clearing, preserved measurement history and safe retry when bond removal fails. A physical test that deliberately detaches the newly working sensor is intentionally not performed.

Review refinement: only explicit full detachment creates new permanent exclusions. Normal receiver-takeover cleanup removes obsolete bonds/state without adding exclusions, and does not silently remove an existing explicit exclusion. The pending reconnect envelope is discarded as well as cancelling its system alarm. Regression coverage includes both behaviors.

After that refinement, the final run passed 55 focused Wear tests and 38 dexcom-g7 tests; build, Lint (zero issues), Detekt and ktlint passed. The final APK was installed successfully. Independent review reported no remaining important findings. The currently working sensor was not detached for testing.

Yesterday's release artifacts were compared by SHA-256 with installed packages: Mobile debug, Wear debug, ApeX release, Orbit debug and Vigil debug matched exactly. Vigil device checks confirmed graph-scale tap, glucose-header app opening, center hand-opacity toggle and native hand visibility off/on. Original graph scale and hand visibility were restored, and the original Orbit instance was reselected. Mobile Watch-menu opening and preview rendering succeeded without a crash. No full-suite UI acceptance claim is implied by these focused checks.
