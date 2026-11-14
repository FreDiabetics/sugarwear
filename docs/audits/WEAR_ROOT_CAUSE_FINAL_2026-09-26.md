# Wear Root-Cause Final — 2026-09-26

## Analysezeitraum und Baseline

- Analyse und Umsetzung: 2026-09-20 bis 2026-09-26.
- Basis: `origin/main` `1919ace0`; Abschluss: `b696dcfe` auf `codex/wear-root-cause-final`, 40 Commits voraus, keine Divergenz und sauberer Working Tree.
- Produktiver Pfad: AAPS-Payload → kanonischer Mobile-State → persistierter Wear-State/Resolver → App, Tile, Complications und Watchfaces; G7-BLE → persistente Historie/Gap-Ledger → Resolver → dieselben Oberflächen.

## Architektur

- Eine kanonische Daten-, Freshness- und Resolver-Schicht bleibt die Quelle aller Darstellungen.
- App- und Graph-Tile verwenden denselben `SharedWearCgmGraphRenderer`; das Tile besitzt davon unabhängige persistente Darstellungswerte.
- LIVE/BACKFILL, Sensor/Session, Mess- und Empfangszeit bleiben getrennt. Reconnect, Neustart und Backfill verwenden das persistente erwartete Zeitfenster und deterministische Deduplizierung.

## Root Causes und historische Probleme

- **Tile-Schrift — vorher nicht behoben, jetzt CODE-VALIDIERT:** App und ProtoLayout lösten trotz gleicher Bezeichnung unterschiedliche Standardschriften auf. Beide Pfade verwenden nun explizit Roboto und gemeinsame semantische Gewichte; echte Glukose-, Trend-, Meta- und Statustexte sind abgedeckt.
- **Insulinaktivität — vorher nicht behoben, jetzt CODE-VALIDIERT:** Produktionsbroadcasts enthalten üblicherweise kein Feld `insulinActivity`; Tests hatten es künstlich geliefert. Die Aktivität wird nun aus den von AAPS berechneten `BGI`- und `ISF`-Werten gemäß `activity = -BGI / (ISF * 5)` übernommen. Unvollständige/unphysikalische Daten scheitern geschlossen; es gibt keine IOB-Schätzung.
- **Graph-Tile — vorher nicht behoben, jetzt CODE-VALIDIERT:** ProtoLayout-Card, Padding und Renderer-Kontur bildeten doppelte Hintergründe. Die äußere Card wurde entfernt; der Graph wird exakt in der endgültigen Bitmapgröße erzeugt und füllt die bestehende Tile-Kontur ohne Skalierung oder Verzerrung. Nur Pixel außerhalb der Rundung tragen die umgebende Tile-Farbe.
- **Fehlende Tile-Einstellungen — vorher offen, jetzt CODE-VALIDIERT:** `Farben & Darstellung` enthält `WEAROS-TILE GRAPH` mit Zeitraum, Punktgröße, beiden Konturen, Konturstärke, Zeitachse, Rundung, Skalenfeld-Deckkraft und allen Graphfarben; Änderungen invalidieren das Tile.
- **App vor dem Update nicht startbar — durch den Versions-/Schema-Fix behoben:** monotone Versionen und Downgrade-Schutz verhindern die frühere inkompatible Datenbankschema-Installation.
- Collector-, Gap-, Restart-, Background-, Sensorwechsel-, Freshness-, Alarm-, Resolver-, Complication- und Ressourcenpfade wurden in den Master-Phasen geprüft; die Änderungen erhalten diese Grenzen und führen kein Polling, keine Restart-Schleife und keine zweite Datenarchitektur ein.

## Tests und Quality Gate

- Aktuell betroffene Module: **535 Tests**, 535 bestanden, 0 Fehler, 0 übersprungen (`ui-shared` 6, `data-source-aaps` 25, `app-mobile` 269, `g7watch` 235).
- Vorheriger vollständiger Branchlauf: **853 Tests**, 853 bestanden.
- Erfolgreich: `assembleDebug`, G7/Mobile `assembleRelease`, G7/Mobile `lint`, `detekt`, `ktlintCheck`, fokussierte Render-/Persistenz-/Payload-Tests und `git diff --check`.
- Ein paralleler JBR-JIT-Lauf und ein paralleler Lint-Lauf scheiterten an JVM-Codecache/VM-Fehlern. Beide Gates wurden ohne deaktivierte Regeln isoliert erfolgreich wiederholt.

## Hardwarevalidierung

- **HARDWARE-TEST OFFEN.** ADB erkennt aktuell ausschließlich `SM-S948B`; die Galaxy Watch `SM-L705F` erscheint weder unter `adb devices` noch per mDNS.
- Der sichere Kombi-Installer baute alle APKs, stoppte aber mit `Genau eine Wear-OS-Watch wird erwartet, gefunden: 0`. Daher wurde nichts auf das falsche Gerät installiert.
- Offen bleiben Sichtprüfung von Schrift/Kontur, Live-CGM/Insulinaktivität, Kaltstart, Process-Kill, Reboot, längerer Offline-Zeitraum und Akku-/Speicherbeobachtung auf echter Hardware.

## Restrisiken

- Das AAPS-`reason`-Format ist extern und textuell; die Auswertung liefert bei Formatänderung keine Aktivitätskurve statt erfundener Werte.
- RGB565 unterstützt keine Transparenz; außerhalb der gerundeten Graphkontur wird deshalb bewusst die umgebende Tile-Hintergrundfarbe gemattet.

## Geänderte Dateien dieser sichtbaren Korrektur

- `core-model/.../WearGlucoseCardPresentation.kt`
- `data-source-aaps/.../AapsPayloadAdapter.kt` und Tests
- `app-mobile/.../DashboardChartsTest.kt`
- `g7watch/.../G7AppearanceActivity.kt`, `G7AppearanceStore.kt`, `G7CollectorGraphView.kt`, `G7GraphPresentation.kt`, `G7GraphTileService.kt`, beide Tile-Services und zugehörige Tests
- `ui-shared/.../SharedWearCgmGraphRenderer.kt`
- Root-/G7-Buildversionen und diese Dokumentation
