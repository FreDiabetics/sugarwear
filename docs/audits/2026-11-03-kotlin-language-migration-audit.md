# Ist-Bericht: Sprachanteile und sichere Kotlin-Migration

Stand: 2026-11-03. Read-only-Quellcodeaudit; keine Änderung am Collector, Sensor, Protokoll oder an installierten Apps. Die ergänzenden lokalen AGENTS.md-Dateien dokumentieren den neuen Arbeitsmodus. Dieser Bericht ist keine Freigabe einer bereits durchgeführten Migration.

## Prüfbasis und Grenzen

- Sugarlicious: `2197aa52b5c2a3a7170fa7bfad5b051b27d6bf53`, main.
- SugarWear: `8fcb6c2853608a38f6424e4a570112c409f7b1cc`, main.
- SugarWear verwendet Sugarlicious als gepinntes Submodul unter `shared/sugarlicious`, Stand `a977f3280cda3425fa295524780a3827ae495c13`. Die hier relevanten G7-Java-Dateien unterscheiden sich nicht zwischen diesen beiden Shared-Ständen.
- Inventar aus versionierten Dateien, nicht aus generierten Build-Verzeichnissen. Dateizahlen sind keine Codezeilen-Prozentanteile und enthalten Tests.
- Untersucht wurden Modulkonfiguration, Sprachintegration, Authentifizierungspfad, Entwicklungswerkzeuge und zugehörige Prüfungen. Ein vollständiger UI-/MVVM-, Energie- oder Laufzeitaudit aller Apps ist damit nicht abgeschlossen.

## Projektaufbau und Sprachanteile

| Bestandteil | Kotlin | Java | Python |
|---|---:|---:|---:|
| Sugarlicious, ohne fremde Build-Ausgaben | 477 | 24 | 25 |
| SugarWear, ohne Shared-Submodul | 134 | 0 eigene | 8 |

SugarWear benutzt trotzdem die 24 Java-Dateien des gemeinsamen G7-Moduls. Es ist daher nicht Java-frei.

Sugarlicious-Kotlin: 266 Main-, 210 Test-/AndroidTest- und eine Debug-Datei. SugarWear-Kotlin: 72 Main-, 60 Test- und zwei Debug-Dateien.

`settings.gradle.kts:16–35` bindet in Sugarlicious 19 Module ein:

- Gemeinsame Bibliotheken: core-model, dexcom-g7, data-source-api, data-source-aaps, data-source-dexcom, data-source-external, wear-protocol, wear-storage, ui-shared, complications.
- Apps: app-mobile und app-wear.
- Vier Watchface-Module: test-wff, sugarlicious-digital, sugarlicious-analog, sugarlicious-orbit.
- Bereits vorhandene Kotlin/JVM-Werkzeuge: aaps-cwf-parser, wff-generator, screenshot-comparator.

SugarWear bindet acht Module ein: sechs gemeinsame Bibliotheken sowie g7watch und Vigil (`settings.gradle.kts:15–20`). Gemeinsamen Laufzeitcode weiter über diese Abhängigkeit nutzen, nicht kopieren.

Alle 24 Java-Dateien liegen unter `dexcom-g7/src/main/java/`. Sämtliche 33 Python-Dateien beider Produkte liegen unter tools. In den untersuchten Anwendungsquellen und Build-Konfigurationen wurde keine Python-/Chaquopy-/Jython-Laufzeitintegration gefunden. Die beobachtete Python-Nutzung betrifft Asset-Konvertierung, Konfiguration, Prüfungen und Produktmigration. Das ist ein Quellen-/Buildbefund, kein vollständiger APK-Forensiknachweis.

Zusätzlich bestehen zwei angewendete Groovy-Gradle-Skripte in Sugarlicious und 17 PowerShell-Dateien. XML, SVG, WFF und andere deklarative Plattform-/Assetformate bleiben erforderlich; sie sind kein zu ersetzender Python-Laufzeitcode.

## Vorhandene Architektur, die erhalten werden sollte

Der direkte BLE-Transport ist bereits Kotlin: SugarWear `g7watch/src/main/kotlin/app/aapswear/g7watch/AndroidG7Ble.kt`; der Collector liegt in G7CollectorService.kt. Die Kotlin-Fassade G7AuthenticationSession kapselt die Java-Authentifizierung bereits. Coroutines, Flows, zentrale Modelle und Speicherkomponenten existieren. Es wäre falsch, ein vollständig fehlendes State Management zu behaupten oder eine parallele Ersatzarchitektur aufzubauen.

Die Architekturprüfung verhindert einen direkten Mobile-G7-Collector. Mobile Compose und imperative Wear-Views bestehen nebeneinander. Eine spätere MVVM-/Compose-Umstellung benötigt eine eigene Bestandsaufnahme; Tiles, Widgets und WFF behalten ihre vorgeschriebenen Plattform-Schnittstellen.

Kotlin/Java-Interoperabilität ist unterstützt; Java allein ist kein Nachweis für Instabilität. Maßgeblich sind Verträge, Nebenläufigkeit und Fehlerbehandlung: https://kotlinlang.org/docs/java-interop.html

## G7: tatsächlich ausgeführter Pfad

1. AndroidG7Ble.kt:535 erstellt G7AuthenticationSession mit Kopplungscode und Schlüsselmaterial.
2. `dexcom-g7/src/main/kotlin/app/aapswear/g7/G7Setup.kt:51–81` kapselt einen neuen Plugin-Aufruf pro Sitzung. Der vorhandene Java-Singleton ist nicht der hier verwendete Einstieg.
3. Persistenzkanäle 8/9/10 transportieren Zertifikatsmaterial, Kanal 2 den gespeicherten Schlüssel, Kanal 6 den Receiver-Slot. WATCH_ALTERNATE ist 0x02, PRIMARY 0x00.
4. Plugin.aNext liefert null beziehungsweise Arrays unterschiedlicher Länge. AndroidG7Ble.kt:981–1008 interpretiert diese als Fehler, Abschluss, Bond-Anforderung oder Sendepakete.
5. Bei zwei Paketen werden Zusatzdaten vor dem Authentifizierungsbefehl gesendet. Aktuell gelten 20-Byte-Blöcke, 40 ms Blockabstand und 500 ms Beruhigungszeit (AndroidG7Ble.kt:1184–1186).

Dieser Ablauf ist die Migrationsgrenze. Sprache wechseln, aber nicht gleichzeitig Kanalnummern, Receiver-Slot, Paketfolge, GATT-Verhalten, Zeitparameter oder gespeicherte Schlüssel ändern.

## Belegte Schwachstellen und konkrete Maßnahmen

### 1. Fehlende vollständige Authentifizierungs-Vergleichsvektoren — höchste Migrationspriorität

Die vorhandenen G7-Tests prüfen unter anderem Parser, Alarmzustände, Wiederverbindung und Receiver-Slots. Vollständige KEKS-Transkripte als Java/Kotlin-Vergleichsbasis wurden nicht gefunden. SugarWear hat zusätzlich Fehler-/GATT-Pfadtests; das ersetzt keinen kryptografischen Äquivalenznachweis.

Vor jedem Austausch synthetische Referenzvektoren für Erstauthentifizierung, gespeicherten Schlüssel, Bond-Anforderung, fragmentierte/ungültige Pakete und Abbruch erstellen. Produktionsdaten, Kopplungscodes und Schlüssel nicht in Fixtures oder Logs übernehmen. Ein alter Referenzadapter darf vorübergehend im Testcode bleiben, nicht als zweite dauerhafte Produktionsimplementierung.

### 2. Fehlerursachen verschwinden hinter null

AESwrapper.java:24–25, DSAChallenger.java:35–38, KeyPair.java und Util.java:51–62 fangen Fehler ab und liefern teilweise null. Außen existieren bereits G7BleException und bereinigte Diagnosecodes, aber die innere Ursache geht verloren.

Zuerst die bestehende Kotlin-Fassade typisieren: klar getrennte Ergebnisse für Sendebefehl, Zusatzdaten, Abschluss, Bond-Anforderung und Authentifizierungsfehler. Fehler enthalten Phase, stabilen Code und Wiederholbarkeit. Coroutine-Abbruch muss erhalten bleiben. Keine Rohpakete oder Geheimnisse protokollieren.

### 3. Kryptografie ist an Provider-Interna gekoppelt

DSAChallenger.java:10/24 erweitert eine BouncyCastle-interne SignatureSpi-Klasse. KeyPair.java verwendet interne Provider-Klassen. dexcom-g7/build.gradle.kts:10–12 enthält BouncyCastle 1.81 und Lombok.

Kotlin-Adapter auf unterstützte JCA-/öffentliche Provider-APIs umstellen, ohne eigene Algorithmen zu erfinden. Providerwahl ausdrücklich prüfen, damit Samsung und Pixel nicht unterschiedliche Implementierungen auswählen. DER-Signaturen weiterhin korrekt in unsigned 32-Byte-r plus 32-Byte-s überführen. Zufällige ECDSA-Signaturen müssen bei unabhängigen Signiervorgängen nicht bytegleich sein: Verifikation und Format prüfen; deterministische Zufallsquellen ausschließlich in synthetischen Tests verwenden. Produktionscode behält SecureRandom.

### 4. Binärformate enthalten gefährliche Portierungsfallen

- Util.java:58 vertauscht die Nibbles: Text `12` wird zu Byte 0x21. Ein Standard-Hexparser wäre keine verhaltensgleiche Übersetzung. Zunächst als ausdrücklich benannter Legacy-Codec erhalten und mit allen betroffenen Konstanten testen.
- Util.java:66–67 verwendet standardmäßig Big Endian; BaseMessage.java verwendet Little Endian. Nicht vereinheitlichen.
- Vorhandenes AES/ECB/NoPadding gehört zum Challenge-Protokoll. Bei einer Sprachmigration nicht durch einen anderen Modus ersetzen.
- BigInteger-Vorzeichen, führende Nullen, feste 32-Byte-Längen und EC-Punktformate exakt erhalten.

Diese Punkte sind Kompatibilitätsanforderungen, kein Beleg, dass die bestehende Verbindung deshalb aktuell fehlschlägt.

### 5. Zustände und Eingabeverträge sind zu implizit

Plugin.java:228 erweitert einen Akkumulator per Arraykopie. Calc.java:173 kopiert 16 Bytes ab Offset 2; die vorgelagerte Prüfung in Plugin.java:313 validiert nicht unmittelbar diese vollständige Mindestlänge. Weitere Pfade verlassen sich auf vorgelagerte Zustandsannahmen.

Längen, Opcode, Phase und maximale Puffergröße vor Zugriff explizit prüfen. Grenzwerte erst aus gültigen Protokolltranskripten ableiten; keine willkürlichen Caps einführen. Ungültige Pakete liefern einen typisierten Formatfehler statt zufälliger Array-Ausnahmen. ByteArrays defensiv kopieren; Kotlin-data-class allein macht sie nicht unveränderlich.

Plugin.java enthält außerdem statisch veränderliche Zustände. Der aktuelle Einstieg erstellt zwar eigene Instanzen, gemeinsame statische Sammlungen bleiben aber eine Kopplung zwischen Sitzungen. Später sitzungsgebundene Zustände mit genau einem Besitzer und expliziter Zustandsmaschine einführen. Das ist ein Code-Risiko, kein bereits nachgewiesener Race-Condition-Gerätefehler.

### 6. Vermeidbare Kopien und eigene Hashimplementierung

Digest.java akkumuliert Daten durch erneutes Kopieren; SHA256.java benutzt synchronisierte globale Arbeitsfelder. Schrittweise durch inkrementelles JCA MessageDigest ersetzen, abgesichert durch Standard- und Legacy-Vektoren. Ohne Profiling lässt sich daraus kein konkreter Akkuprozentgewinn ableiten.

### 7. Diagnose darf keine Geheimnisse freilegen

jamorham/keks/util/Log.java ist absichtlich deaktiviert; LICENSES/xDrip-libkeks.md erläutert, dass die ursprünglichen Meldungen Authentifizierungsdaten enthalten. Nicht pauschal aktivieren. Bereinigte strukturierte Diagnose am Kotlin-Adapter ergänzen. Herkunft und Lizenzhinweise beim Port erhalten.

### 8. Möglicherweise unnötige G7-Abhängigkeit im Companion

app-wear/build.gradle.kts:223 bindet dexcom-g7 ein. Im untersuchten Main-Quellcode wurden keine direkten G7-/jamorham-Imports gefunden. Die frisch aufgelöste debugRuntimeClasspath enthält das Modul und BouncyCastle.

Das ist ein Entfernungskandidat, noch kein Nachweis unbenutzten Release-DEX-Codes. Erst vollständige Referenzen einschließlich Reflection/Manifest prüfen, anschließend Abhängigkeit separat entfernen und Build, Tests sowie APK-Größe vergleichen. Das könnte sinnvoller sein als unnötige Kryptografie überhaupt in diesen Verbraucher zu übernehmen.

## Python- und Build-Werkzeuge

Sugarlicious: 14 Asset-/Konfigurationshelfer, zehn Testdateien und ein Produktmigrationsprogramm. SugarWear: vier Assethelfer, drei Testdateien und ein APK-Verifizierer.

Konkrete Befunde:

- `.github/workflows/build.yml:106–110` führt nur einen Teil der Sugarlicious-Python-Tests aus. Nicht eingebunden sind sechs Module: apex_progress, curated_palettes, native_slot_capture, native_watchface_rendering, saved_watchface_defaults und watchface_presets. Lokal wurden sie jetzt erfolgreich mitgeprüft. Zuerst CI-Abdeckung schließen, bevor Tests umgeschrieben werden.
- `tools/watchface-assets/import_orbit_weather.py:24–64` schreibt Ausgaben schrittweise. Vorabprüfung, Staging und atomare Veröffentlichung verhindern unvollständige Asset-Sätze bei Fehlern.
- `configure_trend_icon_geometry.py:17–29` bearbeitet XML mit Regex und ungesichertem Matchzugriff. Kotlin-XML-Verarbeitung mit erwarteter Trefferzahl, semantischem Vergleich und Idempotenztest einsetzen.
- PowerShell-Renderer verwenden System.Drawing/Arial beziehungsweise WPF. Eine Kotlin-Neufassung kann andere Pixel liefern. Schriftdateien und Renderer reproduzierbar festlegen; Canvas, Transparenzgrenzen, Geometrie und Pixelvergleich prüfen. Goldens nicht still neu erzeugen.
- Importer enthalten persönliche absolute Standardpfade. Eingaben ausdrücklich über Produktkonfiguration oder gültige relative Pfade bereitstellen.
- Drei ähnlich benannte Brand-/Testdateien beider Produkte unterscheiden sich bereits. Gemeinsamen Algorithmus mit produktbezogener Konfiguration extrahieren; keine blinde Dateisynchronisation.
- `gradle/shared-conventions.gradle` dokumentiert absichtliche Classloader-Trennung. Groovy nicht einfach in .gradle.kts umbenennen. Kotlin-Konventionsplugins in einem included build mit ausdrücklich deklarierten Plugin-Abhängigkeiten und Gradle-TestKit prüfen. Siehe https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html
- Produktinstallation/-migration zuletzt portieren: Gerätezuordnung, Seriennummern, Quoting, Timeouts, Berechtigungs-Allowlist, Vorabprüfung aller APKs und Rollback sind Sicherheitsverträge. Fake-ADB-Fehlerprüfungen erhalten; keine echten Geräte in Unit-Tests verwenden.

Bestehende Kotlin-Werkzeuge erweitern. Nicht wegen der neuen Sprachregel eine zweite Asset-/Build-Infrastruktur erfinden. Python erst löschen, wenn ein gleichwertiger, integrierter Ersatz alle bestehenden Aufrufer und Prüfungen übernimmt.

## Priorisierter Migrationsfahrplan

1. Baseline und Referenz sichern: Commits, Abhängigkeiten, Testabdeckung, synthetische Protokollvektoren, Asset-Goldens. Bisher ausgelassene Prüfungen in CI aufnehmen.
2. Typisierte Kotlin-Grenze um die unveränderte Java-Engine legen. Authentifizierungsergebnisse, Material, Receiver-Slot und Fehler entkoppeln; Engine-/Zufallsquellen injizierbar machen.
3. Risikoarme Validatoren und reine Java-Codecs einzeln nach Kotlin überführen. Jede Änderung gegen Legacy-Verhalten vergleichen; zuerst keine Persistenz- oder BLE-Änderung.
4. Kryptografische Adapter, anschließend die Authentifizierungs-Zustandsmaschine migrieren. JCA-/Provider-Verträge, Pakettranskripte, Abbruch und Wiederverbindung separat absichern. Lombok erst entfernen, wenn kein betroffener Java-Code mehr benötigt wird.
5. Mutierende Assetwerkzeuge, Renderer, Gradle-Konventionen und Gerätemigration ersetzen. Alte Werkzeuge erst nach Ausgabeparität, Idempotenz und Fehler-/Rollbacktests entfernen.
6. Gerätefreigabe auf Samsung und Pixel: frische Verbindung, gespeicherte Verbindung, Neustart, Reichweitenverlust, Pause/Fortsetzen, ungültige Pakete und Parallelbetrieb. Separate UI-/MVVM-Migration folgt danach; nicht mit dem Protokollport vermischen.

Abnahmekriterien: identische deterministische Protokollbytes, gleichwertige Signaturverifikation, unveränderte Persistenz/Slots/GATT-Folge, explizite Fehler, kein Secret-Logging, grüne bestehende und neue Tests. Rückfall erfolgt über den letzten freigegebenen App-Stand, nicht über einen dauerhaften zweiten Produktions-Collector.

## Frisch ausgeführte Prüfungen

- `gradlew.bat verifyArchitecture :dexcom-g7:test`: erfolgreich; Testaufgabe zunächst aus Cache.
- Anschließend `gradlew.bat :dexcom-g7:test --rerun-tasks`: erfolgreich, sieben Aufgaben tatsächlich ausgeführt, 38 Tests, null Fehler/Fehlschläge.
- Sugarlicious Python-Testdiscovery: 74 erfolgreich.
- Sugarlicious Produktmigrationstests: zehn erfolgreich.
- SugarWear Python-Testdiscovery: 22 erfolgreich.
- app-wear debugRuntimeClasspath frisch aufgelöst.

Damit sind 38 G7-Modultests und 106 Python-Tests erfolgreich. Nicht ausgeführt: vollständiger Produkt-/Lint-/Release-Gate-Lauf, neue kryptografische Vergleichsvektoren oder Hardware-/Akkuvermessung. Dieser Bericht behauptet weder vollständige Produktionsreife noch eine bereits verbesserte Sensorverbindung.

Die neuen Anforderungen werden von den heutigen Qualitätsregeln noch nicht vollständig erzwungen: beispielsweise ist TooGenericExceptionCaught in config/detekt/detekt.yml:30–32 deaktiviert. Regeln modulweise verschärfen und tatsächliche Befunde beheben; keine globale Unterdrückung und keine unvermittelte Gesamtreform mit tausenden fachfremden Änderungen.

## Entscheidungsempfehlung

Kotlin als Zielsprache ist sinnvoll. Den größten Sicherheitsgewinn liefert jedoch zuerst der nachweisbare Vertrag um die bestehende Authentifizierung, anschließend die überprüfte Migration. Keine Neuentwicklung des Sensorprotokolls und kein gleichzeitiger Wechsel von Sprache, Kryptografie, Persistenz und BLE-Lifecycle.
