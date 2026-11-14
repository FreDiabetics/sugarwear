# Wear therapy and settings implementation plan

> **For agentic workers:** Use superpowers:executing-plans; implement sequentially in the existing checkout, preserving unrelated changes.

**Goal:** Compact Mobile-sized Wear therapy rings, explicit BR/TBR details, one overview contour, visible version/release notes, useful metabolic scaling and Samsung-style scrolling settings headers.

**Architecture:** Reuse native Wear views and canonical BasalState. Reuse the Mobile metabolic renderer for Mobile/widgets; align activity to its IOB host axis without modifying samples. Share About presentation text, not app-specific versions.

**Tech Stack:** Kotlin, Android Views, existing Compose settings content, Robolectric.

**Spec:** User requirements in this chat, 2026-10-22, including observed Samsung Settings scrolling screenshots.

## Global constraints

- No collector, resolver, transport or dosing behavior changes.
- Unknown basal timing remains unknown; never invent elapsed duration.
- IOB/COB square cards side by side, 66dp rings matching Mobile. Basal ring left, BR and TBR details right; graph follows.
- Current source values and explicit static graph choices remain authoritative.
- Use existing worktree; no unrelated cleanup or bulk commits.

## Review focus

- Small round displays: equal square cards and no ring/text clipping.
- Missing, future or expired TBR: no fabricated percent, active rate or elapsed time.
- Dynamic viewport must not inherit configured static maxima; static maxima stay respected.
- Zero/negative insulin activity: aligned IOB zero, no invented curve, no changed data.
- Scroll restoration/navigation: header resets correctly and back action remains accessible.

### 1. Wear therapy layout and basal details
- [ ] Add tests for square card measurement and basal elapsed/total formatting, missing/expired timings.
- [ ] Implement native square cards and canonical basal detail formatter; keep Mobile-sized rings, fit text on smaller watches.
- [ ] Share overview tile border enabled/color with graph, remove duplicate graph-border setting.
- [ ] Run relevant Wear tests and build.

### 2. Metabolic scaling
- [ ] Test dynamic maxima ignore static floors; fixed modes retain configured maxima.
- [ ] Test activity zero maps to IOB zero, peaks stay within host-axis space and negative values remain bounded.
- [ ] Implement shared axis mapping following independently inspected AndroidAPS scaling principles; never copy source or synthesize samples.
- [ ] Run graph regression tests and regenerate affected widget previews.

### 3. About pages
- [ ] Add common runtime version lookup and product-specific recent feature summaries.
- [ ] Integrate Mobile, Wear and SugarWear About screens, no false collector feature claims.
- [ ] Verify labels and versions in tests/builds.

### 4. Scrolling Mobile settings header
- [ ] Add pure scroll-state tests: smooth title fade, floating back-circle and reset.
- [ ] Overlay native header above scrolling content, reuse native navigation; no blocking touch overlay or snap-only title replacement.
- [ ] Compare expanded/scrolled states with Samsung reference on the phone.

### Handoff
- [ ] Targeted tests/builds and relevant static checks, then install target packages on verified devices.
- [ ] Record hardware checks honestly; no completion claim for untested visual behavior.

## Execution evidence — 2026-10-22

- Implementation tasks above completed. Targeted Wear basal/layout, Mobile graph/header/widget preview and shared About tests passed. Broader MainActivity regression suite passed in the preceding run.
- Final three-app builds and Lint passed (`wear-ui-final-green.log`, BUILD SUCCESSFUL); generated therapy previews copied into resources and Mobile rebuilt successfully.
- Review caught percent-only TBR and 192dp text fitting; both corrected, regression-tested and reviewed again.
- Installed Mobile on SM-S948B (192.168.178.71:34101), Wear and About-only collector update on SM-L705F (192.168.178.68:35405); all installs returned Success.
- Watch screenshot confirms square side-by-side IOB/COB cards, wide basal card and complete BR/TBR elapsed/programmed text. Small-watch fitting additionally covered by layout test.
- Phone comparison confirms expanded title and intermediate fade; short inspected subpages do not scroll enough to expose the fully floating back circle, so that final visual state remains hardware-unconfirmed (state/navigation tests passed).
- Compared AndroidAPS activity/IOB mapping and Samsung Settings reference without changing user settings. Existing dynamic IOB/COB preferences preserved. Collector/data resolver/dosing logic untouched.
- No bulk commit, cleanup or branch mutation performed: existing unrelated dirty work preserved.

## Follow-up — linked widget graphs and final header check

- Wide glucose graph now uses the metabolic chart's effective contour radius, with equal 10dp top/start header inset.
- Native 4x2 glucose frame exposes a transparent header tap target; period changes update installed wide therapy graph instances too, including resized legacy providers. Other instance settings remain independent. Serialized period transactions prevent rapid taps from reordering linked changes.
- Native frame/linked-period regression tests demonstrated the missing tap target and unsynchronized fixed/legacy therapy providers before correction. Native frame, widget presentation and preview tests, Mobile build and Lint passed afterward (`widget-final-green.log`). Updated wide glucose picker preview packaged in a successful final resource build.
- Mobile installed successfully on SM-S948B. Real home-screen taps changed CGM and therapy together to 6h without opening the app; cycled back to 2h afterward. Rounded graph/header placement inspected on the actual widgets.
- Long overview/graph settings page scrolled on phone: title disappeared, content moved beneath the shaded header and circular floating back button remained fixed. Tapping that button navigated back. This closes the earlier hardware-unconfirmed header state.

## Follow-up — supplied therapy icons and wide Basal details

- Imported the latest `ic_basal_bckgrnd`, `ic_basalmore_bckgrnd`, `ic_basalless_bckgrnd` and `ic_iob_bckgrnd` SVG geometry from the user's App Icon folder. Widgets retain separately tintable circle/symbol layers; plain shared drawables omit the circle and normalize symbol bounds. Complication basal resource names now wrap the same shared symbols, avoiding stale duplicate geometry.
- BR/TBR formatter moved to core-model and reused by Wear and Mobile; the Wear wrapper preserves its callers. No collector, source resolution or dosing changes.
- Only the wide therapy widget moves the bottom Basal ring left and uses the spare column for base rate/percent and actual TBR elapsed/programmed time with background icons. Existing 2x1/2x2 layouts remain unchanged. Long detail rows wrap or fit inside their own clipped column.
- Regression tests first reproduced the old hollow IOB asset and missing duration display, then passed. Therapy worker rendering, icon tinting, BR/TBR formatting, picker previews and Wear icon checks passed. Mobile/Wear Lint and final APK builds passed (`basal-details-green.log`, `basal-details-final.log`, `basal-details-install-build.log`).
- Four therapy picker previews refreshed; About release notes updated. Mobile and Wear APKs installed successfully on SM-S948B / SM-L705F. Screenshots confirm full BR/TBR lines beside Basal on the real 4x2 home widget, colored background icons in widgets and circle-free new symbols in the Wear overview. No changes to user settings were needed.

## Follow-up — graph contour and time-label containment

- Root cause: the embedded metabolic Mobile view drew its own unconditional theme contour. Widget rendering now suppresses that built-in stroke and honors the existing per-instance graph contour toggle, width and GRAPH_OUTLINE palette role. Mobile's own chart keeps its prior default. The wide therapy settings section exposes the same contour controls/colorpicker as the CGM widget; outer widget contour remains independent.
- Root cause of embedded CGM time-label overflow: label placement accounted for the outer widget mask only and ignored the graph's own rounded background. Clamp now uses the measured ink at the actual baseline against both relevant contours, plus 1dp inner clearance.
- Failing-before/passing-after regressions cover contour toggle/color/thickness and rounded-background containment for bold 1h/24h labels. Graph-axis, therapy worker rendering and native picker preview tests, Mobile build and Lint passed (`widget-contour-axis-final.log`); final preview resource build passed too.
- Installed on SM-S948B. Actual widget settings verified: toggle reveals thickness and color; colorpicker includes HEX copy and reset. Draft test change cancelled, no saved appearance settings altered.
- Separate urgent device request: watch reported `lock 1`, accelerometer rotation 0 and user_rotation 1. Set `wm user-rotation lock 0`; immediately verified lock 0, accelerometer 0 and user_rotation 0. Watch later disconnected; no claim of a permanent Samsung charging-dock fix.

## Follow-up — 2026-10-23: smaller widget rings, Vigil alignment, Wear detail icons

- User clarified smaller rings refer to therapy widgets. Only ring diameter/stroke draw at 88%; value/icon placement, typography and right-hand BR/TBR details are unchanged. The unfinished request about right-hand text was not guessed.
- Vigil header now centers value+trend and secondary text on face x225 (local x135 in the existing x90 slot), including ambient mode. Pairing button uses that same center. The 400 label now shares the high/low label lane's left anchor rather than the plot edge. Collector/source logic untouched.
- Wear BR/TBR detail icons now use the colored-background basal assets (16dp) while other app/complication icons remain plain. Independent layer tint is applied after ImageView attachment so Android does not clear the chosen circle/symbol colors. Existing compact-watch icon hiding remains intact.
- Failing-before/passing-after tests cover ring ink bounds, active/ambient header centering, Vigil scale alignment and red-circle/black-symbol rendering. Graph/provider regressions and Wear layout tests passed; Mobile, Wear and G7 Lint passed (`vigil-therapy-final.log`, BUILD SUCCESSFUL). Native preview fixture uses Galaxy Watch density and the existing selector palette; no device preferences changed.
- Updated native Vigil face/header/graph previews and four therapy picker previews. Mobile/Wear/G7 APKs plus Vigil release APK built successfully (`vigil-therapy-install-build.log`). Mobile installed on SM-S948B. Phone was locked, so physical post-install inspection remains open.
- Watch installation remains pending: old Samsung 192.168.178.68:35405 timed out; discovered 192.168.178.77:35727 also rejected connection. No unverified device received an APK. Current watch WLAN-debugging address requested.
