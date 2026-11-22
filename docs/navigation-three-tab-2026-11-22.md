# SugarWear – drei Hauptbereiche (Entwicklung in dev)

## Ziel
- Die existierende SugarWear-Übersicht mit Glukose- und Verlaufskachel bleibt unverändert; nur der bisherige separate Einstellungen-Button wird durch die dauerhafte Navigation ersetzt.
- Eine gemeinsame Icon-Pille mit drei nebeneinanderliegenden 48-dp-Touch-Zielen führt zu **Übersicht**, **Verbindungen** und **Profil**. Sie steht **am scrollbaren Ende jeder App-Menüseite** (auch in Unterkategorien), niemals als am Bildschirmrand fixierter Footer. Beschriftungen werden ausschließlich bei langem Drücken als kurzzeitige Toast-Hinweise angezeigt; für Screenreader sind die Tasten dauerhaft beschriftet.
- Er verdeckt weder die Alarmvollbildansicht noch den Berechtigungs-/Ersteinrichtungsassistenten. System-Berechtigungsdialoge bleiben Plattformoberflächen und dürfen nicht ersetzt werden.
- Es gibt weiterhin nur eine Sensorzustandsverwaltung, eine Alarm-Engine und einen BLE-Collector.

## Navigation und Verantwortlichkeiten
| Bereich | Inhalt |
|---|---|
| Übersicht | Vorhandene Glukosekachel und Verlauf, unveränderte Pairing-/Alarmfunktionen |
| Verbindungen | Sensorstatus, zehn Segmente bis zum verifizierten Sensor-Endzeitpunkt, Restzeit in Tagen/Stunden/Minuten, Zugriff auf bestehende Sensorverwaltung, funktionierender Share-&-Follow-Einstieg, nicht anklickbarer Health-Connect-Platzhalter |
| Profil | Alarme zuerst; In-App-Unterkategorien, Tile-Unterkategorien, Vigil-Unterkategorien; danach Berechtigungen und Über |
| Unterseiten | Bestehende Activity- und ViewModel-Strukturen behalten ihre wirksamen Einstellungen. Gemeinsame Icon-Pille als letzter Eintrag im jeweiligen scrollbaren Seiteninhalt |

Der Sensor-Endzeitpunkt stammt ausschließlich aus dem gespeicherten aktiven Sensor bzw. einem dazugehörigen Messwert aus derselben Sensor-Session. Ist er unbekannt, werden keine Resttage oder aktiven Segmente erfunden. Segmente zeigen aufgerundete, verbleibende Sensortage mit maximal zehn Markierungen.

## Umsetzungsschritte und Prüf-Gates
1. Gemeinsame Scroll-End-Navigation in Übersicht und bestehendem Settings-Compose-Host; alte fixierte Footer entfernen und keinen separaten Navigationsdienst oder State-Resolver anlegen.
2. Verbindungen mit Sensor-Laufzeitfunktion und echten Links in die vorhandenen Sensor-/Share-Funktionen. Health Connect als ausdrücklich deaktivierten Platzhalter ausweisen.
3. Profil-Kategorien neu gruppieren; In-App- und Tile-Kategorien durch Scoping des bestehenden Appearance-Menüs trennen, keine Einstellungsduplikate.
4. Manifest, SVG-/Vector-Assets und Regressionstests aktualisieren. Erst nach erfolgreichen Tests und CI mögliche weitere UI-Feinheiten beurteilen.
5. Auf Samsung Galaxy Watch und Pixel Watch prüfen: 192dp/227dp rund, Scrollen ohne verdeckte Karten, Icon-Pille als letztes scrollbares Element aller Untermenüs und Long-Press-Beschriftungen, Back-Navigation, Sensorwechsel, unbekanntes Sensorende, aktiver Share-Status, AOD, Alarm-Overlay und Berechtigungsassistent.

**Ungeprüft:** Geräte-Rendering und tatsächliche Navigations-/Pairing-Abläufe, solange keine Uhrmessung vorliegt. `main` bleibt unverändert.
