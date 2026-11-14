# Orbit daily TIR

Add one Arc option TIR, today plus previous four local calendar days, oldest left.
Reuse validated canonical history and configured CGM thresholds, never predictions.
Percent is time-weighted over observed intervals, each at most five minutes; do not
bridge gaps. Missing days show dash; today's value is partial. Preserve source,
sensor/session deduplication. Separate persisted six-day cache, bounded 9000 samples,
does not change resolver/live state or transport payload. Cache builds from existing
24h phone history and resolved Watch Direct; no fabricated older statistics.
Render transparent theme-tinted image in existing upper Arc geometry, day and whole
percent on two concentric curved tracks. Internal SMALL_IMAGE provider hidden from
general chooser; fixed slot only visible for Arc TIR. Existing slots remain intact.
No extra timers, network calls or BLE changes. Updates follow existing event/minute
complication refresh mechanism. Extend preview/manifest/tests; build and install
Wear + Orbit. User opted out of approval loops; implement inline.
# Follow-up 2026-10-24: persisted Samsung provider mismatch

## Daily TIR correctness and typography follow-up

Device archive inspection found only five readings from 23:37 onward for 22 October;
displaying 100% for that fragment incorrectly suggested a full day's statistic.
Percentages now require observed intervals to reach both ends of the local daily
period (today: midnight to now, five-minute sample tolerance). Missing periods
remain a dash; no retrospective values are manufactured. Covered minutes remain
available in the accessible description. A complete 23 October archive yields
about 90%, using the device's synchronized 80–160 mg/dL target.
Both rows use the shared explicit bold system-font policy. A single fixed image
provider is rendered through two radial masks: medium theme tone for weekdays,
light tone for percentages. No additional selectable complication is introduced.

Hardware completion: after registering the Android provider as ICON (WFF uses
MONOCHROMATIC_IMAGE), the stale favorite-240 binding was repaired through the
platform DEBUG_SURFACE set-complication operation for runtime slot 18, type 6.
Runtime logs confirm OrbitDailyTirComplication is activated and delivers type 6.
TEMP/tir-final.png confirms five curved weekdays and percentages/dashes. No other
providers or user style values were reset. Existing affected installations need
this one-time binding repair; XML validation does not perform that migration.

Hardware evidence: Orbit favorite 240 maps runtime slot 17 (WFF slot 9) to
OrbitGlucoseGraphComplication, and runtime slot 18 (WFF slot 8) to that same
provider. The new direct TIR slot was hoisted before the existing nested graph
slot. Samsung persists providers using these positional runtime IDs.

Correction: append new TIR after all existing hoisted slots so graph retains its
position. TIR uses MONOCHROMATIC_IMAGE, matching its theme-neutral alpha artwork
and rejecting the previously cached SMALL_IMAGE graph source. Provider manifest,
payload, static preview and WFF type change together. Existing favorite migration
must still be checked on hardware; generated XML alone does not prove it.

The Orbit marker stroke grows 4 to 5; its centered ellipse diameter grows by the
same amount to preserve an inner opening equal to the bar thickness. ApeX marker
strokes likewise grow by one, with expanded drawing bounds to prevent clipping.

Completed follow-ups: captured ApeX/Orbit provider/style templates; segmented
ApeX bars with indicators; native round watchface previews; corrected Vigil
preview sizing and SugarWear-only caption. The Watch menu has a bounded list,
fixed overview/back area and pushing sticky section titles on the page colour.
Complication previews use dark-grey surfaces, grey titles, white values, smaller
type, proportional arrows and canonical therapy icons. The preset strip displays
the chosen previews rather than labels; the separate trend demo is removed.

Orbit range labels are bold, right-aligned, high above / low below the guides,
drawn before the dots. ApeX retains guides without labels. The lower Orbit gauge
has no indicator. The final upper-right segment is longer and its caption moves
six degrees left. AOD keeps the selected graph dimmed, suppresses the graph-mode
background panels and never re-enables the full-dial glow. The canonical grey
seconds-hand hub remains visible in AOD without a frozen seconds hand.

Hardware evidence: synchronized 80/160 targets and archive coverage inspected;
live Orbit labels, missing-fragment TIR, removed lower marker, and Watch menu
fixed overview/pushing section titles checked. Rotation was found locked at 90
degrees and restored to a stable 0-degree lock. Ambient rendering is protected by
generated WFF contracts; a physical ambient screenshot was not obtained while the
charging screen kept the watch awake. Native previews contain example data, not
live clinical readings.
