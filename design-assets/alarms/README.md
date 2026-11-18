# Originale SugarWear-Alarmgrafiken

Quelle: Originalentwürfe für SugarWear und Vigil, bereitgestellt vom Nutzer
am 16. November 2026. Die SVGs sind Designquellen, keine Laufzeit-Screenshots.

- Vollbildreferenzen: `hoch fullscreen notification.svg`, `tief notification.svg`,
  `bald tief notification.svg`, `sensorfehler notification.svg`,
  `signalverlust notification.svg`, `bluetooth notification.svg`.
- Pausendialog: `datenempfang pausiert.svg`.
- Eigenständige Statusicons für die Glukosekachel: `ic_bluetooth.svg`,
  `ic_datenpausiert.svg`, `ic_sensorfehler.svg`, `ic_signalverlust.svg`.

Die vier Einzelicons werden mit ihrer ursprünglichen 66×66-Zeichenfläche in
`ic_status_*.xml` übernommen. Keine Kontur- oder Farbänderung und kein Tinting.
Die Vorlage `ic_bluetooth.svg` ist weiß; das blaue Vollbildicon stammt aus dem
Notification-Preview. Diese bewusst unterschiedlichen Originalfarben bleiben
erhalten.

`ic_artwork_*.xml` enthält ausschließlich isolierte illustrative Pfade und bei
Pause die zwei gelben Balken. Native TextViews und ein nativer Button übernehmen
alle Texte/Bedienung in `CgmAlarmFullBleedView`. Die ursprünglichen Positionen
werden auf die 450×450-Referenz skaliert. Bei gemeinsam definierten CSS-Farben
(`.cls-2, .cls-3`) gilt die Farbe für beide Klassen.

Die vorhandene validierte `URGENT_LOW_SOON`-Episode verwendet „Bald Tief“.
Für `RAPID_RISE`, `RAPID_FALL` und `TECHNICAL_WARNING` gibt es keine eigene
freigegebene Illustrator-Alarmvorlage; deren funktionierende bisherige
Alarmansicht bleibt erhalten. Bluetooth ist ein unabhängiger Sensorstatus und
kein zusätzlich erfundener Alarmtyp.

Die systemseitige Wear-OS-Benachrichtigung bleibt unter Kontrolle von Wear OS.
Ihr ContentIntent öffnet die native Alarmansicht. Kein Fullscreen-Intent und
keine Berechtigung für automatische Hintergrundstarts.

Rendering-Nachweis: `CgmAlarmArtworkTest`, Ausgabe unter
`app-sugarwear/build/reports/alarm-artwork/`. Native Systemglyphen unterscheiden
sich von den Illustrator-Schriftpfaden; Positionen, Gewichte und statische
Farben sind getrennt von den dynamischen Glukose-/Trendfarben.
