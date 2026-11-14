# G7 unpaired-runtime and initial discovery correction

## Device evidence

The morning Galaxy capture contains no active collector service or registered SugarWear scan. The last retained attempt started at 01:03:30 and ended at 01:08:09 on 2026-10-27, Europe/Berlin. No subsequent overnight attempt is recorded. The retained battery counters do not establish an overnight continuous scan or a precise overnight energy cost; they must not be presented as such.

The preceding live initial-setup capture proves a different problem. SugarWear selected a G7 different from the main phone's successful Dexcom connection. One matching advertisement arrived with RSSI -92 dBm, followed by four status-133 connection failures before authentication. The phone's actual sensor window followed roughly seven seconds later. That unverified candidate was receiving known-peer retry treatment and could occupy the intended sensor's advertisement window. A scan result alone cannot establish identity; no pairing-code mismatch or successful pairing was inferred from those radio failures.

Raw state, device logs and encrypted preferences remain only in ignored build reports under `g7watch/build/reports/galaxy-pairing-20261027` and `galaxy-overnight-20261027`. They are not committed.

## Verified code causes and corrections

1. `ensureG7PairingAttempt` renewed an expired initial deadline whenever the service returned. Initial setup now has one original absolute deadline. Only the explicit user start creates a new budget; boot, alarm and UI restoration cannot manufacture one from an enabled flag and saved code. Expired or unconfigured setup becomes inactive until explicit user action.
2. Cancelling the system reconnect alarm left its persisted scheduled-cycle envelope behind. The common stop path now removes both, along with the advertisement wake, watchdog, live scan owner and wake handoff. Code, history and the local association are preserved.
3. Initial discovery now distinguishes an unauthenticated candidate from an established sensor. Pre-authentication connection failures defer that candidate for 20 seconds using a monotonic, invocation-local clock, then continue discovery within the same original budget. Other candidates remain eligible immediately. The candidate becomes eligible again after cooldown; no transient error creates a permanent exclusion. Authentication mismatches remain rejected for the current invocation, and cryptographic validation is still mandatory.
4. The budget covers GATT/authentication work as well as discovery. A child coroutine timeout bounds the entire unpaired collection call. Explicit expiry checks also protect protocol/authentication and shared-key commits, including when timer dispatch is delayed. Known authenticated sessions bypass obsolete initial-setup deadlines; runtime reset preserves their established authentication authority before the first glucose value.
5. The pairing page now uses actual collector phases and recent errors. A paused collector has a non-animated paused message; preparation, Android consent, connection, recovery and terminal timeout are distinct. A selected peer or authenticated session still does not produce a fabricated glucose reading.

## Validation boundary

Focused regression tests first demonstrated the deadline-renewal, unconfigured-runtime, stale scheduled-envelope and paused-animation failures. Additional regressions reproduced late authentication past the deadline and loss of authenticated authority during runtime reset before those corrections. Candidate tests cover the captured seven-second window, cooldown expiry, separate authentication rejection and unchanged known-address/session-key policy.

The final build/static results and installed-device observations are recorded in the implementation plan. Successful unit tests are not evidence of a successful physical sensor transfer. No new pairing is initiated by the implementation or installation workflow; final acceptance requires observing a user-initiated attempt and a validated value from the intended sensor.

## Installed-device observation

The final APK passed the affected regression tests, assembleDebug, lintDebug (no issues), Detekt and ktlintCheck. It was installed on the Galaxy Watch Ultra; the installed APK SHA-256 matched the built artifact and the retained attempt history was unchanged.

During the user-initiated attempt at 14:41:59 Europe/Berlin, discovery selected a candidate at RSSI -74 dBm at 14:43:09. Android returned status 133 after 264 ms, before authentication. Cleanup closed that GATT and the new candidate cooldown resumed discovery without the former rapid same-peer retry sequence. This candidate matches the main phone's previously captured successful Dexcom peer; unlike the preceding night's candidate, it cannot be dismissed as a different sensor. No authenticated connection or validated live value has yet been observed in this attempt.

The user found and removed a residual sensor bond on the Pixel Watch from a failed setup. Inspection showed no SugarWear collector service or stored collector state on that Pixel at the time. A residual bond alone does not establish an active competing connection or prove the Galaxy failure's cause. The Galaxy attempt remains under observation after that removal.

### Follow-up: consent and Dexcom control

At 14:48 the Galaxy reached the configured sensor, exchanged authentication messages and entered Android bonding. The user later confirmed they had been away and had not accepted the consent prompt. Android's bond changed from BONDING to NONE after SMP connection timeout and peer termination; no valid live reading was observed. Later scan-only windows received no advertisements. After the third failed radio window, the runtime was recycled; a persisted next-cycle envelope existed for 15:00, so absence of a foreground service at that instant did not prove missing scheduling. The comparison was explicitly paused without deleting sensor state or history.

The secondary Galaxy S24 still retained the Dexcom sensor association but had zero Android bonds. The user clarified that they had manually removed its sensor bond during the previous transfer; only clearing cache was not the demonstrated cause. The main phone's Bluetooth had initially remained enabled, making the initial transfer comparison uncontrolled. At 15:44:10 on the secondary phone, Android recorded accepted consent followed by successful LE authentication and BOND_BONDED. Dexcom's UI then displayed a glucose value instead of signal loss. This establishes a successful reference pairing, not yet sustained multi-cycle reception or a proved universal cause of the earlier watch failures. Full HCI captures stay outside Git in the private reverse_engineering capture folder.

## Reference follow-up and completed Galaxy pairing

The secondary phone's full HCI export contains the successful pairing and two subsequent automatic cycles (15:48 and 15:53). Each established cycle exchanges authentication opcodes 0x02/0x03/0x04/0x05, then control request/response 0x4e. The 19-byte live response arrived approximately 712 and 681 milliseconds after the first ATT exchange. This agrees with SugarWear's existing authentication/current-read path; auxiliary original-app commands are not evidence that the validated collector needs those operations.

A separate code defect was reproduced: after interrupted Android consent, a saved address and application key with BOND_NONE require completing setup again. The scan override was incorrectly clamped to the known-address 60-second limit, although the original initial-pairing budget remained valid. Regression expected 720000 ms and observed 60000 ms. Discovery now uses the remaining original budget specifically for pairing recovery; the outer absolute budget and established-session short scans remain unchanged.

With the corrected build, the user accepted Android consent on the Galaxy at 16:03. Bluetooth reached BOND_BONDED, the collector authenticated and stored a validated fresh LIVE reading. An automatic subsequent cycle at 16:08 also succeeded with the following sequence and four-second packet age while reference-phone Dexcom remained running. This is observed two-cycle coexistence, not a long-term reliability claim or Pixel proof.

### First-reading address publication race

Despite successful reception, the API36 pairing page displayed a missing-address error. The shared-key callback had already persisted the sensor address, but provisional live publication replaced the current sensor with the addressless collection-start snapshot while optional backfill proceeded. The final result later restored the address; the UI had meanwhile latched a local failure.

Live publication now preserves the current sensor only when sensorId and sessionId match the collection identity, retaining the established address and updating live sensor state. Pairing success also waits for a nonblank sensor address; a transient missing address does not become a latched companion-access failure. Both the address preservation and premature-success regressions failed before the correction. Physical confirmation of the corrected screen and Pixel pairing remain required.

## Pixel Watch validation and platform-specific setup correction

The user accepted Pixel Android consent at 16:33:20. Android recorded BOND_BONDED and the collector authenticated, stored a fresh LIVE reading and scheduled the next window. The subsequent 16:38 cycle produced the consecutive sequence with six-second packet age, while the reference-phone app remained running and the Galaxy collector was paused.

A distinct setup UI failure followed valid reception: CompanionDeviceManager rejected the extra association with the system message "3p apps are not allowed to create associations on watch." Actual Bluetooth consent had succeeded. Setup must not require this separate association/removal-ownership capability. `ensureForAuthenticatedSensor` now checks the exact peer's actual bond and completes without creating a companion association; unbonded peers still fail. The new bonded-peer regression first failed against the old implementation. Tests, build, Lint (no issues), Detekt and ktlint then passed. Installed on both watches without re-pairing; the Pixel's actual screen returned to its normal glucose overview with authenticated state and no collector error.

This correction does not claim that Pixel allows automatic removal of arbitrary Bluetooth bonds. The unlink path still requires permitted ownership or explicit system-level removal; the existing failure UI must not report a bond as removed when the platform denies removal. No bond, stored reading or credentials were deleted by these validation steps.
