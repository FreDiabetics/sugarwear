# Test Report

## Project-wide behavior-preserving refactoring, 2026-10-06

- Finaler Gesamtgate: `verifyArchitecture test assembleDebug assembleRelease lint detekt ktlintCheck --no-daemon --no-configuration-cache --max-workers=4` erfolgreich in 5 min 41 s; 1.151 Gradle-Tasks.
- 158 JUnit-XML-Suites mit 990 Tests: 0 Fehler, 0 Fehlschläge, 0 übersprungen.
- Alle vier aktiven/Test-WFF-Dokumente bestanden den offiziellen Validator als WFF v1; alle vier Release-APKs sind codefrei und enthalten 0 DEX-Dateien.
- Geprüft wurden insbesondere zentrale CGM-Identität, Resolver-Konsumenten, revisionierte Persistenz, Collector-/BLE-Policy, GATT-Generation, Mobile-Graphen, Appearance-Persistenz sowie unveränderte Provider-/Tile-Verträge.
- `CODE-VALIDIERT` und `BUILD-VALIDIERT`; `HARDWARE-TEST OFFEN` für BLE/OEM-Scheduling, DND und reale Process-Recovery nach diesem Refactor.

## Main-Hardening-Nachprüfung, 2026-09-20

- Repositoryzustand vor der Nachprüfung: `main` entsprach `origin/main` bei
  `1919ace0995cbae245353319556926fb667b4272`; Arbeitsbaum sauber, keine offenen
  Pull Requests und keine offenen GitHub-Issues.
- Die aktuelle Produktmatrix umfasst Digital und Vigil plus das technische
  Testface, insgesamt drei Module. Historische Zahlen in den nachfolgenden
  versionsbezogenen Abschnitten bleiben als damalige Prüfergebnisse erhalten
  und beschreiben nicht den heutigen Umfang.
- Noch nicht als bestanden behauptet werden Sensorwechsel, BLE-Reconnect,
  Backfill-SLA, Akku-Langzeitverhalten, aktive/AOD-Goldens und gleichzeitige
  Kopplung mehrerer realer Uhren. Diese Punkte benötigen neue Hardwarebelege.
- Der saubere Gesamtgate-Lauf (`clean test assembleDebug lint detekt
  ktlintCheck`) war nach 2.120 Tasks erfolgreich. 148 JUnit-Suites mit 823
  Tests liefen ohne Fehler oder Auslassung; 38 Lint-Berichte enthalten null
  Findings. Detekt und Ktlint sind ebenfalls grün.
- Der damalige Abschlusslauf vor Entfernung der ausgemusterten Module umfasste
  3.336 Tasks und 30/30 historische WFF-Artefakte. Diese Zahl ist keine
  aktuelle Produktanforderung. Die aktive Matrix validiert und prüft jetzt
  Digital, Vigil und das technische Testface (3/3).
- Android Lint schreibt beim vollständigen sauberen Lauf für acht unveränderte
  MP4-Alarmressourcen je zwei rohe XML-Parserdiagnosen auf stderr. Sie sind
  keinem Lint-Check oder Finding zugeordnet; die 38 maschinenlesbaren Berichte
  bleiben leer. Breites Unterdrücken oder Umkodieren der Alarmdateien wurde
  deshalb bewusst nicht als vermeintlicher Quellcode-Fix übernommen.

## Sugarlicious 0.6.1, 2026-08-09

- Carousel-Nachprüfung auf Samsung SM-S948B, 1440×3120: Rahmen und Zifferblatt
  wurden gemeinsam auf 135 dp vergrößert. Der aktive Inhalt liegt konzentrisch
  im Galaxy-Watch-Ultra-Rahmen; links und rechts ist jeweils genau ein Nachbar
  mit symmetrischem Mittelpunkt sichtbar, weiter außen liegende Seiten sind
  ausgeblendet.
- Zwei gezielte JUnit-Suites mit zehn Tests: 0 Fehler, 0 Fehlschläge, 0
  übersprungen. Der neue Regressionstest bestätigt, dass das Speichern von
  `watchFaceIndex` dieselbe ComposeView behält und keinen Dashboard-Neuaufbau
  mehr auslöst. Mobile-Debug-Build und Mobile-Lint erfolgreich, 0 Lint-Fehler.

- AndroidAPS `dev` unmittelbar vor Abschluss erneut live bei
  `7fc8205e9a73259cec2982fc199f3d2055f84347` bestätigt; kein Upstream-Delta
  während des Arbeitspakets.
- Gezielte Regression für AAPS-Adapter, Wear-Protokoll, Mobile, Wear und
  Complications erzeugte 14 JUnit-Suites mit 48 Tests: 0 Fehler,
  0 Fehlschläge, 0 übersprungen. Neu geprüft werden SMB-Parsing,
  Verlaufsakkumulation, Graphrollen, Notification und Ein-Schritt-Karussell.
- Mobile-Debug, Wear-Debug, Complications-Debug und Sugarlicious-Rings-Release
  erfolgreich gebaut. Der kombinierte Gradle-Aufruf überschritt nach Abschluss
  der XML-Testausgabe das 120-Sekunden-Clientfenster; deshalb wurde der Timeout
  nicht als Erfolg gewertet. Die Module und Artefakte wurden anschließend
  einzeln geprüft.
- Der gepinnte offizielle Google-Validator akzeptierte das geänderte
  Sugarlicious-Rings-Dokument als WFF v1. Das Release-APK enthält kein
  `classes*.dex`.
- Mobile- und Wear-Lint: 0 Fehler. Mobile enthält drei bekannte Hinweise
  (API-37-Verfügbarkeit, Ziel-API 36 und bestehender Adaptive-Icon-v26-Ordner),
  Wear einen bestehenden Icon-Ordner-Hinweis. Der neu erkannte Pager-
  Performancehinweis wurde behoben und verschwand im Wiederholungslauf.
- API-35-Phone-Emulator, 1080×2400: finalen Build nach vollständigem Emulator-
  Neustart installiert. Die einzige Uhrenressource ist die gelieferte SVG;
  Gehäuse und damaliges 100-dp-Zifferblatt lagen sichtbar konzentrisch und das Zifferblatt
  füllte den runden Displayausschnitt. Drei absichtlich sehr lange Wischgesten
  schalteten jeweils exakt eine Stufe (`Graph → Analog → Orbit → Rings`). Kein
  ANR oder Absturz trat mit dem finalen asynchronen Loader auf.
- Ein früher Versuch, die 3,8-MB-SVG über WebView zu rendern, verursachte im
  Emulator reproduzierbar einen ANR und wurde vollständig entfernt. Dieser
  verworfene Zwischenstand ist nicht Teil des finalen Builds.
- Mobile-Debug-APK nach Carousel-Nachprüfung: 16.855.659 Byte, SHA-256
  `F7DDA7D54AFC55C294DCC0B61105BE4C74E1F90AE8131A3ED57190E0AF93EB9B`.
- Wear-Debug-APK: 14.440.432 Byte, SHA-256
  `CBB40AA17BA570A9FF12E020647C4203F70354C1CC93B9D4931705D9EA57922B`.
- Sugarlicious-Rings-Release-APK: SHA-256
  `92F05248F59D20E1D4ECDF718BCEDA2559DB7C2E57C3CDE2041D01711D373F56`.

Nicht als bestanden behauptet werden die reale Galaxy-Watch-Ultra-Installation,
AOD der geänderten Rings-Version, die One-UI-Live-Hervorhebung und eine
archivierte Zeitlupen-Goldenaufnahme des Carousel-Wechsels.

## Sugarlicious 0.6.0, 2026-08-09

- AndroidAPS `dev` erneut live bei
  `7fc8205e9a73259cec2982fc199f3d2055f84347` bestätigt; kein Delta zum
  vorherigen Audit. xDrip+ `master` bei
  `c2a0ba1a8f69d5f93610a83695656bd0fd15a142` auditiert.
- Vollständiger `tools/build-release.ps1`-Lauf erfolgreich: 2.599 Gradle-Tasks,
  beide Apps, alle Tools, 28 auslieferbare WFF-Pakete und das Testface.
- Gesamte Testmatrix erfolgreich: Core-Modell, AAPS- und xDrip-Adapter,
  Wear-Protokoll, Wear-Storage, Complications, Mobile, Wear sowie Parser,
  Generator und Screenshot-Comparator. 19 Suites, 64 Tests, 0 Fehler,
  0 Fehlschläge, 0 übersprungen.
- Alle 29 WFF-XML-Dateien bestanden den fest gepinnten offiziellen Validator
  als WFF v1. Die vier in diesem Arbeitspaket gebauten Sugarlicious-Release-
  APKs enthalten kein `classes*.dex`.
- Gemergtes Wear-Debug-Manifest geprüft: 27 Complication-Provider, 27 statische
  Vorschauen; Provider 02 deklariert `SHORT_TEXT,RANGED_VALUE`.
- Gemergter Runtime-Quellstand geprüft: keine `INTERNET`-Berechtigung, kein
  Nightscout-Backfill/-Client und keine Hamburger-/Overflow-View mehr.
- `lintDebug` für Mobile und Wear: 0 Fehler. Verbleibend sind drei Mobile-
  Hinweise (bewusstes API-36-Ziel bei installiertem API 37 sowie der
  Adaptive-Icon-Ordner) und ein gleichartiger Wear-Icon-Hinweis. Das von Lint
  vorgeschlagene Verschieben aus `mipmap-anydpi-v26` wurde praktisch geprüft
  und von AAPT mit fehlender Launcher-Ressource abgelehnt; die gültige Struktur
  bleibt deshalb erhalten.
- Mobile-Debug-APK: 18.110.812 Byte,
  SHA-256 `8EA0F52B35D10752C6922674ABDB2825FFA389496F19D668E5E482542903F43D`.
- Wear-Debug-APK: 14.440.432 Byte,
  SHA-256 `E37C2C672836D53F6AD24392A44F5E5A8074CF6CCD98769017659E34BC1C6D6B`.
- DIY-Paket: `dist/sugarlicious-0.6.0-diy-preview.zip`; die maßgebliche
  Prüfsumme wird daneben in `.zip.sha256` erzeugt.

Nicht als bestanden behauptet werden ein neuer visueller Telefon-Screenshot,
die One-UI-Live-Hervorhebung und ein aktiver/AOD-Lauf der vier 0.6.0-WFFs auf
realer Hardware. Diese Punkte benötigen die angeschlossenen Geräte.

## Sugarlicious 0.5.1, 2026-08-07

- AndroidAPS-`dev` bei `7fc8205e9a73259cec2982fc199f3d2055f84347`
  fixiert und das Ein-Commit-Delta vollständig geprüft. Der externe Tizen-
  Statussender blieb byteidentisch; geändert wurde nur interne AAPS-Szenen-
  und Wear-Steuerlogik.
- Gezielter Lauf `:app-mobile:testDebugUnitTest :app-mobile:assembleDebug`:
  13 Tests aus vier Suites, keine Fehler, Fehlschläge oder Überspringungen.
- Gezielter Lauf `:app-wear:testDebugUnitTest :app-wear:assembleDebug`: ein
  Test aus einer Suite, kein Fehler, Fehlschlag oder Skip.
- Neue Regressionen prüfen die normale laufende Standardbenachrichtigung,
  `START_STICKY`, den Low-Importance-Kanal, den Boot-Empfänger, den optionalen
  API-36-Promoted-Ongoing-Status ohne Glukosewert, die Inline-Umschaltung und
  die offizielle Promotion-Einstellung. Palette und Systemakzent werden als
  konkrete RGB-Werte geprüft.
- Eine visuelle Prüfung der Live-Darstellung auf realer One-UI-8.5-Hardware
  wurde noch nicht ausgeführt und wird nicht als bestanden behauptet.

Der vollständige `tools/build-release.ps1`-Abschlusslauf war erfolgreich:

- 2.314 Gradle-Tasks: 2 ausgeführt, 2.312 aktuell
- 16 JUnit-XML-Suites: 50 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen
- 26 von 26 WFF-XML-Dateien im offiziellen gepinnten Validator gültig
- 26 von 26 WFF-Release-APKs ohne `classes*.dex`
- Mobile-Debug-APK: `app.aapswear`, Version 0.5.1/Code 7
- Wear-Debug-APK: `app.aapswear`, Version 0.5.1/Code 7
- Mobile-Manifest enthält `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`,
  `POST_PROMOTED_NOTIFICATIONS` und `RECEIVE_BOOT_COMPLETED`; keine
  `INTERNET`-Berechtigung. Service-Typ, nicht exportierter Service und
  dokumentierter `specialUse`-Untertyp wurden im gemergten Manifest geprüft.
- DIY-Paket: `dist/sugarlicious-0.5.1-diy-preview.zip`; die maßgebliche
  Prüfsumme steht in der daneben erzeugten `.sha256`-Datei.

## Sugarlicious 0.5.0, 2026-08-06

- AndroidAPS-`dev` vor Arbeitsbeginn unverändert bei `e1068e77…` bestätigt.
- `app-mobile:assembleDebug`, `app-wear:assembleDebug` und beide neuen
  WFF-Release-APKs erfolgreich gebaut.
- Neue Robolectric-Fälle prüfen die Sugarlicious-App-Info samt GitHub-/Mail-
  Intents sowie den sicheren datenlosen Start des Wear-Tile-Dashboards. Der
  gemeinsame Mobile-/Wear-Testlauf war erfolgreich.
- Der gepinnte offizielle Google-WFF-Validator akzeptierte
  `sugarlicious-digital` und `sugarlicious-analog` als WFF v1.
- Wear-OS-6-Emulator: Android 16/API 36, 480×480 rund. Wear-App mit rein
  synthetischem aktuellen Zustand und Scrollbereich sichtbar geprüft. Beide
  Watchfaces installiert, aktiviert sowie aktiv und in echtem Doze/AOD
  aufgenommen; Report und PNGs liegen unter
  `docs/test-artifacts/wear-os-6/sugarlicious-0.5.0/`.
- Kein echter Gesundheitswert wurde für diese Screenshots verwendet. Der
  Empfänger `DebugStateReceiver` liegt ausschließlich unter `src/debug` und
  ist im Release-Build nicht vorhanden.

Der vollständige `tools/build-release.ps1`-Abschlusslauf war erfolgreich:

- 2.314 Gradle-Tasks: 254 ausgeführt, 2.060 aktuell
- 15 JUnit-XML-Suites: 44 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen
- 26 von 26 WFF-XML-Dateien gültig (25 auslieferbare Pakete plus Testface)
- 26 von 26 WFF-Release-APKs ohne `classes*.dex`
- Wear-Release-Manifest separat geprüft: kein `DebugStateReceiver` und keine
  Aktion `app.aapswear.DEBUG_INJECT_STATE`
- DIY-Paket: `dist/sugarlicious-0.5.0-diy-preview.zip`
- ZIP-SHA-256:
  `9ff8786ca7d3f73a374f9a545783460fa24b564718ac7b09069aac0a9440ca0d`
  (maßgeblich ist die daneben erzeugte `.sha256`-Datei)

Der zusätzlich gestartete API-35-Phone-Emulator zeigte beim Kaltstart einen
plattformseitigen `Pixel Launcher isn't responding`-Dialog über jeder App.
Diese überlagerten Bilder wurden entfernt und nicht als Smartphone-Sichttest
gewertet. Mobile-Build, Ressourcen, App-Info und externe Intents sind durch den
erfolgreichen Abschlusslauf und Robolectric abgedeckt; ein neuer unverdeckter
0.5.0-Phone-Golden wird nicht behauptet.

## Smartphone-Dashboard 0.4.0, 2026-08-05

Ausgeführt: `gradlew test assembleDebug --no-daemon --max-workers=1` sowie der
vollständige Releaseweg. Ergebnis: **erfolgreich**; der Abschlusslauf meldete
2.163 Aufgaben. Die aktuell erzeugten 14 JUnit-XML-Dateien enthalten 42 Tests,
0 Fehler, 0 Fehlschläge und 0 übersprungene Tests.

Neu abgedeckt sind der sichere AAPS-`predBGs`-Parser, fehlende/ungültige
Prognosen, der auf 24 Stunden/300 Punkte begrenzte Verlaufspuffer, DataStore-
Weiterverwendung, Live-Diagnose, persistente Inline-Einstellungen sowie native
Canvas-Pixelprüfungen für feste grüne CGM-/Zielband-Darstellung, echte
Prognoseserien und getrennte blaue IOB-/orange COB-Flächen. Die Farben stammen
aus der vorgegebenen Bildvorlage; es gibt keine grenzwertabhängige
Glukosefarbwahl.

Zusätzlich wurde ein isolierter API-35-Phone-Emulator mit 1080×2400 Pixeln und
420 dpi angelegt. Mit rein synthetischen Broadcastwerten wurden Übersicht,
Verlauf, Daten und Einstellungen gerendert und über UI-Automator geprüft. Nach
dem ersten Sichtlauf wurden Zielwert-Umbruch, Statusfarbhierarchie,
Graphstandardhöhe, Statistik-Ellipsierung und die Überlagerung der
Verbindungskachel korrigiert. Im letzten Lauf lag `80–160` in einer Zeile; die
Verbindungskachel endete vollständig oberhalb der festen Navigation, und alle
drei weiteren Bereiche waren über die flache Navigation erreichbar.

Die Mobile-Debug-APK 0.4.0/Code 5 wurde erfolgreich auf dem Samsung SM-S948B
installiert. Ein privater Screenshot wurde nicht archiviert. Das Gerät war beim
Sichtvergleich gesperrt, weshalb kein bestandener pixelgenauer Realgeräte-
Vergleich behauptet wird. Die Wear-App 0.4.0 konnte in diesem Lauf nicht auf die
physische Uhr übertragen werden, weil sie von ADB nicht angeboten wurde.

Der anschließende vollständige `tools/build-release.ps1`-Lauf war ebenfalls
erfolgreich. Der gepinnte offizielle Validator akzeptierte 24 von 24 WFF-v1-
Dateien; die Inhaltsprüfung bestätigte 24 von 24 Release-APKs ohne DEX. Das
Paket `aaps-wear-watchfaces-0.4.0-diy-preview.zip` enthält 23 auslieferbare
Watchfaces und besitzt die SHA-256-Prüfsumme
`f7b72b03fc4d960412109a5b4c55e4e095d8e81afd79ac9818a42fdb6d44f3d4`.

## Reale Samsung-Telefon-/Uhr-Prüfung 2026-08-05

- Smartphone: Samsung SM-S948B
- Uhr: Samsung SM-L705F, Android 16/API 36,
  `ro.build.version.oneui=80000` (One UI Watch 8)
- AndroidAPS auf Telefon und Uhr: `4.0.0-dev-b`, VersionCode 1500
- eigene Mobile-/Wear-App: `app.aapswear`; Mobile-Patch 0.3.1/Code 4
- offizielles AAPS-V4-WFF und eigenes AAPS-V4-WFF gleichzeitig installiert

Die mobile Bridge empfing einen gültigen
`info.nightscout.androidaps.status`-Broadcast, erkannte
`AAPS_EXTENDED_STATUS_V1`, meldete eine erreichbare Uhr und schloss den
Data-Layer-Versand mit `ok` ab. Die DataStore-Dateien auf Telefon und Uhr wurden
in derselben Minute aktualisiert. Beim erneuten Auslesen zeigte die Mobile-UI
AAPS-Version, Vertrag, Empfangs-/Messzeit, eine Uhr und `übertragen`.

Die zunächst beobachtete scheinbar leere Mobile-App war ein Anzeigeproblem:
Eine bereits sichtbare Activity beobachtete keine späteren Änderungen an den
Diagnose-SharedPreferences. Version 0.3.1 registriert den Listener nur im
sichtbaren Lifecycle. `MainActivityTest` reproduziert und prüft diesen Ablauf.
Gezielter Testlauf und Mobile-Debug-Build waren erfolgreich; das APK wurde als
Update auf dem realen Smartphone installiert und als 0.3.1/Code 4 bestätigt.

Dass offizielles und eigenes AAPS-V4-WFF dieselben Therapiewerte zeigen, ist
erwartet: Beide bilden dieselbe AAPS-V4-Oberfläche nach, verwenden aber ihre
jeweiligen Complication-Provider. Die übereinstimmenden aktuellen Werte passen
zum nachgewiesenen erfolgreichen Bridge-/Data-Layer-Zustand.

## Abschlusslauf 2026-08-03

Ausgeführt durch `tools/build-release.ps1`:

- `gradlew test assembleDebug`
- Mobile- und Wear-`assembleRelease`
- Release-Build für technisches Testface und 23 ausgelieferte WFF-Pakete
- offizieller Google-WFF-Validator auf Commit `702e9bdf…`
- APK-Inhaltsprüfung auf `classes*.dex`

Ergebnis: **erfolgreich**. Gradle meldete 2.156 Tasks, davon 1.195 ausgeführt
und 961 aktuell, in 4 Minuten 27 Sekunden. Zehn JUnit-XML-Suites enthielten
34 Tests: 0 Fehler, 0 Fehlschläge, 0 übersprungen.

Abgedeckt sind unter anderem Parsing bekannter/fehlender/unbekannter/falsch
typisierter AAPS-Felder, mg/dl und mmol/l, Trends, Deltaformatierung,
Zeitstempel/Future-Rejection/Frische, IOB/COB/Basal/Pumpe, Vertragserkennung,
Schema-1-zu-2-Migration, DataStore-Wiederherstellung, alle 27 Provider,
Ranged-Value-Ausgaben, stale-Sperren, CWF-Sicherheit, Generator-Degradation und
Screenshot-Differenzmetriken.

## WFF und APKs

- 24 von 24 `watchface.xml`: gültig gegen WFF v1
- 24 von 24 WFF-Release-APKs: kein DEX, damit codefrei
- alle WFF-Pakete: VersionName 0.3.0, VersionCode 3
- ausgeliefertes Paket: 23 Watchface-APKs, 2 App-APKs, Dokumente und Lizenzen
- `SHA256SUMS.txt`: 44 Einträge, 0 Abweichungen bei erneuter Prüfung
- Mobile- und Wear-Debug-APK fordern keine `INTERNET`-Berechtigung an
- Debug-Testdatenreceiver fehlt nachweislich im Wear-Release-Manifest
- App-/WFF-DIY-Artefakte verwenden denselben Android-Debug-Zertifikatsschlüssel;
  dies ist ausdrücklich keine Produktionssignatur

## Android-15-Telefontest

Auf dem API-35-Phone-Emulator wurde ein Payload mit den exakt belegten
AAPS-Broadcastfeldern an `info.nightscout.androidaps.status` gesendet. Der
Zustand wurde vor der Data-Layer-Operation persistiert. Ohne gekoppelte Uhr
endete die Übertragung nach dem gesetzten Timeout als `unavailable`, ohne ANR
oder Fatal Exception. Nach Force-Stop und Prozessneustart war der 991-Byte-
DataStore-Zustand weiterhin vorhanden und die Diagnoseansicht zeigte Vertrag,
Messzeit und Verbindungsstatus korrekt. Screenshots liegen unter
`docs/test-artifacts/android-15/`.

## Wear-OS-6-Emulatortest

Ziel: runder offizieller Wear-OS-6-Emulator, 454×454 Pixel.

- Wear-App und 23 Watchface-Pakete erfolgreich installiert
- ohne Zustand: `—`, `keine Daten`, null Telefonverbindungen, read-only sichtbar
- vollständiger synthetischer Zustand im Debug-Build gespeichert und von den
  Complication-Providern gelesen
- 23 aktive und 23 echte Doze/AOD-Screenshots aufgenommen
- vor jedem AOD-Bild `mWakefulness=Dozing` und `Display State=DOZE*` geprüft
- `capture-report.json` enthält 23 Zeilen; sämtliche PNG-Hashes stimmen
- automatisierte Sichtbarkeitsprüfung: kein aktives Bild unter 1 Prozent
  nicht-schwarzer Pixel; Minimum 3,1637 Prozent bei AIMICO
- AAPS V4 nach abgeschlossener asynchroner Providerbindung erneut aufgenommen;
  sichtbar sind Glukose, Trend, IOB, COB, Verlauf und Status

Goldens mit Daten liegen unter
`docs/test-artifacts/wear-os-6/watchfaces-data/`; die vorherigen No-Data-
Regressionsbilder bleiben getrennt erhalten.

## Nicht als bestanden behauptet

- gezielte Bluetooth-Unterbrechung/Wiederverbindung und mehrere gekoppelte Uhren
- zweites reales Wear-OS-Gerät ohne Samsung-Oberfläche
- pixelgleiche Original-vs.-Port-Aufnahmen auf identischer realer Hardware
- produktive Release-Signatur und Store-/öffentliche Release-Freigabe
- PinkFloydTheWall; rechtlich blockiert und nicht gebaut

Diese offenen Punkte verhindern die Behauptung, die Version-1-Abnahmekriterien
seien vollständig erfüllt. Der vorhandene Stand ist ein gebautes und breit
automatisiert/emuliert geprüftes DIY-Vorschaupaket.

## Abschlusslauf 2026-08-05

Nach dem Realgeräte-Fix wurde `tools/build-release.ps1` ohne übersprungene
Buildphase erneut ausgeführt. Ergebnis: **erfolgreich**.

- 2.163 Gradle-Tasks: 24 ausgeführt, 2.139 aktuell
- 11 JUnit-XML-Suites: 35 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen
- 24 von 24 `watchface.xml`: gültig mit dem auf Commit `702e9bdf…`
  gepinnten offiziellen Google-WFF-Validator
- 24 von 24 WFF-Release-APKs: ohne `classes*.dex`
- Mobile-Debug-APK 0.3.1/Code 4 zuvor erfolgreich auf SM-S948B installiert;
  vorhandener AAPS-Empfang und Data-Layer-Zustand blieben nach dem Update erhalten
- Paket: `aaps-wear-watchfaces-0.3.1-diy-preview.zip`; die daneben erzeugte
  `.sha256`-Datei enthält die Prüfsumme des vollständigen ZIP-Archivs

Der erste Versuch deckte einen unvollständigen temporären Git-Cache des
WFF-Validators auf. Nach der eng begrenzten Cache-Reparatur bestand sowohl der
isolierte Validatorlauf als auch der vollständige Release-Lauf. Die öffentliche
AndroidAPS-Dev-Referenz blieb währenddessen unverändert auf
`e1068e77db4f801c046340c8313cd7a2856f4e7c`.

## Sugarlicious 0.6.2 – Abschlusslauf 2026-08-09

- `DashboardChartsTest`: 5 Tests, 0 Fehler; feste logarithmische CGM-Skala,
  Ziel-/Prediction-Farben sowie IOB/COB/SMB/Insulinaktivität gerendert.
- `OverviewWatchFaceTileTest`: 4 Tests, 0 Fehler; Karussell-Einzelschritt,
  Nachbarsichtbarkeit und vier Watchface-Karten geprüft.
- `MainActivityTest`: 7 Tests, 0 Fehler; Navigation, Aktualisierung und Erhalt
  der Compose-Ansicht geprüft.
- `PersistentBridgeServiceTest`: 3 Tests, 0 Fehler; Notification-Layout und
  gerundeter Graph-Renderer geprüft.
- `TherapyComplicationsTest`: 6 Tests, 0 Fehler; Complication 02 liefert im
  `RANGED_VALUE` Wert `123`, Trend `↗`, Bereich 40–260 und keine Bilddaten.
- `:app-mobile:assembleDebug`, `:app-wear:assembleDebug`: erfolgreich;
  Versionen Mobile 0.6.2/10 und Wear 0.6.2/12 per `aapt` verifiziert.
- `:app-mobile:lintDebug`: 0 Fehler, 3 bestehende Warnungen (SDK-/Ressourcen-
  Hinweise, keine neue Quellcodewarnung).
- Offizieller Google-WFF-Validator: Analog, Orbit, Rings und Graph jeweils
  gültig gegen WFF v1.
- WFF-Release-Paketcheck: vier APKs signiert, jeweils `android.hardware.type.watch`,
  eindeutige Application-ID, Version 0.6.2 und 0 DEX-Dateien.
- Nicht ausgeführt: Installation auf Hardware, Galaxy-Wearable-Erkennung,
  aktive/AOD-Screenshotkontrolle; beim Abschluss war kein ADB-Gerät verbunden.
