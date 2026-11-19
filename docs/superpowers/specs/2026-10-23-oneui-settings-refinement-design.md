# One UI settings refinement

User approved autonomous implementation, after completed Orbit hands installation.

## Scope

Recently renewed Mobile/settings and all widget configuration pages only. Retain
existing data, graph, source resolver, collector and widget publication behavior.

- Large page title collapses to normal 20sp title beside back; it remains readable
  above the first card then scrolls out naturally. Back stays available and gains
  its circular surface only when scroll content reaches it. Every last card can
  reach the header through adequate bottom scroll space.
- Samsung-like Light/Dark illustrated radio card with a separated
  "Geräteabhängig" switch, following actual device mode. Explicit color palette
  editing still offers only Light/Dark and never changes the display mode.
- Reuse DashboardThemeMode for display choice. Widgets persist it per instance;
  existing instance migration preserves their previously inherited app choice.
- Replace textual per-setting reset controls by accessible reset/redo icon actions,
  in menus and inside color pickers; color resets remain inside color pickers.
- Glucose units use the screenshot's three-column separated radio-card style,
  without a switch. Keep source/mg-dL/mmol-L semantics and medical values unchanged.
- GB/US languages deferred under user's explicit conditional permission: current
  menus contain extensive hardcoded German, so a complete localization is not a
  small resource addition. Do not claim partial English as a language pack.

## Approach

Extend shared header geometry, introduce shared Compose choice/reset controls,
embed those in native settings using existing ComposeView bridges. Reuse native
theme colors, typography, switches and existing widget instance store. Avoid a
second settings framework or independently styled duplicate widgets.

## Verification

Focused geometry/persistence/mode tests, real control callbacks and accessibility,
regression for save/cancel and isolated instances. Build and relevant static checks,
then direct phone installation and visual checks if device remains available.
