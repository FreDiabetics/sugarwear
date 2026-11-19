# Watch editor and Vigil refinement implementation plan

**Goal:** Make provider selections update both native-shaped slot demos and the hero immediately; remove the legacy four-item editor limit; refine Vigil and ApeX graph geometry.

**Architecture:** Preserve native WFF provider assignment through the Watch app. Persist phone-side demo assignments by actual WFF slot ID, validate supported formats and derive render geometry from WFF templates. Native header actions use supplied vectors. Vigil continues through canonical direct-watch models and the existing shared graph renderer.

- [x] Trace legacy list versus static slot-image mismatch; implement validated per-slot maps and dynamic demos.
- [x] Wire hero and slot panel to the same map; filter provider choices to the selected slot.
- [x] Move open/send actions into header icons; remove Vigil complication heading and SugarWear-only caption.
- [x] Import supplied SugarWear branding vectors; update launcher/notification targets.
- [x] Restrict the curved ApeX graph cap to the area above the middle complications, preserving its provider identity and theme.
- [x] Refine Vigil graph/axes/clock/units/400 toggle/system time.
- [x] Focused tests, affected module builds, static checks and independent source review.
- [x] Install Mobile and verify opening Watch and switching the selected face without another crash.
- [x] Final Watch installation and physical hit-dispatch/visibility checks, completed on 2026-10-25 after direct sensor collection recovered.

Native Samsung/Google binding is not overwritten by phone metadata. Sending a preset is described accordingly; source/data integrity stays unchanged.

## Subsequent requirements

- [x] Native slot-ID-preserving display order; larger short-text catalog demos, responsive system typography, neutral header actions and no Vigil info card.
- [x] Exact transparent ApeX hands on Vigil, rounded perimeter markers clear of data, subtle lower-right hand shadows.
- [x] Center-tap hand opacity: 100% → 30% → 100%, with a private non-customizable control and event-driven rendering; native Zeiger visibility setting and concentric pivot.
- [x] Wear UI icon margins and SugarWear vector rendering at its actual display size; retain clinical glyph geometry.
- [x] Refresh Vigil's native device-font preview at 22:10 after final rendering changes.
- [x] Complete repository gates. Commit/PR/merge/main synchronization is tracked in the associated GitHub integration history.

Suite version: 0.7.1 (71). README remains the supplied Sugarlicious mark and “Something is happening here...........”.

Validation: full verifyArchitecture/test/assembleDebug/detekt/ktlintCheck/lint and all five WFF release builds passed; 44 Python checks and official WFF/code-free checks passed. Android Lint reports no errors or warnings. Independent native rendering checks cover all six Orbit slots and the final Vigil preview. The initial Watch-menu crash was caused by a wrap-content first measurement before an asynchronous resize; the host now has a finite height before attachment, with a regression test covering that first measurement.

Mobile, Sugarlicious Wear, SugarWear and ApeX were installed during implementation on SM-S948B / SM-L705F. The last Watch connection subsequently went offline. The final lightweight Vigil action surfaces, hub and native visibility option still need installation and physical checks. On 2026-10-25 the user explicitly deferred device tests until the following day; automated validation continued.

Vigil's eight fixed slots include lightweight cached action-only images above the full-size hand rendering, preserving header and graph actions without duplicate clinical bitmap generation. Header/graph/clock active and ambient images share their original provider identities; previous ambient provider classes remain for compatibility. The exact behavior of the hidden center slot when Zeiger is off is not established by schema or unit tests and remains part of the deferred device test.

Sensor connection investigation: BLE connection and service discovery succeeded before an authentication-response processing failure (G7-AUTH-204). Its former rejection message did not distinguish local processing exceptions. Diagnostics now record only a fixed processing stage, exception class and sanitized stack identifiers; no protocol/retry/session/pairing behavior changed. No ANR was recorded. Successful sensor authentication remains unverified; neither sensor data nor pairing settings were reset.

2026-10-25 follow-up: the final installed artifacts were matched against the built APK hashes. Vigil graph-scale and glucose-header taps, center opacity toggle, and native Zeiger off/on were confirmed on the Galaxy Watch Ultra; scale/visibility were restored and the user's Orbit reselected. Mobile Watch-menu opening and preview rendering were checked without a crash. Direct collector authentication and consecutive fresh readings were subsequently verified; see docs/analysis/2026-10-25-g7-search-abort.md for the new discovery, scan-filter and detach corrections. Earlier hardware-open notes above describe the overnight handoff, not the final follow-up status.
