# AAPS Complication Inventory

Die aktuelle AAPS-Baseline manifestiert 23 Provider:

| Gruppe | Typen |
|---|---|
| LongStatus, LongStatusFlipped | LONG_TEXT |
| Sgv, SgvLarge, SgvExt1, SgvExt2 | SHORT_TEXT |
| BgGraph | SMALL_IMAGE, LARGE_IMAGE |
| BrCobIob und Ext1/2, BrTt, Target, BrIob, Br | SHORT_TEXT |
| CobIob, CobIcon, CobDetailed, IobIcon, IobDetailed | SHORT_TEXT |
| UploaderBattery | RANGED_VALUE, SHORT_TEXT, ICON |
| Wallpaper Light/Dark/Gray | LARGE_IMAGE |

Die unabhängige Wear-App manifestiert 35 getrennte Provider und deckt
`SHORT_TEXT`, `LONG_TEXT`, `RANGED_VALUE`, `MONOCHROMATIC_IMAGE`,
`GOAL_PROGRESS`, `WEIGHTED_ELEMENTS`, `SMALL_IMAGE` und `LARGE_IMAGE` ab.
Jeder Provider liefert genau einen Typ; Varianten derselben Datenquelle werden
als eigene Complications angeboten. Bildprovider rendern Glukose oder den auf
der Uhr gehaltenen Verlauf. Primäre Typen, Ranged Values, stale-Sperren und
Preview-Daten sind automatisiert getestet. Alle 35 Provider liefern eine
Picker-Vorschau; die Glukosefamilie besitzt zusätzlich statische Preview-Daten.
Wear-OS-6-Goldens
zeigen den bis Version 0.5.1 veröffentlichten Stand; die neuen 0.6.0-WFF-
Varianten benötigen noch einen neuen visuellen Hardwarelauf.
