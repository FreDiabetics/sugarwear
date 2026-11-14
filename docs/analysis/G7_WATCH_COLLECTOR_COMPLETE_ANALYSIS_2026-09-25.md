# G7 Watch Collector Komplettanalyse — 2026-09-25

## Analysezeitraum

- Belastbare Hardware-Baseline: `210112880ce1dd503c4e0a60197509c77478d530` (43 h 57 min Hardwareanalyse bis 2026-09-06).
- Analysierter Stand: `d588a2660a0903a1c0781d1d89256cd3e0e1f7fd` (`main`).
- `origin/main`: `1919ace0995cbae245353319556926fb667b4272`, vor Beginn frisch abgerufen und vollständig in lokalem `main` enthalten.
- Wesentliche Änderungen: kanonische Backfill-Identität, persistentes Expected-Window-Ledger, deterministische Gap-Auswahl nach LIVE-Reconnect, Runtime-Reconciliation, Sensor-Unlink, GATT-Generationen, Resolver-/Alarm-/UI-Fanout und Background-/Manifest-Hardening.

## Tatsächlicher Runtime-Pfad

`AlarmManager/Advertisement → Receiver → G7CollectorService → AndroidG7Ble → Scan/Direct Connect → GATT generation → Discovery/Notifications/Auth → LIVE packet → validation/toCgm → G7ReadingDatabase → G7ExpectedWindowLedger → G7LocalReadingResolver/CanonicalCgmSourceResolver → alarms/UI/tiles/complications/watchfaces`.

Gap-Pfad: `terminal expected window → persistent ledger → oldest same sensor/session gap → next successful LIVE session → bounded Backfill request → validation → canonical insertOrIgnore → ledger closure`.

## Root Causes und Änderungen

1. Wechselnde `NO_CALLBACK`-/Fallback-Zyklusformen setzten die aus Attempt-Formen rekonstruierte Radiofehlerfolge zurück. Der Zähler wird nun aus `G7CollectorHealth` fortgeführt; ab degradiertem Cluster werden Scanner und app-eigener Callback-Looper rotiert. Regression: `G7BlePolicyTest`.
2. Bis zu 2.304 vollständige Attempts mit je bis zu 40 Events sowie 2.304 Ledger-Zeilen wurden bei jeder Änderung als große SharedPreferences-JSONs neu geschrieben. Abgeschlossene Attempts werden kompakt auf 192 begrenzt; das Ledger behält 300 technisch recoverable offene und 192 aktuelle geschlossene Fenster. Regressionen: `G7CollectorDiagnosticsTest`, `G7BackfillOrchestratorTest`.
3. Backfill speicherte jeden Datensatz mit eigenem Provider-/Broadcast-/Tile-Fanout. Die bestehende Einfüge-/Deduplogik bleibt unverändert, Benachrichtigungen werden pro Batch zusammengeführt. Regression: `G7ReadingDatabaseTest`.

## Historische Probleme am heutigen Code

- Galaxy Watch Ultra längere Recovery: Code-seitig plausibel verbessert; Hardwaretest offen.
- LIVE vorhanden, Backfill nicht ausgelöst / ältere Gaps offen: behoben und durch Ledger-/Recovery-Tests belegt.
- lange LIVE-Ausfälle, 609-Minuten-Ausfall, Pixel Watch findet Sensor nicht: nicht ausreichend allein code-seitig belegbar; Hardwaretest offen.
- GATT 133: begrenzte Retries, Cleanup/Backoff vorhanden; Hardwaretest offen.
- `NO_CALLBACK`: weiterhin reale Plattformfehlerklasse; die belegte Zähler-/Runtime-Stagnation ist behoben, Hardwaretest offen.
- Fallback Scan: begrenzt und nur nach recoverable Direct-Failure; Hardwaretest offen.
- Sensorwechsel erforderte App-Daten-Löschung: Unlink beendet Collector/GATT/Reconnect/Session und erhält History; code-seitig plausibel behoben, Hardwaretest offen.

## Integrität und Folgekomponenten

- Identität: Sensor + Session + `measuredAt`/Status; LIVE wird gegenüber überlappendem BACKFILL bevorzugt.
- Freshness und Signalverlust basieren auf Messzeit, nicht Empfangszeit oder BLE-Disconnect.
- Resolver bleibt getrennt: Mobile bis >15 min, Watch Direct nur frisch, Mobile Recovery bis zum zweiten frischen Mobile-Wert, sonst NO_SOURCE.
- Alarme erhalten nur validierte aktuelle LIVE-Zustände; Backfill schließt History-Lücken, löst aber keine aktuellen Alarme/Recovery-Counter aus.
- Wear UI, Tiles, Complications und Watchfaces lesen den kanonischen Resolverpfad; keine eigene Source-Umschaltung gefunden.

## Validierung

- CODE-VALIDIERT: neue Tests wurden zunächst mit den erwarteten fehlenden Symbolen/Verhalten rot und danach grün ausgeführt.
- BUILD-VALIDIERT: `test`, `assembleDebug`, Mobile-/Wear-/G7-Release-Builds, `lint`, `detekt` und `ktlintCheck` sind grün. 829 Tests: 829 bestanden, 0 fehlgeschlagen, 0 Fehler, 0 übersprungen. Das Gesamtgate endete nach 8 min 41 s mit 2.372 Gradle-Tasks (189 ausgeführt, 2.183 aktuell).
- HARDWARE-TEST OFFEN: Samsung `SM-L705F` war per WLAN-Debugging verbunden; installiert war `app.aapswear.g7watch` 0.1.13 (14). Die abschließende ADB-Abfrage zeigte kein verbundenes Gerät. Kein reales Sensor-/Reichweiten-/Restart-Szenario wurde in dieser Codeanalyse vorgetäuscht.

## Geänderte Dateien

- Produktionscode: `AndroidG7Ble.kt`, `G7CollectorService.kt`, `G7CollectorDiagnostics.kt`, `G7ExpectedWindowLedger.kt`, `G7ReadingDatabase.kt`.
- Regressionstests: `G7BlePolicyTest.kt`, `G7BackfillOrchestratorTest.kt`, `G7CollectorDiagnosticsTest.kt`, `G7ReadingDatabaseTest.kt`.
- Nachweise: dieser Analysebericht sowie Design- und Umsetzungsplan unter `docs/superpowers/`.

## Restrisiken

- Android BLE-Firmwareverhalten bei GATT 133/NO_CALLBACK und mehrstündiger Funkabwesenheit bleibt nur auf echter Watch + echtem G7 beweisbar.
- Ein vollständiger Hardwarelauf muss Installationszeitpunkt, APK-Hash, Sensor/Session und spätere Paketwechsel festhalten.
