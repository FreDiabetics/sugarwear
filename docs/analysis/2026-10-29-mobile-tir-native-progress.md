# Mobile TIR overview / native progress bars — 2026-10-29

- Removed the three separate Loop, IOB/COB and Basal cards below the glucose tile. They were only shown by the TIR detail mode. Removed their private rendering helpers and obsolete mobile-only toggle/callback/preference projection. The shared loop presentation used by complication previews remains intact.
- The legacy `showDetails` preference is retained for existing Wear configuration compatibility; no user data or therapy history is cleared.
- The chart layout now reclaims the removed row's space without an empty spacer.
- TIR uses native Android horizontal ProgressBar widgets under the existing DeviceDefault theme, so Samsung supplies its OEM drawable. Clinical/user-selected above/target/below colors remain assigned through progress tints.
- Percent labels use the existing system-font One UI body style. All three columns reserve the measured width of `100%` under the current font scale; rows expand for text scaling. Accessibility provides the named range and known value, and explicitly describes missing data without inventing a percentage.
- No new polling or animation loop. Native progress animation is requested only when the integer percentage actually changes.

## Verification

The removed-switch regression first failed against the original UI, then passed. The broader run passed 49/50 tests, with the remaining pre-existing navigation test still expecting the old notification-category order and the former "Allgemein" title. Expectations were aligned to the already implemented user-requested category order and "Datenquellen" title; that test then passed. No production category reordering was made for this change.

Mobile APK built. Detekt, ktlint and final lint passed (`No issues found`). Installed on the main SM-S948B at 192.168.178.71:40723. The initial capture was locked. After user unlock, a fresh hardware capture (`mobile-tir-finalcheck.png` in local Temp) confirmed all three lower cards removed, the graph directly below the glucose tile, and three native slim bars with aligned, readable percentages. The active TIR selection was preserved.

## Visual follow-up

The user found the OEM horizontal drawable too thin for a Health-style overview. The TIR render now uses full-height 14dp rounded data bars with 24dp minimum rows, 8dp row spacing and 14sp bold system-font percentages. Proportional fill, user range colors, measured percentage columns and accessibility semantics are preserved. This is a Health-inspired data visualization, not a claim of exact Samsung Health geometry. It adds no polling or animation loop. Mobile assemble, Detekt and ktlint passed; updated APK installed.
