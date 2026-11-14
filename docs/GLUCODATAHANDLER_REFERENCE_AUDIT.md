# GlucoDataHandler Reference Audit

Baseline: `114460bc29973c78580095e2e9b0f212eed9df20`, MIT.

GDH stellt 38 manifestierte Provider bereit: Glukose als Short/Ranged/Text/Icon/Small/Large Image, Kombinationen aus Wert/Trend/Delta/Zeit, einzelne Trend-/Delta-/Zeit-Provider, Smartphone- und Uhrenbatterie, IOB/COB, andere Einheit, Patientenname sowie quadratische und rechteckige Diagramme.

Wesentliche Muster:

- Text-Provider liefern semantische Daten; Bildprovider rendern kontrollierte Optik.
- Glukose kann als `RANGED_VALUE` mit Zielbereichsbezug geliefert werden.
- Trend, Delta und Datenalter existieren einzeln und kombiniert.
- Diagramme werden als Bild-Complications ausgegeben, weil das Ziel-Watchface sonst keine Kurve zeichnen kann.
- Fehlende/veraltete Daten werden in einer gemeinsamen Datenhaltung behandelt; Konfiguration beeinflusst Farben, Zielgrenzen, Einheit und Tap-Aktion.

Es wurde kein GDH-Code kopiert. Der aktuelle Prototyp übernimmt nur das öffentlich erkennbare Produktmuster „semantische plus bildbasierte Provider“ und besitzt eigene Modelle/Implementierung. Falls später MIT-Code übernommen wird, werden Datei, Commit und Copyright in `LICENSES/` einzeln dokumentiert.

## Verbindliche Designabgrenzung

GlucoDataHandler ist keine Designquelle für Smartphone-Oberfläche, Graphen,
Farben oder Kacheln. Die Mobile-Optik folgt ausschließlich der gelieferten
Bildvorlage; fehlende technische Graphdetails werden anhand des Nightscout Data
Toolkits ergänzt. Insbesondere werden keine GDH-Bereichsfarben oder
grenzwertabhängigen Glukosefarben in das Dashboard übernommen. Die grüne
CGM-Serie und das Zielband sind feste Bestandteile der Bildvorlage.
