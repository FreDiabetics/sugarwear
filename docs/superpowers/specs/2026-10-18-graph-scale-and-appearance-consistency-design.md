# Graphskalen und systemweit konsistente Appearance-Einstellungen

## Ziel und Umfang

Dieser Auftrag korrigiert die IOB-/COB-Achsenbeschriftung, erhält Predictions in einer vollständig herausgezoomten 24-Stunden-CGM-Ansicht, vereinheitlicht gemeinsame Darstellungsoptionen systemweit anhand der Mobile-App und positioniert Widget-Skalenwerte bündig an der Jetzt-/Skalengrenze.

Die Vereinheitlichung umfasst Appearance-, Graph-, Farb- und Geometrieeinstellungen. Fachlich eigenständige Einstellungen für Collector, Sensor, Alarme, Hintergrundbetrieb und Datenquellen bleiben außerhalb dieses Vertrags.

## IOB-/COB-Achsenvertrag

- Kurve, Ereignismarker, Achsentick und Achsenwert verwenden dieselbe `GraphAxisScale` und dieselbe `mapAxisY`-Abbildung.
- Der Maximal-Tick wird exakt auf `mapAxisY(maximum)` gezeichnet; der Minimal-Tick exakt auf `mapAxisY(minimum)`.
- Die Text-Baseline wird aus den Fontmetriken um diesen Tick zentriert. Sie darf nicht selbst die fachliche Tickposition definieren.
- Der Nullwert bleibt exakt an `mapAxisY(0)`.
- Der Vertrag gilt identisch für IOB und COB, unabhängig von linearer, dynamischer oder logarithmischer Skalierung sowie vom fließenden Wechsel der Skalenseite.
- Bolus- und Kohlenhydratbeschriftungen bleiben Ereignismengen. Ihre Markerposition folgt weiterhin dem zugehörigen Kurvenwert; sie werden nicht fälschlich als Achsenticks interpretiert.

## 24-Stunden-Predictions

- Wenn der sichtbare CGM-Zeitraum die vollständige 24-Stunden-Ansicht erreicht, bleiben aktivierte Predictions sichtbar.
- Das gilt sowohl nach Auswahl der 24-Stunden-Stufe als auch nach vollständigem Pinch-Zoom auf 24 Stunden.
- Die Zukunftsbreite wird zusätzlich zum 24-Stunden-Historienfenster dargestellt und reduziert die historische Abdeckung nicht.
- In diesem Zustand steht der Skalabereich links, damit die Predictions rechts vollständig sichtbar bleiben.
- Unterhalb der vollständigen 24-Stunden-Ansicht bleibt der vorhandene Live-Übergang unverändert: Wenn Predictions aus dem sichtbaren Bereich gescrollt werden, bewegt sich der Skalenbereich fließend von rechts nach links.
- Deaktivierte Predictions werden weiterhin nicht gezeichnet und erzwingen keine Zukunftsbreite.

## Kanonischer Appearance-Katalog

Gemeinsame Optionen werden über einen zentralen Katalog beschrieben. Ein Eintrag definiert mindestens:

- stabile semantische ID;
- verbindliche deutsche Bezeichnung aus der Mobile-App;
- Control-Typ;
- Wertebereich, Schrittweite und sichtbare Formatierung;
- Vorschau-Semantik;
- unterstützte Oberflächen.

Beispiele für verbindliche Begriffe sind `Hoch-Grenzlinie`, `Sehr-hoch-Grenzlinie`, `CGM-Dot-Kontur`, `Konturstärke historische CGM-Dots` und `Konturstärke aktueller CGM-Dot`. Derselbe semantische Wert darf in Widget-, Tile-, Complication-, Wear- oder SugarWear-Einstellungen nicht anders benannt werden.

Oberflächenspezifische Persistenz bleibt erhalten. Der Katalog vereinheitlicht Bedeutung und Präsentation, nicht die Besitzer der jeweiligen Einstellungen.

## Gemeinsame Controls

- Alle Appearance-Colorpicker verwenden dieselbe Farbauswahl, Alpha-Unterstützung, Vorschau und Reset-Semantik wie Mobile.
- Alle Appearance-Slider verwenden denselben Aufbau, dieselbe Beschriftung, denselben Wertebereich, dieselbe Schrittweite und dieselbe Werteanzeige wie Mobile.
- Alle Appearance-Dropdowns verwenden denselben Aufbau, Auswahlzustand und dieselbe Vorschau wie Mobile.
- Die CGM-Dot-Konturstärken verwenden überall den Mobile-Bereich `0,25–3,0 dp` und dieselbe Schrittweite.
- Vorschauen verwenden die produktive Auflösung der jeweiligen Einstellungen und denselben Rahmen-/Hintergrundvertrag; sie dürfen keine abweichenden Demo-Defaults besitzen.
- Plattformspezifische Layouttechnik darf unterschiedlich sein, das beobachtbare Control-Verhalten jedoch nicht.

## Widget-Skalenwerte

- Hoch- und Tiefwert im rechten Widget-Skalenbereich werden mit dem Mobile-Graph-Abstand direkt an der Jetzt-/Plotgrenze positioniert.
- Die Werte bleiben vollständig lesbar und kollidieren weder mit Kontur noch Widget-Rand.
- Die Korrektur gilt für alle produktiven Mobile-Widgets mit CGM-Graph und deren Settings-Vorschauen.

## Migration und Kompatibilität

- Schlüssel und gespeicherte Nutzerwerte werden nicht allein wegen einer Umbenennung verändert.
- Abweichende Wertebereiche werden beim Lesen sicher in den kanonischen Bereich begrenzt; gültige bestehende Werte bleiben erhalten.
- Wenn eine alte Oberfläche nur eine gemeinsame Konturstärke besaß, dient sie weiterhin als Migrationsquelle für die getrennten kanonischen Stärken.
- Keine zweite parallele Appearance-Architektur wird eingeführt.

## Tests

Automatisierte Regressionen prüfen mindestens:

1. IOB-Maximum, -Minimum und Nulltick stimmen pixelgenau mit `mapAxisY` überein.
2. Dasselbe gilt für COB und alle unterstützten Skalenmodi.
3. Ereignislabels ändern die Achsenskalierung oder Tickposition nicht.
4. Aktivierte Predictions bleiben bei exakt 24 Stunden sichtbar und die Skala steht links.
5. Auswahl der 24-Stunden-Stufe und Pinch-Zoom liefern denselben Zustand.
6. Unterhalb 24 Stunden bleibt der bestehende fließende Skalenübergang unverändert.
7. Gemeinsame semantische Settings besitzen in allen Oberflächen denselben Namen und dieselben Control-Metadaten.
8. Colorpicker, Slider, Dropdowns und Vorschauen erfüllen ihren gemeinsamen Vertrag.
9. Konturstärken sind überall im Bereich `0,25–3,0 dp` identisch einstellbar.
10. Widget-Hoch-/Tiefwerte liegen bündig an der Jetzt-/Plotgrenze und bleiben innerhalb der sichtbaren Fläche.

## Definition of Done

- IOB- und COB-Skalenwerte sitzen an der fachlich richtigen Kurvenhöhe.
- Die 24-Stunden-Ansicht enthält aktivierte Predictions und verwendet die linke Skalenspalte.
- Der vorhandene dynamische Rechts-/Links-Übergang bleibt für alle anderen Viewports erhalten.
- Gemeinsame Appearance-Einstellungen heißen und funktionieren systemweit identisch zur Mobile-Referenz.
- Widget-Skalenwerte stehen bündig an der Jetzt-Linie.
- Relevante Tests, Debug-/Release-Builds, Lint, Detekt und ktlint sind grün.
- Mobile, Sugarlicious Wear und SugarWear werden anschließend auf den verbundenen Geräten installiert; reale Renderprüfung wird als Hardwarevalidierung getrennt dokumentiert.
