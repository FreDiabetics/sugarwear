# Mobile glucose value and dot colors

The live overview translated HIGH/LOW/IN_RANGE widget roles into mobile palette roles but omitted VERY_HIGH and URGENT_LOW, falling back to TEXT_PRIMARY at the urgent boundaries. The mapping now covers all five canonical classes, preserving user palettes, thresholds and stale styling. A regression failed on the previous urgent mapping and passes after correction.

CGM dot classification already selected independent HIGH and VERY_HIGH colors. However, GlucoseDashboardChart.bind skipped invalidation when only the runtime palette or configured thresholds changed. It now tracks those immutable inputs along with data/style/clock state. A native rendering regression failed before this fix at the repaint assertion, and now proves repaint plus visible distinct high/very-high dot fills without a new reading or clock tick.

Eleven focused overview/pixel-rendering/customization tests, Mobile debug build, Detekt and Lint passed. Installation and real-device visual verification were explicitly deferred by the user while away from the computer. No collector code was changed.
