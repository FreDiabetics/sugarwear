# Architektur- und Arbeitsprotokoll

Diese Regeln ergänzen bestehende Projektvorgaben. Zuverlässige CGM-Daten, bestehende Produktgrenzen und nachgewiesenes Verhalten haben Vorrang vor kosmetischen Architekturänderungen.

## Vorgehen vor jeder Architekturänderung

- Zuerst den tatsächlich ausgeführten Codepfad, Repository/Branch/HEAD, Module, Abhängigkeiten, Sprachanteile und Tests prüfen. Keine unbelegten Annahmen über Python-Laufzeitcode oder fehlendes State Management.
- Vor Implementierung einen belegten Ist-Bericht, Risiken, Zielarchitektur und einen priorisierten, schrittweisen Refactoring-Fahrplan erstellen. Vorhandene Dateien selbst lesen; nur tatsächlich fehlende Eingaben anfordern.
- Keine Komplettmigration ohne abgesicherte Zwischenschritte. UI-Migration, Datenmodell-/Persistenzmigration und BLE-/Authentifizierungsänderungen in getrennten, überprüfbaren Schritten durchführen.
- Keine Platzhalter, parallelen Ersatzarchitekturen, ausgelassenen Gates oder behaupteten Prüfungen. Beobachtbares Verhalten und Gerätebelege von reinen Build-/Testnachweisen unterscheiden.

## Sprache und Plattform

- Eigener neuer Anwendungs- und Domänencode wird in Kotlin geschrieben. Bestehenden eigenen Java-Code nach Audit schrittweise migrieren. Java nur bei belegter technischer Notwendigkeit; Ausnahmen samt Ablöseplan dokumentieren.
- Python und andere ausführbare Hilfssprachen vollständig inventarisieren: Laufzeit, Build, Asset-Erzeugung, Migration und Tests getrennt bewerten. Eigene Werkzeuge bevorzugt in Kotlin/Gradle Kotlin DSL überführen und dabei Funktion, Reproduzierbarkeit und Testabdeckung erhalten. Bis zur geprüften Ablösung keine funktionierenden Werkzeuge löschen.
- Fremdcode, Lizenzen und kryptografische Implementierungen gesondert bewerten. Authentifizierung/Protokollcode nur mit Vergleichstests, Testvektoren und belegter Kompatibilität migrieren; keine selbst erfundenen kryptografischen Ersatzverfahren.
- Plattformgebundene deklarative Dateien wie Manifest/XML, SVG, Watch Face Format und CI-Konfiguration sind keine Python-/Java-Laufzeit. Erforderliche Plattformformate und codefreie Watchface-APKs erhalten.

## Zielarchitektur und Zustände

- MVVM für interaktive App-Oberflächen: Jetpack Compose, featurebezogene ViewModels, immutable UI-State und StateFlow. UI rendert Zustand und sendet Aktionen; keine BLE-, Speicher-, Netzwerk- oder Resolverlogik in Views/Composables.
- Domäne/Use Cases, Repositories/Quellenadapter, Transport, Speicherung, Resolver, Alarme und Darstellung trennen. Bestehende kanonische Datenmodelle und Source-Resolver wiederverwenden.
- Coroutines mit strukturierter Nebenläufigkeit, definierten Dispatchern, Lifecycle-Bindung und korrekter Cancellation. Kein blockierendes IO oder runBlocking auf Produktions-UI-Pfaden.
- Grenzen für Dependency Injection vorbereiten; Hilt/Koin anhand bestehender Module und Lifecycle-Anforderungen auswählen. Interfaces dort einsetzen, wo sie echte Verantwortungs- oder Austauschgrenzen bilden.
- Widgets, Wear-OS-Tiles und WFF-Watchfaces über ihre unterstützten Plattform-APIs anbinden; gemeinsam genutzte Logik bleibt Kotlin. Diese Oberflächen nicht gegen Plattformvorgaben in eine Compose-Activity umdeuten.
- Bestehende freigegebene Darstellung erhalten: Systemskalierung, One UI auf Mobile und One UI Watch auf Wear, AOD, verschiedene runde Displaygrößen und zugängliche Bedienung.

## CGM, Hintergrundbetrieb und Alarme

- Direkte G7-Bluetooth-Kommunikation ausschließlich in SugarWear auf Wear OS. Sugarlicious Mobile bleibt Datenquellen-/Wear-Bridge. SugarWear/Vigil und Sugarlicious samt seinen Watchfaces bleiben getrennte Produkte.
- Foreground-Service-Typen, Berechtigungen, Startbeschränkungen, Benutzerzustimmung, Boot-/Prozesswiederanlauf und Ressourcenfreigabe gegen die unterstützten Android-/Wear-Versionen prüfen.
- Ein Foreground Service ist keine Garantie gegen Prozessbeendigung. Wiederanlauf, Reconnect, Backoff/Jitter, Callback-Generationen, Sensor-/Sessionwechsel, Benutzer-Pause und vollständiges Trennen müssen nachweislich korrekt sein.
- Ohne verbundenen/eingerichteten Sensor und ohne explizite Verbindungsabsicht keine endlosen BLE-Scans. Keine unnötigen WakeLocks, Dauerschleifen oder häufigen Polling-Aufrufe.
- Datenqualität, Quelle, Zeitstempel, Session, Deduplizierung, Backfill, Delta/Trend, Stale und NO_SOURCE zentral validieren. Keine erfundenen Werte und keine scheinbar frischen Altwerte.
- Alarme nur aus validierten Zuständen; bestehende zuverlässige Alarmwege nicht durch UI-Refactoring oder verschiebbare Jobs ersetzen.

## Fehlerbehandlung und Konfiguration

- Keine leeren catch-Blöcke oder still verschluckten Fehler. Erwartbare Fehler explizit und typsicher modellieren, Recovery und Logging bewusst festlegen.
- Coroutine-Cancellation erhalten; keine pauschalen Throwable-Fänge, die Cancellation oder Programmfehler verbergen.
- Keine Zugangsdaten, Pairing-Codes, Schlüssel oder unnötigen sensiblen Gesundheitsdaten in Logs.
- Keine unbenannten Magic Numbers oder versteckten Verhaltensschalter. Fachliche Grenzwerte, Protokollkonstanten, Defaults und Designwerte zentral, benannt, typisiert und dokumentiert halten. Keine unnötigen Interfaces oder Settings für reine Implementierungsdetails.

## Verifikation und Auslieferung

- Bestehende Gates zunächst als Baseline ausführen; für neue Fehler und Verhalten sinnvolle Regressionstests ergänzen. Keine Tests löschen/abschalten, um eine Migration grün zu bekommen.
- Insbesondere Reconnect, Neustart, Pause/Fortsetzen, Sensorwechsel, Stale, Out-of-order, Duplikate, Backfill, Alarmzustände und Persistenzmigration absichern.
- Betroffene Builds, vollständige relevante Tests, Lint, statische Analyse, Formatierung und CI müssen grün sein. Plattform-/Geräteverhalten zusätzlich auf unterstützten Samsung- und Pixel-Geräten prüfen, soweit betroffen.
- Keine Zusage absoluter Fehlerfreiheit oder unbegrenzter Gerätestabilität ohne entsprechende Nachweise. Unterstützte Versionen/Geräte, gemessene Ergebnisse und verbleibende Risiken präzise benennen.
- Vor Git-Änderungen Status, Branches, Remotes und Worktrees prüfen. Fremde Änderungen erhalten; getrennte PRs mit überprüfbarem Umfang und nachvollziehbarer Validierung verwenden.
