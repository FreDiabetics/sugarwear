# Systemweite CGM-Punktkonturen und transparente Sehr-hoch-Grenzlinie

## Ziel

Alle produktiven Sugarlicious- und SugarWear-Graphen verwenden denselben Vertrag für echte CGM-Punkte. Der jeweils aktuelle Punkt besitzt immer eine sichtbare Kontur. Historische Punkte können optional konturiert werden. Beide Konturstärken sind unabhängig einstellbar.

Vorhersagepunkte sind ausdrücklich nicht Teil dieser Änderung und behalten ihre vorhandenen Einstellungen.

## Verbindlicher Darstellungsvertrag

- Der aktuelle echte CGM-Punkt wird immer mit Kontur gezeichnet.
- Für den aktuellen Punkt existiert kein Aktivierungsschalter mehr.
- Historische echte CGM-Punkte besitzen einen eigenen Ein-/Aus-Schalter.
- Die Konturstärke historischer Punkte ist separat einstellbar.
- Die Konturstärke des aktuellen Punkts ist separat einstellbar.
- Beide Stärken werden im bestehenden sicheren Bereich von 0,25 bis 3,0 dp begrenzt.
- Farbe, Punktradius und Prediction-Darstellung bleiben unabhängig von diesem Vertrag.
- Ein veralteter gespeicherter Wert, der die aktuelle Kontur deaktiviert, darf die feste Produktregel nicht überstimmen.

## Betroffene Oberflächen

Die Änderung wird durch alle tatsächlich verwendeten Graphpfade gezogen:

- Sugarlicious Mobile In-App-CGM-Graph
- Mobile Widgets mit CGM-Graph, einschließlich 2x2-Kombinationswidget
- eingeklappte und ausgeklappte Mobile-Benachrichtigungsgraphen
- Mobile Complication- und Watchface-Graphvorschauen, soweit sie echte CGM-Punkte zeichnen
- Sugarlicious Wear In-App-Graph
- Sugarlicious Wear CGM-Graph-Tile
- SugarWear/G7 In-App-Graph
- SugarWear/G7 CGM-Graph-Tile
- gemeinsam genutzte Wear-Graph-Renderer und alle darauf aufbauenden Darstellungen

Reine dekorative Vorschauen ohne produktive Graph-Settings erhalten keine eigene Parallelkonfiguration. Sie verwenden die zugehörige produktive Graph-Policy.

## Gemeinsames Modell

Die Graphdarstellung erhält folgende kanonische Felder:

- `historicalDotOutlineEnabled: Boolean`
- `historicalDotOutlineWidthDp: Float`
- `currentDotOutlineWidthDp: Float`

Ein lesbares Legacy-Feld für `currentDotOutlineEnabled` darf während der Migration erhalten bleiben, wird beim Auflösen aber immer als `true` behandelt und anschließend nicht mehr als Nutzeroption geschrieben. Ein gemeinsames altes `dotOutlineWidthDp` bleibt ausschließlich Migrationsquelle, nicht langfristige Renderquelle.

## Migration und Kompatibilität

- Fehlen die neuen getrennten Stärken, wird die vorhandene gemeinsame Konturstärke für beide übernommen.
- Fehlt der historische Aktivierungswert, wird der bisherige allgemeine Aktivierungswert übernommen; ohne Altwert gilt `true`.
- Ein alter deaktivierter Wert für die aktuelle Kontur wird ignoriert; die aktuelle Kontur ist nach dem Update aktiv.
- Mobile-Wear-Transport bleibt abwärtsverträglich: ältere Nutzdaten werden mit den Legacy-Feldern gelesen, neue Nutzdaten enthalten die getrennten Stärken.
- Gespeicherte Farben, Punktradien, Graphskalen und Prediction-Einstellungen bleiben unverändert.

## Settings-Oberflächen

Jeder Graph-Settingsbereich mit echten CGM-Punkten zeigt konsistent:

1. Schalter `Historische Punktkontur`
2. Regler `Konturstärke historische Punkte`
3. Regler `Konturstärke aktueller Punkt`

Der historische Stärkenregler kann bei deaktivierter historischer Kontur ausgeblendet oder deaktiviert werden. Der Regler für den aktuellen Punkt bleibt immer sichtbar. Ein Schalter für die aktuelle Kontur wird entfernt.

## Rendering

Renderer bestimmen den aktuellen Punkt über den neuesten kanonischen echten CGM-Wert im sichtbaren Datenbestand. Prediction-Punkte dürfen nicht als aktueller echter Punkt gelten.

Für jeden echten Punkt gilt:

- aktuell: Kontur immer mit `currentDotOutlineWidthDp`
- historisch und aktiviert: Kontur mit `historicalDotOutlineWidthDp`
- historisch und deaktiviert: keine Kontur

Layout-Innenabstände berücksichtigen die größere der beiden wirksamen Konturstärken, damit Punkte und Konturen nicht abgeschnitten werden.

## Tests

Automatisierte Regressionen prüfen mindestens:

- aktuelle Kontur kann nicht deaktiviert werden
- historischer Schalter wirkt nur auf historische Punkte
- getrennte Stärken werden korrekt gerendert
- aktueller Punkt wird unabhängig vom historischen Schalter konturiert
- Prediction-Punkte bleiben unverändert
- Legacy-Migration übernimmt gemeinsame Stärke in beide neuen Felder
- alter deaktivierter aktueller Schalter wird zu aktiv migriert
- Persistenz für Mobile, Widgets, Benachrichtigungen, Wear und G7
- Mobile-Wear-Transport mit neuen und alten Nutzdaten
- alle Settings zeigen historische Aktivierung und beide Stärken, aber keinen aktuellen Aktivierungsschalter
- Punkt- und Kontur-Clipping bleibt auf kleinen Widget-, Tile- und Watchflächen korrekt

## Validierung und Installation

Nach Umsetzung werden die relevanten Unit-, Integrations-, Renderer-, Persistenz- und Transporttests sowie Mobile-, Wear- und G7-Builds, Lint, Detekt und ktlint ausgeführt. Anschließend werden Mobile sowie die betroffenen Wear-Apps auf den verbundenen Geräten installiert. Reale Renderprüfung wird getrennt von Code- und Buildvalidierung dokumentiert.

## Nicht im Umfang

- Änderung der Prediction-Punktkonturen
- Änderung von CGM-Punktfarben oder Punktradien
- Änderung von Graphskalen, Datenquellen oder Collectorlogik
- Einführung eines zweiten parallelen Graph-Settingssystems

## Mobile Sehr-hoch-Grenzlinie

Das Patchpaket behebt zusätzlich den Defekt, dass die Grenzlinie `Sehr hoch` im produktiven Mobile-CGM-Graph trotz einer konfigurierten Transparenz von 100 Prozent sichtbar bleibt.

Verbindlicher Vertrag:

- Die im Colorpicker gespeicherte Alpha-Komponente von `GRAPH_VERY_HIGH_LINE` wird unverändert bis zum produktiven Canvas übertragen.
- Alpha `0` bedeutet vollständig unsichtbar; es darf weder eine Mindestdeckkraft noch einen Farb-Fallback geben.
- Die Transparenz der Sehr-hoch-Fläche und die Transparenz der Sehr-hoch-Grenzlinie bleiben unabhängige Einstellungen.
- Die anderen Grenzlinien behalten ihre jeweils eigenen Farben und Alpha-Werte.
- Wiederverwendete `Paint`-Objekte dürfen keine Alpha-Werte früherer Zeichenoperationen in eine Grenzlinie übernehmen. Farbe und Alpha werden vor jeder Grenzlinie vollständig definiert beziehungsweise nach temporären Alpha-Operationen wiederhergestellt.
- Colorpicker-Vorschau, Persistenz und produktiver Mobile-Graph müssen denselben ARGB-Wert verwenden.

Zusätzliche Regressionstests prüfen:

- `GRAPH_VERY_HIGH_LINE` mit Alpha `0` erzeugt keine sichtbaren Pixel an der Sehr-hoch-Grenze
- eine teilweise transparente Linie behält exakt ihre konfigurierte Alpha-Komponente
- eine opake Linie bleibt opak
- Sehr-hoch-Fläche und Sehr-hoch-Grenzlinie beeinflussen sich nicht gegenseitig
- nach Zeichenoperationen mit temporärem `Paint.alpha` bleibt die Grenzlinien-Transparenz deterministisch
