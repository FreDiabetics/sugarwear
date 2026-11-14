# Lint und Kover, 2026-11-04

1. Bestehende 28 Mobile-/Wear-Lint-Hinweise beheben: KTX gleichwertig verwenden, leere Superaufrufe entfernen, UI-Texte als Ressourcen. Bestätigte commits bleiben synchron; apply bleibt asynchron. Relevante Tests, Build und frische Lint-Prüfung müssen erfolgreich sein und keine Hinweise melden.
2. Kover 0.9.11 in beiden Roots und allen Kotlin-Laufzeit-/Werkzeugmodulen konfigurieren, Root-Berichte zusammenführen. WFF ohne JVM-Code bleibt unverändert. Nur JVM-Test-Instrumentierung, keine APK-Instrumentierung, keine erfundene Mindestquote, keine fachlichen Klassen ausblenden.
3. HTML/XML-Berichte tatsächlich erzeugen, alle Gates ausführen, Regressionen beheben und einmal unabhängig prüfen. Protokoll/Timing/Werte, Sensor-/Gerätezustand sowie Git-HEAD/Index bleiben unangetastet. Kein Commit/PR/Push.

Offizielle Grundlage: https://kotlin.github.io/kotlinx-kover/gradle-plugin/ und https://github.com/Kotlin/kotlinx-kover/blob/main/CHANGELOG.md.
Lint-Abschluss: Mobile und Wear jeweils 'No issues found'. Vollständige Gates zunächst erfolgreich (976 Tasks), anschließend gezielte Wear-Nachprüfung erfolgreich. Keine Suppressions oder Baselines ergänzt. Kover-Aufgabenstruktur mit 16 Code-Modulen erfolgreich validiert; WFF-Module ohne JVM-Code ausgeschlossen. Berichtserzeugung und instrumentierte Tests laufen.

Sugarlicious: Kover HTML/XML erzeugt (1.357 Klassen); vollständige Gates erfolgreich (1.014 Tasks), nach Tooltip-API-Korrektur erneut erfolgreich (1.022 Tasks). Abschließende unabhängige Leseprüfung ohne relevante Defekte. SugarWear-Gates laufen separat. Runtime-Quellen-Hashvergleich: 0 Änderungen.

Abschluss: SugarWear vollständige Gates erfolgreich (438 Tasks); HTML/XML mit 924 Klassen. Mobile/Wear/SugarWear Lint jeweils 'No issues found'. Beide Roots: koverHtmlReport und koverXmlReport erfolgreich. Keine Produktionsinstrumentierung in den drei App-APKs. Abschließender Runtime-Hashvergleich: 0 Änderungen. HEAD und Index unverändert, kein Commit/PR/Push, keine Installation. Berichte jeweils build/reports/kover/html/index.html und build/reports/kover/report.xml. Ausführung im jeweiligen Projekt: .\gradlew.bat koverHtmlReport.
