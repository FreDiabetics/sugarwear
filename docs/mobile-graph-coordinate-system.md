# Mobile graph coordinate system

The Sugarlicious mobile CGM and metabolic graphs render timed content through one
`ChartViewport` snapshot per frame. The snapshot defines the visible start and end
timestamps and the immutable clock/prediction boundary. History, predictions, target
history, therapy markers, activity, the time axis, and the divider all map timestamps
through that same interval. The divider is never clamped to a screen edge, so a manual
historical viewport can move it completely off-screen.

`ChartViewport` has two explicit modes. `LIVE_FOLLOW` advances with the clock.
Horizontal navigation or focal-point zoom switches to `USER_NAVIGATING` and preserves
the selected end timestamp across new data. Only `returnToNow()` resumes live following.

The CGM graph resolves one `CgmGraphYScale` per render from the active mode:

- `STATIC`: configured linear minimum and maximum.
- `DYNAMIC`: one linear range from visible history, visible predictions, and targets.
- `LOGARITHMIC`: configured positive minimum and maximum with a real logarithmic transform.
- `LOGARITHMIC_DYNAMIC`: the same dynamic input set with a logarithmic transform.

Dynamic ranges include padding, expand immediately when required, and retain the prior
range during small contractions to avoid visual jitter. The resolved scale is shared by
CGM history, CGM predictions, target bounds, target values, and their labels. Insulin
activity has its own unit axis, but its actual and projected portions are combined and
smoothed once before being split at one boundary point, guaranteeing the same boundary Y.

Prediction samples retain their real timestamps. An existing sample exactly on the clock
boundary is canonical; otherwise a boundary sample is interpolated only when valid samples
straddle the boundary. No stale point is visually dragged to the divider and duplicate
boundary points are removed.

Fixed controls and labels remain screen UI. Timed lines, dots, markers, events, target-value
segments, prediction content, and the prediction divider are graph content and therefore
pan and zoom together.
