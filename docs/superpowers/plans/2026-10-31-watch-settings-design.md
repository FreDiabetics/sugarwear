# Watch Settings Design

Priority: implement the visual redesign for Sugarlicious Wear and SugarWear before the deferred general refactoring audit. Keep all settings, values, collector behavior and alarms intact. No deletion of functionality.

Use the existing shared public Android controls, with watch-specific rounded list surfaces, 48dp targets, system-scaled typography, neutral black/gray menu chrome and Sugarlicious accent for selections. Remove phone-style switch dividers and cramped multi-column choices. Group navigation rows logically with dividers; let text wrap at large system scale. Maintain gesture-safe switches/sliders and bounded crown/touch scrolling. Apply the same vocabulary to color/value dialogs and every Wear settings activity.

Sources: Samsung One UI 9 Watch Design Story (2026-09-18), Samsung One UI design principles, Android Wear toggle chips/sliders/typography. These are public design references, not a claim of access to Samsung private controls or exact private dimensions.

1. Update and verify common Wear controls in ui-shared, including adaptive page margins and accessible choice/list components.
2. Apply shared components to Sugarlicious Wear settings and its submenus.
3. Commit shared components canonically in Sugarlicious and update SugarWear submodule pin; apply to SugarWear display, Vigil, permissions, sensor and alarm pages.
4. Run focused settings/control/gesture tests, affected APK builds, lint/format/static checks. Inspect round 192/227dp and large fonts. Install the affected Wear apps only and inspect reachable hardware without changing settings or sensor associations.
5. Report completed design scope and any outstanding device-only checks honestly. Resume broad code audit separately; deletion requires explicit approval of the inventory.
