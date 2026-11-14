# Sugarlicious To-do

## Eigene Watchfaces: Complication-Slot-Sweep

- Für jedes Sugarlicious-eigene Watchface alle Complication-Slots vollständig und ausdrücklich definieren.
- Nicht pauschal jeden Slot für jeden Wear-OS-Complication-Typ freigeben.
- Pro Slot generische, geometrisch passende Renderer für alle sinnvoll unterstützbaren Typen vorsehen, insbesondere `SHORT_TEXT`, `LONG_TEXT`, `RANGED_VALUE`, `MONOCHROMATIC_IMAGE`, `SMALL_IMAGE`, `PHOTO_IMAGE`, `GOAL_PROGRESS` und `WEIGHTED_ELEMENTS`.
- Slotgröße, Position, Runddisplay-Sicherheit, Active/AOD-Darstellung und tatsächliche Provider-Kompatibilität einzeln prüfen.
- Maximale Individualisierung anbieten, ohne unpassende Typ-/Geometrie-Kombinationen zu deklarieren.
