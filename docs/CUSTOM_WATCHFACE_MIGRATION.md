# Custom Watchface Migration

## Implementierter Datenfluss

`AAPS-CWF-ZIP → Parser/Validator → CwfDocument → WFF-Generator → offizieller WFF-Validator`

Der Parser unter `tools/aaps-cwf-parser` liest genau eine
`CustomWatchface.json`, verlangt eine Vorschau, normalisiert das 400×400-Layout
und erfasst Ressourcen, Elemente und Funktionsmerkmale. Er blockiert ZIP-
Pfadtraversal, doppelte Dateinamen, Einzeldateien über 2 MiB, mehr als 10 MiB
entpackte Nutzdaten, falsche JSON-Typen und unvollständige Metadaten.

Der Generator unter `tools/wff-generator` erzeugt ein codefreies WFF-v1-Modell
mit höchstens acht Complication-Slots. Nicht abbildbare sichtbare Elemente und
Merkmale werden vollständig gemeldet. Ohne `--allow-degraded` wird in diesem
Fall keine Datei geschrieben; es gibt keine stille, unvollständige Konvertierung.

## Verifizierter Referenzlauf AAPS V2

- Quelle: offizielles AAPS-V2-ZIP aus AndroidAPSdocs, Blob `3c8b9c1b…`.
- Parser: gültig; 400×400 Pixel, 34 Layoutelemente.
- Erkannte Grenzen: dynamische Einstellungen und Twin-View.
- Generator: acht Slots; `avg_delta` wegen der WFF-Plattformgrenze von acht
  Slots ausdrücklich ausgelassen.
- Ergebnis: mit dem auf Commit `702e9bd` gepinnten offiziellen Google-WFF-
  Validator als WFF v1 bestanden.

Die manuell kuratierte AAPS-V2-Portierung bleibt das Releaseartefakt, weil sie
die dokumentierten Abweichungen bewusst gestaltet. Der Generator ist derzeit
ein validierter Migrationsstartpunkt, kein automatischer Paritätsnachweis für
beliebige CWF-Dateien. Analoge Zeiger, `dynData`, `dynPref`, Twin-View,
benutzerdefinierte Schriften und komplexe AOD-Regeln benötigen explizite
Mappings. Freitext-Elemente mit `textvalue` sowie getrennte sichtbare
Stunde/Minute/Sekunde werden inzwischen korrekt als Text beziehungsweise
WFF-DigitalClock erzeugt.

## Vollständiger Austauschplattform-Lauf

19 veröffentlichbare offizielle ZIPs wurden mit dem Parser gelesen. Vier
bekannte defekte Archive werden vorab durch das SHA-gepinnte Skript
`repair-known-official-cwf.ps1` reproduzierbar repariert. Alle 19 resultierenden
Quellen validieren. PinkFloydTheWall bleibt sowohl wegen des außerhalb der
akzeptierten Fläche liegenden Charts als auch wegen ungeklärter Drittrechte
blockiert und wird nicht generiert.
