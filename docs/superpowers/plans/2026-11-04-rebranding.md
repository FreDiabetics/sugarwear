# Rebranding, 2026-11-04

## Grenzen

Keine Commits, PRs, Pushes oder Installation. Bestehende lokale Änderungen bleiben erhalten. Sicherung: externer Snapshot im temporären Benutzerverzeichnis. Beide Ausgangs-Gates (Tests, Build, Lint, Detekt, ktlint; zusätzlich Architektur in Sugarlicious) waren erfolgreich.

## Reihenfolge

1. Module umbenennen und beide vollständigen Gates ausführen.
2. Eigene Packages, Typen und Android-Komponenten anhand des freigegebenen Mappings umbenennen. Alte Komponenten entfallen; Slot-Neuzuordnung ist akzeptiert.
3. Ressourcen und interne Werkzeuge nachziehen; technische Kompatibilitätskonstanten und Lizenz-/Herkunftsnachweise ausdrücklich erhalten.
4. Beide vollständigen Gates und Referenztests erneut ausführen.

## Unveränderliche Verträge

Application IDs, Signatur, Datenbank-/Preference-Dateinamen und Schlüssel, Keystore-Aliasse, serialisierte Werte, externe Broadcast-Actions und Paketnamen, BLE-Nachrichten und Timing bleiben identisch. Keine Sensortrennung, Datenlöschung oder Geräteänderung.

Gemeinsamer Code wird ausschließlich im kanonischen Sugarlicious-Checkout bearbeitet. SugarWear verwendet während der uncommitteten Migration den bereits vorhandenen sharedSourceRoot-Override. Sein Submodul wird nicht kopiert oder ohne Commit neu gepinnt.

Kover, Hilt und neue CI-Konfiguration gehören zu nachfolgenden Architektur-Schritten, nicht zu dieser Umbenennung.
## Zulässige historische Zeichenketten

- Persistenzverträge: Datenbankdateien, Preference-Dateien/-Schlüssel, Keystore-Aliasse und serialisierte Herkunftskennungen behalten ihre bisherigen Bytes.
- Externe Integrationen: Broadcast-Actions, Hersteller-Paketnamen, SDK-/System-Metadaten und Datenquellenbezeichnungen bleiben korrekt und nachvollziehbar.
- Rechtliche Herkunft: Copyright-, Lizenz- und Fremdcode-Hinweise werden nicht anonymisiert.
- Vergleichsfixtures: Historische, festgeschriebene Referenzdateien bleiben als Prüfbasis unverändert. Sie sind keine ausgelieferten Alt-Komponenten.

## Prüfprotokoll

- Ausgangsstand beider Repositories: vollständige Gates erfolgreich.
- Modulschritt Sugarlicious: vollständige Gates erfolgreich (870 Tasks).
- Modulschritt SugarWear: vollständige Gates erfolgreich (360 Tasks), nach Korrektur veralteter Asset-Testpfade.
- Gleichzeitige Builds auf gemeinsamen Ausgabeordnern sind unzulässig: unter Windows entstand eine reproduzierbare Dateisperre. Prüfungen werden sequenziell ausgeführt.

## Präzisierung des Mappings

G7Reading wird SensorProtocolReading, nicht CgmReading. CgmReading existiert bereits als kanonisches normalisiertes Modell im selben Package. Die erste Compilerprüfung hat diese Kollision aufgedeckt; die neun betroffenen Quellen wurden aus der externen Ausgangssicherung mit dem korrigierten Mapping erneut übertragen. Felder und Verarbeitung wurden nicht geändert.

## Stand der Identitätsmigration

- Package-/Klassen-Schritt Sugarlicious: vollständige Gates erfolgreich (870 Tasks, 2m23s).
- Package-/Klassen-Schritt SugarWear: vollständige Gates erfolgreich (412 Tasks einschließlich Formatierung, 7m18s).
- Neue RebrandingWireCompatibilityTest-Tests vor der Feldumbenennung: erfolgreich.
- Dieselben Tests nach der Feldumbenennung mit SerialName sowie vollständige cgm-protocol-Referenztests: erfolgreich (16s).
- Weitere interne Bezeichner und 30 Ressourcenkennungen sind umbenannt. CgmCgm-Redundanz wurde zu Cgm bereinigt.
- Ruling: Das Kotlin-Feld heißt therapyBridgeDisplaySemantics, der gespeicherte JSON-Schlüssel bleibt per SerialName aapsDisplaySemantics. Damit bleiben bisherige Nachrichten und Archive kompatibel; ohne Alias wären diese Metadaten nicht mehr lesbar.
- Ruling: SugarWear liest den vorhandenen sharedSourceRoot-Override auch aus der ignorierten lokalen local.properties. Sein Build leitet den gemeinsamen Root vom tatsächlich eingebundenen core-model-Projekt ab. Damit ist kein unautorisierter Submodul-Commit und keine Codekopie nötig. Der Submodul-Zeiger muss beim später ausdrücklich angeforderten Git-Abschluss aktualisiert werden.
- Ruling: Ein nativer SQLite-Test erreichte auf Windows durch den längeren Package-Namen exakt 260 Pfadzeichen. Nur sein Testname wurde verkürzt, keine seiner Prüfungen. Der Test ist danach erfolgreich.
- Ruling: Historische DOM-Geometrie-Hashes bleiben unverändert. Beide Watchface-Eingaben liegen als feste Fixtures vor; nur der autorisierte Komponenten-Namespace wird für diesen historischen Vergleich zurücknormalisiert. Aktuelle Manifest-/Provider-Tests prüfen weiterhin die neuen Komponenten.
- Aktuelle vollständige Endprüfungen: %TEMP%/rebrand-main-final.log und anschließend %TEMP%/rebrand-wear-final.log (sequenziell, ohne CLI-Override für SugarWear).
- Keine Commits, PRs, Pushes, Submodul-Änderungen oder Geräteinstallationen. Beide HEADs unverändert.

## Abschlussnachweis

- Sugarlicious: `gradlew.bat ktlintFormat verifyArchitecture detekt ktlintCheck test assembleDebug lint` erfolgreich, 976 Tasks, 7m38s.
- SugarWear: `gradlew.bat detekt ktlintCheck test assembleDebug lint` erfolgreich, 360 Tasks, 3m17s. Ohne CLI-Quellenoverride; der lokale relative Quellenpfad funktioniert.
- Berichtete Source-/Compilerwarnungen wurden beseitigt: unnötiger Unit-Ausdruck, redundante Float-Konvertierung und übersetzbarer Retry-Text als Android-Ressource. Der lokale Override verwendet einen relativen Pfad ohne Windows-Property-Escape-Fehler.
- APK-Prüfung: Mobile 16, Sugarlicious Wear 15 und SugarWear 15 DEX-Dateien. Keine Klassendeskriptoren unter app/aapswear, jamorham/keks, jamorham/libkeks oder com/eveningoutpost/dexdrip/plugin.
- Paket-/Versionsprüfung: Mobile/Wear app.sugarlicious und SugarWear app.sugarwear, jeweils unverändert 0.7.1 (71). Neue Launcherklassen gehören den eigenen Namespaces.
- Elf Zeichenketten des verschlüsselten Sensorzugangsspeichers (Datei/Schlüssel/Alias/Transformation) nach neutraler Konstantenumbenennung unverändert. Collector-Datenbankname und Schemaversion unverändert.
- Der unabhängige Review bestätigte unveränderte binäre Bild-/Font-/Video-Assets sowie Manifest-Authorities und Berechtigungswerte. Keine Änderungen an Kryptografie-Algorithmen gefunden.

### Review-Fixes

- P1: Die historische SugarWear-App-ID im Import ist wieder app.aapswear.g7watch. ProductMigrationCompatibilityTest prüft echtes Provider-OnCreate ohne alten installierten Provider und erhaltene Einstellungen: beobachtet rot (MigrationFailure), danach grün (bereit). Der aktuelle Prozess wird nicht mehr als seine eigene Importquelle adressiert.
- P2: CollectorCycleTiming.scanNamedCgmResults verwendet SerialName("scanNamedG7Results"). Historische JSON-Fixture behält den Zähler bei Lesen und Schreiben: RebrandingDiagnosticCompatibilityTest beobachtet rot, danach grün.
- Ruling: Der als P3 gemeldete UI-Konstantenrest ist wegen der ausdrücklich verlangten neutralen Eigennamen Bestandteil der Anforderung, kein aufgeschobenes Polishing. Alle 55 betroffenen eigenen Konstantenbezeichner sind neutral; Werte und Übertragungs-/Speicherkennungen bleiben identisch. Kein aufgeschobener Review-Befund.
- Historische JSON-Eigenschaften wurden vor/nach der Umbenennung getestet. Der vollständige Serializable-Eigenschaftsvergleich des Ausgangsstands identifizierte nur die beiden genannten umbenannten Speicherfelder.

### Bewusst unverändert

Historische Speicher-/Transport-/Migrationskennungen, tatsächliche Herkunfts- und Datenquellenidentitäten, externe System-/SDK-Bezeichnungen und Lizenz-/Copyrightnachweise bleiben erhalten. Sie sind keine alten ausgelieferten Android-Klassen oder Kompatibilitäts-Aliasse.

Keine Installation, Sensoraktion, Collector-Analyse, Commits, PRs, Pushes oder Submodul-Pointeränderung. HEAD Sugarlicious 37eee630b551ddd3a879ef5cd5c0b14785daad54; HEAD SugarWear 8ff8dc42bdaaa5053b44766f1c499d9225116581. Kein gestagter Inhalt.

Geräteverhalten nach einer zukünftigen Installation ist hier nicht als geprüft ausgewiesen. Alte Slot-Zuweisungen dürfen gemäß Freigabe neu gewählt werden. Für einen später autorisierten Git-Abschluss muss SugarWear auf den dann vorhandenen gemeinsamen Commit gepinnt werden; bis dahin wird der gemeinsame Code lokal direkt eingebunden und nicht kopiert.

Vollständige aktuelle Prüflogs und die Rot/Grün-Nachweise liegen lokal unter build/reports/rebranding/ (ignorierte Build-Artefakte).

### Abgegrenzte Bestands-Hinweise

Die vollständigen Gates scheitern nicht an vorhandenen Stilhinweisen: Mobile meldet 2 EmptySuperCall-Hinweise; Sugarlicious Wear meldet 26 vorhandene Hinweise (überwiegend KTX-Empfehlungen, außerdem EmptySuperCall und UI-Textressourcen). Die betroffenen Aufrufe waren bereits im Ausgangssnapshot vorhanden. Sie wurden in dieser verhaltenswahrenden Namensmigration weder pauschal unterdrückt noch durch eine neue Lint-Baseline versteckt. SugarWear meldet im abschließenden Lint-Bericht keine Probleme. Diese Bestands-Hinweise sind kein als behoben ausgegebener Teil dieses Rebrandings.
