# Implementation Log

## 2026-08-09, 19:47 +02:00, Carousel-Geometrie und flackerfreier Wechsel

- Geändert: `OverviewWatchFaceTile.kt`, `MainActivity.kt`, die zugehörigen
  Mobile-Tests sowie Changelog und Testbericht.
- Zweck: nur den unmittelbaren linken und rechten Watchface-Nachbarn zeigen,
  beide exakt symmetrisch zum festen Uhrenrahmen ausrichten, die komplette
  Uhrendarstellung um 35 Prozent vergrößern und das gemeldete Aufblinken beim
  Einrasten beseitigen.
- Umsetzung: Carousel-Höhe 224 dp, Face-Größe 135 dp und symmetrisch berechnete
  Seitenränder; Seiten ab Abstand 1,25 sind vollständig transparent. Der SVG-
  Cache liefert bei einer erneuten Komposition sofort sein Bild. Änderungen
  von `watchFaceIndex` speichern und übertragen weiterhin die Auswahl, lösen
  aber keinen vollständigen Dashboard-Neuaufbau mehr aus.
- Tests: zehn gezielte Mobile-Tests in zwei Suites ohne Fehler, Fehlschlag oder
  Skip; darunter unmittelbare-Nachbarn-Logik, Ein-Schritt-Geste und Erhalt
  derselben ComposeView beim Speichern. Mobile-Debug-Build und Lint erfolgreich,
  Lint mit 0 Fehlern. APK auf Samsung SM-S948B installiert; die aktive Uhr und
  jeweils genau ein gleich weit entfernter Nachbar wurden auf dem 1440×3120-
  Display visuell geprüft. Kein Fatal- oder ANR-Eintrag im kontrollierten Log.
- Einschränkungen: Der Touchwechsel wurde automatisiert funktional und über
  View-Identität geprüft; eine Zeitlupenaufnahme auf dem realen Gerät wurde
  nicht als separater Golden-Test archiviert.
- Nächster Schritt: dieselbe Carousel-Geste zusätzlich auf einer kleineren
  Displaybreite und mit aktiviertem hellen Modus visuell abnehmen.

## 2026-08-09, 18:42 +02:00, Graphparität, SMB, Karussell und SVG-Uhrenrahmen 0.6.1

- Geändert: Mobile-Graphen, Verlaufsakkumulator, AAPS-SMB-Parser, Kernmodell,
  Wear-Protokoll, Notification, Farbwahl, Watchface-Karussell, Watch-/Über-
  Ansichten, Rings-WFF, Versionen, Tests, Lizenz- und Projektdokumentation.
- Zweck: die gemeldeten Graph-, Gesten-, Ausrichtungs-, Notification- und
  Einstellungsabweichungen korrigieren und den gelieferten Galaxy-Watch-Ultra-
  Rahmen ohne zusätzliche PNG-Ressource einsetzen.
- Umsetzung: lineare dynamische Graphskalen; AAPS-orientierte invertierte
  Basal/TBR-Spur samt gestricheltem Basisbasal; gelbe Aktivität mit
  gestrichelter Zukunft; IOB/COB-Lanes und SMB-Dreiecke; Alpha-/AARRGGBB-
  Farbeingabe; größerer Notification-Graph; exakt ein Karussellschritt pro
  Geste; 100-dp-Zifferblatt mit gemessenem -5-dp-Vertikalversatz. Die gelieferte
  SVG enthält selbst ein Base64-PNG. Dieses wird auf einem IO-Dispatcher
  validiert, mit Sample-Faktor 2 dekodiert und pro Prozess zwischengespeichert;
  JavaScript, WebView und Netzverkehr sind nicht beteiligt.
- Tests: 14 JUnit-Suites/48 Tests ohne Fehler, Fehlschlag oder Skip; Mobile,
  Wear, Complications und Rings gebaut; Rings offiziell als WFF v1 validiert
  und ohne DEX geprüft. Im API-35-Emulator waren SVG-Rahmen und Zifferblatt
  konzentrisch; drei sehr lange Wischgesten wechselten jeweils genau ein Face.
  Mobile-/Wear-Lint meldete keine Fehler; ein neuer Pager-Performancehinweis
  wurde durch Layer-lokales Lesen des laufenden Offsets behoben.
- Während der Prüfung behoben: WebView- und synchroner SVG-Ladeversuch führten
  wegen der großen eingebetteten Rastergrafik zum ANR. Beide Wege wurden
  entfernt und durch den asynchronen Loader ersetzt; der saubere Neustart des
  finalen Builds zeigte keinen ANR.
- Einschränkungen: Die SVG ist keine echte Pfad-Vektorgrafik. Historische SMBs
  existieren nur ab lokalem Empfang. Realtelefon, reale Galaxy Watch Ultra,
  Rings-AOD und One-UI-Live-Notification wurden in diesem Paket nicht visuell
  abgenommen.
- Nächster Schritt: Mobile-/Wear-/Rings-APKs auf den angeschlossenen Geräten
  installieren und dort Touch, Complication-Picker, AOD und Notification
  prüfen.

## 2026-08-09, 10:36 +02:00, interaktive Graphen, xDrip+, vereinfachte UI und vier Analog-WFFs

- Geändert: Mobile-Dashboard und Graph-Canvas, Verlaufsakkumulator, Farbrollen
  und HSV-Farbwähler, Watchface-Karussell, Benachrichtigungsservice,
  AAPS-/xDrip-Empfänger, Modell/Protokoll/Watch-Speicher, Wear-Graphen,
  Complication-Provider/-Manifest/-Previews, vier Sugarlicious-WFF-Module,
  Build-/Screenshotskripte, Tests und Dokumentation.
- Zweck: die vorgegebenen Interaktionen und die Nightscout-Toolkit-artige
  Graphlogik umsetzen, technische Nutzeroberflächen entfernen, lokale xDrip-
  Glukose optional zulassen und vier größere analoge Sugarlicious-Designs
  bereitstellen.
- Umsetzung: Touch-Viewport mit Einfinger-Pan und Zweifinger-Zoom; dynamische
  Log-Skalierung; ausschließlich Zielgrenzen rechts; AAPS-Basal/TBR als
  invertierte cyanfarbene Spur; quellenpriorisierte 90-Sekunden-Deduplizierung;
  Prediction-Clipping rechts der Jetzt-Linie; getrennte Farbrollen über
  Protokoll 5; freundliches, flaches Watch-Menü; lokaler xDrip-Intentadapter;
  Standard-Foreground-Notification mit Wert/Trend/Alter/Bitmapgraph; 27
  vollständig registrierte Provider; vier deklarative Original-WFFs mit
  kräftigen eigenständigen Baton-Zeigern.
- Tests: vollständiger Releaseweg mit 2.599 Gradle-Tasks erfolgreich; 64 Tests
  aus 19 Suites ohne Fehler, Fehlschlag oder Skip; 29/29 WFF-XMLs offiziell
  valide; 29/29 WFF-APKs ohne DEX; 27/27 Provider mit statischer Vorschau im
  gemergten Manifest. Mobile- und Wear-Lint liefen ohne Fehler.
- Einschränkungen: Die Insulinaktivität ist eine ausdrücklich gekennzeichnete
  Display-Schätzung aus IOB-Abfall, nicht AAPS-Therapielogik. One-UI-Live-
  Darstellung und reale aktive/AOD-Sichtprüfung der vier 0.6.0-WFFs stehen aus.
- Nächster Schritt: APKs auf Telefon und Galaxy Watch installieren, reale
  Gesten/Live-Notification/Complication-Picker/AOD sichten und anschließend
  nur belegte visuelle Abweichungen nachschärfen.

## 2026-08-07, 00:17 +02:00, neutrale Oberfläche und zweistufige Hintergrund-Benachrichtigung

- Geändert: Mobile-/Wear-Palette, Karten-, Chip-, Navigations- und Dropdown-
  Ressourcen, FreDiabetics-Assets, Mobile-Manifest, neuer Foreground-Service
  samt Boot-Empfänger, Inline-Einstellung, Versionen, Tests, Release-Skript und
  Dokumentation.
- Zweck: die Oberfläche auf echtes neutrales Grau mit Icon-Grün als
  Systemfarbe umstellen und den lokalen AndroidAPS-/Wear-Empfang sichtbar im
  Hintergrund priorisieren. Standard bleibt eine normale Benachrichtigung;
  Live ist eine ausdrückliche Option.
- Umsetzung: farbige Rahmen durch tonale Grauflächen ersetzt, alle Menüs und
  Auswahlen stark abgerundet. `PersistentBridgeService` läuft als
  `specialUse`-Foreground-Service mit stillem Low-Importance-Kanal und
  `START_STICKY`. Er wird aus der App sowie nach Boot/App-Update angefordert.
  Ab API 36 setzt der optionale Modus den offiziellen
  `android.requestPromotedOngoing`-Hinweis, bietet „Live beenden“ an und zeigt
  ausschließlich AAPS-/Watch-Verbindungsstatus ohne Therapiewerte. Fehlt die
  Systemfreigabe, wird die offizielle Promotion-Einstellungsseite geöffnet.
- Tests: gezielte Mobile- und Wear-Unit-Tests sowie Debug-Assemblies liefen vor
  der Dokumentation erfolgreich. Abgedeckt sind normaler/laufender Standard,
  API-36-Live-Anforderung ohne Therapiedaten, Boot-Wiederanlauf, persistente
  Inline-Umschaltung, Promotion-Einstellung und neutrale RGB-Palette. Der
  vollständige Release-Abschlusslauf ist im aktuellen `TEST_REPORT.md`
  protokolliert.
- Baseline: AndroidAPS `dev` auf `7fc8205e…` aktualisiert. Das Ein-Commit-Delta
  ändert nur interne Szenen-/Wear-Steuerlogik; der gelesene Tizen-Broadcast ist
  byteidentisch.
- Einschränkungen: kein Foreground-Service kann erzwungenes Stoppen absolut
  verhindern. Die One-UI-8.5-Darstellung muss noch auf realer Hardware visuell
  bestätigt werden; Betriebssystem/OEM entscheiden über die Live-Hervorhebung.
- Nächster Schritt: die Live-Hervorhebung und den Neustartpfad auf realer
  One-UI-8.5-Hardware sichten; anschließend den kontrollierten Stand als
  öffentliche DIY-Vorschau kennzeichnen.

## 2026-08-06, 15:07 +02:00, Sugarlicious-Branding, Wear Tiles und zwei Original-WFFs

- Geändert: sichtbare Namen und Versionen von `app-mobile`/`app-wear`, Adaptive
  Icons, Logoressourcen, Smartphone-App-Info, Kontaktaktionen, Wear-Layout,
  Debug-Testdatenquelle, zwei neue WFF-Module, Release-/Screenshot-Skripte,
  Tests und Dokumentation.
- Zweck: das Produkt als Sugarlicious kennzeichnen, die Watch-App an die
  vorgegebene Tile-Optik angleichen und je ein digitales sowie analoges
  eigenständiges Watchface liefern.
- Umsetzung: bestehende Paket-ID `app.aapswear` erhalten; Logo im dunklen
  Material-Adaptive-Icon eingebettet; E-Mail/GitHub ohne INTERNET-Berechtigung
  per System-Intent geöffnet; Wear-Werte weiterhin nur bei aktuell/verzögert
  sichtbar. Beide Watchfaces sind WFF v1, deklarativ, separat installierbar und
  besitzen je acht frei belegbare Slots. Die schlanken, innen dunkel
  abgesetzten Analogzeiger wurden neu als Vektoren gezeichnet und verwenden
  keine Apple-Datei oder kopierte Apple-Geometrie.
- Tests: vollständiger Release-Lauf erfolgreich: 2.314 Gradle-Tasks, 44 Tests
  aus 15 XML-Suites ohne Fehler/Fehlschlag/Skip, 26/26 WFF-Dokumente gültig und
  26/26 WFF-APKs codefrei. Runder Wear-OS-6-Emulator 480×480: Wear-App,
  Digital/Analog, aktive Darstellung und echtes Doze/AOD mit synthetischen
  Daten aufgenommen. Wear-Release-Manifest enthält den Debugempfänger nicht.
- Einschränkungen: veröffentlichte Goldens belegen den Emulatorstand; erneuter
  physischer Sichttest auf One UI Watch 8 für diese beiden neuen Designs steht
  noch aus. Complication-Favoriten können nach umgekehrter Installationsfolge
  zunächst leere Slots behalten.
- Nächster Schritt: kontrollierten Git-Stand committen und in das bestehende
  GitHub-Repository pushen; danach neue Designs auf One UI Watch 8 sichten.

## 2026-08-05, 23:34 +02:00, Smartphone-Kacheldashboard und Graphvertrag 0.4.0

- Geändert: `app-mobile`-Dashboard, Navigation, Ressourcen, Graph-Canvas,
  Inline-Einstellungen, AAPS-Prognoseparser, begrenzter Displayverlauf,
  `core-model`, Wear-Protokoll, Mobile-/Wear-Versionen, Tests und Dokumentation.
- Zweck: die vorgegebene Smartphone-Bildvorlage mit möglichst wenigen
  Untermenüs umsetzen und echte, ausschließlich lesend empfangene AAPS-Daten
  für Verlauf und Prognosen nutzbar machen.
- Umsetzung: vier flache Bereiche (Übersicht, Verlauf, Daten, Einstellungen),
  feste Bild-Palette, Nightscout-Data-Toolkit-Prinzipien für Skalen/Flächen,
  echte Suggested-/Enacted-`predBGs`, 24-Stunden-/300-Punkte-Puffer im letzten
  Zustand. GlucoDataHandler wurde nur als Provider-/Hintergrundreferenz
  betrachtet; weder Design noch Code wurden übernommen. Der Glukosewert erhält
  keine Hypo-/Ziel-/Hyper-abhängige Farbe.
- Tests: vollständiger Release-Lauf erfolgreich; 2.163 Aufgaben, 42 Tests aus
  14 JUnit-Dateien, keine Fehler/Fehlschläge/Überspringungen.
  Mobile-APK 0.4.0/Code 5 auf SM-S948B installiert. Vollständiger Release-Lauf
  erfolgreich; 24/24 WFF-Dateien gültig, 24/24 WFF-APKs ohne DEX. Das
  0.4.0-DIY-ZIP wurde samt externer SHA-256-Datei erzeugt.
- Sichtprüfung: eigener API-35-Phone-Emulator (1080×2400, 420 dpi) mit
  synthetischen Daten. Zielwert und Statistiktexte auf eine Zeile begrenzt,
  feste Statusfarbhierarchie hergestellt und kompakte Graphhöhen so angepasst,
  dass die Verbindungskachel oberhalb der festen Navigation liegt. Übersicht,
  Verlauf, Daten und Inline-Einstellungen per UI-Automator erreicht.
- Baseline: AndroidAPS `dev` unmittelbar vor Dokumentation unverändert bei
  `e1068e77db4f801c046340c8313cd7a2856f4e7c`; GlucoDataHandler unverändert bei
  `114460bc29973c78580095e2e9b0f212eed9df20`.
- Einschränkungen: physischer Sichtvergleich wegen gesperrtem Telefon noch
  offen; Watch in diesem Lauf nicht per ADB sichtbar; Verlauf baut sich erst
  aus zukünftigen realen Broadcasts auf.
- Nächster Schritt: entsperrten Realgeräte-Screenshot prüfen, Wear-App 0.4.0
  installieren und anschließend den kontrollierten Git-Stand veröffentlichen.

## 2026-08-02, initiales Arbeitspaket

- Geändert: Projektaufbau, Kernmodell, AAPS-Adapter, Wear-Protokoll, mobile/watch Apps, DataStore, Complication, WFF-Testmodul und Audit-Dokumente.
- Zweck: reproduzierbare Phase-0-Baseline plus minimaler read-only End-to-End-Prototyp.
- Umsetzung: AAPS-Broadcast wird defensiv normalisiert; nur der letzte Zustand wird per DataItem übertragen und auf der Uhr gespeichert; stale Glukose wird nicht als Wert angezeigt.
- Tests: Unit-Tests und Debug-Assemblies erfolgreich; separates WFF-Release erfolgreich und ohne DEX verifiziert. Details in `TEST_REPORT.md`.
- Während der Prüfung behoben: AGP-9 Built-in-Kotlin-Konfiguration, SDK-Lokalisierung, nicht verfügbare JDK-17-Toolchain, Android-freier Adaptertestpfad und korrekte `ComponentName`-Übergabe an den Complication-UpdateRequester.
- Einschränkungen: siehe `KNOWN_LIMITATIONS.md`.
- Nächster Schritt: Gradle-Wrapper erzeugen, Tests/Build ausführen, Fehler beheben; danach AAPS V4 starten.

## 2026-08-02, Ausbau 0.2.0

- Geändert: gemeinsamer Paketname, kompletter Tizen-Broadcastadapter, Capability-/Validator-/Dev-/Stable-Adapter, mobile DataStore-Persistenz, Wiederanfragepfad und 27 Provider.
- Zweck: den zuvor bekannten End-to-End-Paketblocker entfernen und den belegten aktuellen AAPS-Datenvertrag abdecken.
- Tests: Adapter-, Vertragserkennungs- und Protokolltests sowie Mobile-/Wear-Build erfolgreich; Providerzahl 27 aus Manifest geprüft.
- Einschränkungen: keine Hardware vorhanden; temporäres Ziel und voller Loopmodus fehlen im öffentlichen Broadcast.
- Nächster Schritt: AAPS-V4-WFF-Paket übernehmen/entkoppeln, danach AAPS V2 und visuelle Goldens.

## 2026-08-02, AAPS V4 Start

- Geändert: neues Ressourcenmodul `watchfaces/aaps-v4` mit fünf AAPS-Provider-Slots und Ambient-Varianten.
- Zweck: Beginn der manuellen AAPS-V4-Portierung ohne ausführbaren Watchface-Service.
- Tests: Release-Build erfolgreich; kein `classes.dex` im APK.
- Einschränkung: Layout ist noch nicht per Golden-Screenshot gegen das originale AAPS-V4-WFF abgenommen.
- AAPS V2: offizielles ZIP analysiert; Metadaten nennen Andrew Warrington und Philoul. Die spätere Repository-Prüfung hat die AGPL-3.0-Herkunft belegt.

## 2026-08-02, Stabilisierung, originale AAPS V4 und AAPS V2

- Geändert: zentrale Formatierung; Ranged-Value-Ausgabe für Reservoir und beide
  Batterien; globale Stale-Sperre für Therapiewerte und Bilder; aktive
  Wiederanfrage beim Öffnen der Uhr; fortlaufende Wear-Diagnose; DataStore- und
  Provider-Tests; originale AAPS-V4-Ressourcen; neues AAPS-V2-WFF; AGPL-
  Lizenztexte und Herkunftsnachweise; reproduzierbare WFF-/No-DEX-Prüfskripte
  und CI.
- Zweck: Neustart-/Reconnect-Verhalten absichern, niemals alte IOB-/COB-/Pumpen-
  Daten als aktuell zeigen und die ersten beiden Kernwatchfaces als echte,
  codefreie WFF-Pakete liefern.
- Umsetzung: AAPS V4 entspricht der Upstream-XML plus exakt fünf Provider-
  Ersetzungen. AAPS V2 bildet das 400×400-CWF mit dem WFF-Maximum von acht
  Complication-Slots nach. Der offizielle Google-Validator ist auf Commit
  `702e9bd` gepinnt.
- Tests: Formatter-, DataStore- und Provider-Robolectric-Tests bestanden;
  Wear-Debug-Build bestanden; alle drei WFF-v1-Dateien offiziell validiert;
  drei Release-APKs gebaut und ohne DEX bestätigt.
- Während der Prüfung behoben: Robolectric 4.13 war nicht JDK-25-kompatibel und
  wurde auf 4.16.1 aktualisiert; der Validator deckte zwei fehlerhafte Attribute
  im alten Test-WFF auf, die korrigiert wurden.
- Einschränkungen: reale Data-Layer-Paarung, AOD/Burn-in, Emulator-/Hardware-
  Screenshots und One UI Watch 8 sind weiterhin nicht ausgeführt.
- Nächster Schritt: Emulator-Paar aufsetzen, mobile Broadcastzustellung und
  Data Layer integriert prüfen, danach AAPS Standard/Circle/Digital Style und
  die Parser-/Generatorwerkzeuge vervollständigen.

## 2026-08-02, 21:40 +02:00, Migrations- und Screenshot-Werkzeuge

- Geändert: `tools/aaps-cwf-parser`, `tools/wff-generator`,
  `tools/screenshot-comparator`, Modulregistrierung und Migrationsdokumentation;
  Mobile-/Wear-Versionsstand auf 0.3.0 angehoben.
- Zweck: CWF-Eingaben sicher und nachvollziehbar analysieren, unvollständige
  automatische WFF-Ausgaben standardmäßig verhindern und spätere Golden-
  Screenshots quantitativ vergleichen.
- Umsetzung: ZIP-/Größen-/Typvalidierung, normalisiertes CWF-Modell,
  WFF-v1-Ausgabe mit maximal acht Slots und expliziter Degradationsfreigabe;
  PNG-Metriken MAE/RMSE/Pixelanteil/Maximalabweichung samt Differenzbild.
- Tests: vier Parser-, drei Generator- und drei Comparator-Tests bestanden.
  AAPS V2 real geparst; eine ausdrücklich degradierte Ausgabe erzeugt und mit
  dem offiziellen WFF-Validator erfolgreich geprüft.
- Bekannte Einschränkungen: automatische Konvertierung bildet dynamische
  Einstellungen/Twin-View nicht ab; Screenshot-Aufnahme selbst erfordert
  Emulator oder Uhr.
- Nächster Schritt: vollständiger Regressionsbuild; anschließend AAPS
  Standard, Circle und Digital Style untersuchen und portieren.

## 2026-08-02/03, vollständiger Port- und Emulatorlauf

- Geändert: 23 veröffentlichbare WFF-Module, vollständiges Community-Inventar,
  SHA-gepinnte Reparatur bekannter Quellarchive, Freitext-/Split-Zeit-Mappings,
  AAPS-Vertragsschema 2, Paketversionserkennung, Android-Paketvisibilität,
  Debug-Testdateneingang, Golden-Aufnahmeskript, Release-/Lizenzdokumentation.
- Zweck: sämtliche zulässig portierbaren AAPS-/Austausch-Watchfaces in
  codefreie Pakete überführen und den Datenweg bis zum WFF-Rendering prüfen.
- Umsetzung: Provider-App vor WFF installiert; 23 Favoriten mit synthetischem
  vollständigem Therapiezustand gebunden; je aktiver und echter Doze/AOD-Frame
  aufgenommen. AAPS V4 wurde nach abgeschlossener asynchroner Providerbindung
  gezielt erneut aufgenommen.
- Tests: Android-15-Broadcast/Persistenz/Prozessneustart ohne ANR; Wear-OS-6-
  Installation; 23 aktive + 23 AOD-Goldens; 23 Hashzeilen; alle aktiven Bilder
  mindestens 3,16 Prozent nicht-schwarze Pixel. Vollständiger Gradle-/Validator-
  /No-DEX-Lauf wird im Testbericht als eigener Abschlusslauf protokolliert.
- Behoben: Data-Layer-Warten im BroadcastReceiver blockierte ein Telefon ohne
  Uhr; nun persist-first plus vier Sekunden Timeout. WFF-Favoriten ohne
  Default-Provider wurden durch dokumentierte Installationsreihenfolge erkannt.
- Einschränkungen: echte gekoppelte Data-Layer-Hardware und One UI Watch 8
  fehlen; PinkFloydTheWall ist rechtlich blockiert; pixelgenaue Original-Goldens
  auf identischer Hardware bleiben offen.

## 2026-08-05, 22:21 +02:00, realer Datenweg und Mobile-Liveanzeige 0.3.1

- Geändert: `app-mobile/MainActivity.kt`, Mobile-Buildkonfiguration,
  `MainActivityTest.kt`, Changelog, Source-Baseline, Testbericht, bekannte
  Einschränkungen und Release-Checkliste.
- Zweck: gemeldete Diskrepanz untersuchen, bei der Watchfaces aktuelle Werte
  zeigten, die bereits geöffnete Smartphone-Diagnose aber keinen Empfang
  anzeigte.
- Diagnose: Die Bridge hatte einen gültigen AAPS-Broadcast als
  `AAPS_EXTENDED_STATUS_V1` gespeichert, eine Uhr erkannt und den DataItem-
  Versand mit `ok` abgeschlossen. Telefon- und Uhr-DataStore wurden in derselben
  Minute aktualisiert. Die Activity las SharedPreferences jedoch nur bei
  `onCreate`/`onResume` und beobachtete spätere Änderungen nicht.
- Umsetzung: Lifecycle-gebundener
  `OnSharedPreferenceChangeListener` registriert; jede Diagnosedatenänderung
  rendert auf dem UI-Thread neu. Mobile-Version auf 0.3.1/Code 4 angehoben.
- Tests: `:app-mobile:testDebugUnitTest` und `:app-mobile:assembleDebug`
  erfolgreich. Der neue Robolectric-Test lässt die Activity sichtbar, ändert
  den Empfangsstatus und prüft die unmittelbare Textaktualisierung. APK auf dem
  physischen Smartphone installiert; Version 0.3.1/Code 4 bestätigt.
- Realgerät: Samsung SM-S948B mit AndroidAPS 4.0.0-dev-b und Samsung SM-L705F
  mit Android 16/API 36, `ro.build.version.oneui=80000`. Bridge-Diagnose:
  eine erreichbare Uhr, gültiger Vertrag und erfolgreiche Synchronisation.
- Nicht abgeschlossen: gezielter Bluetooth-Ausfall/Reconnect, mehrere Uhren,
  reales Nicht-Samsung-Gerät und produktive Signatur.
- Nächster Schritt: vollständigen Regressionstest ausführen und Patch nach
  sauberer Diff-/Baseline-Prüfung veröffentlichen.

## 2026-08-05, 22:52 +02:00, Regression und DIY-Paket 0.3.1

- Geändert: `tools/wff-validator/validate.ps1`, Changelog, Implementierungs- und
  Testprotokoll.
- Zweck: Einen durch ein unvollständiges temporäres Git-Repository blockierten
  WFF-Abschlusslauf reproduzierbar fortsetzen und den Realgeräte-Fix vollständig
  gegen das Projekt prüfen.
- Umsetzung: Der Prüfer validiert den Cache jetzt als echtes Git-Repository.
  Ausschließlich der exakt erwartete commitbezogene Ordner im Windows-Temp-
  Verzeichnis darf bei Beschädigung neu erzeugt werden; jeder native Git-Schritt
  wird auf seinen Exitcode geprüft.
- Tests: `tools/build-release.ps1` vollständig erfolgreich; 35 Tests in elf
  JUnit-Suites ohne Fehler, Fehlschlag oder Überspringen; 24 WFF-Dateien gegen
  WFF v1 gültig; 24 WFF-Release-APKs ohne DEX. Paket
  `aaps-wear-watchfaces-0.3.1-diy-preview.zip` einschließlich externer
  `.sha256`-Prüfsummendatei erzeugt.
- Baseline-Prüfung: Die öffentliche AndroidAPS-Dev-Branch stand vor und nach
  dem Arbeitspaket auf `e1068e77db4f801c046340c8313cd7a2856f4e7c`.
- Bekannte Einschränkungen: Reconnect-Test, mehrere Uhren, reales
  Nicht-Samsung-Gerät, produktive Signatur und rechtlich blockierte Motive sind
  weiterhin offen.
- Nächster Schritt: Änderungen kontrolliert committen und in das öffentliche
  GitHub-Repository übertragen; danach gezielten Reconnect-Test durchführen.

## 2026-08-09, 21:45 +02:00, Sugarlicious 0.6.2 – Watchfaces, Complication 02 und Graphlayout

- Geändert: Mobile-Watch-Menü, Watchface-Vorschau, Complication-Katalog,
  `GlucoseTrendComplication`, statische Wear-Vorschau, die vier WFF-Module
  Analog/Orbit/Rings/Graph, CGM-/IOB-/COB-Graphen, Notification-Renderer,
  Versionsstände, Installation, Changelog und zugehörige Tests.
- Zweck: die vier realen WFF-Pakete in der Entwicklung eindeutig installierbar
  machen, das Watch-Menü auf einzelne Watchface-Tiles umstellen, Complication 02
  lesbarer machen und Skalen/Flächennutzung des CGM-Graphs korrigieren.
- Umsetzung: vier separate codefreie WFF-Release-APKs mit eindeutigen Paket-IDs
  und Version 0.6.2; ADB-Installationsskript; Glukosewert und Trend als getrennte
  Ranged-Value-Felder; dickere kontrollierte Ringe/Balken; vier Watchface-Tiles
  vor dem Complication Studio; feste logarithmische 40–400-mg/dL-Skala mit
  Innenbeschriftung, kompakter Basalspur und größerer Plotfläche. Wiederholte
  Activity-Aktualisierungen behalten die Compose-Ansicht bei; Notification- und
  Stoffwechselgraph wurden im selben Arbeitspaket vergrößert beziehungsweise an
  die Nightscout-Toolkit-Darstellung angenähert.
- Tests: 25 ausgewählte Unit-/Robolectric-Tests bestanden; Mobile- und Wear-
  Debug-APKs gebaut; Mobile-Lint ohne Fehler; alle vier WFFs mit dem offiziellen
  Google-Validator als WFF v1 gültig; vier signierte Release-APKs ohne DEX und
  mit den erwarteten Paket-IDs verifiziert.
- Ergebnis: automatisierte Prüfungen grün. `adb devices -l` lieferte zum
  Abschluss kein verbundenes Gerät; Installation, Darstellung in Galaxy
  Wearable und visuelle Kontrolle auf der realen Watch wurden daher in diesem
  Arbeitspaket nicht behauptet.
- Nächster Schritt: Watch per WLAN-/USB-Debugging verbinden, vier WFF-Release-
  APKs installieren, Galaxy-Wearable-Liste aktualisieren und aktive/AOD-
  Screenshots von Complication 02 sowie allen vier Watchfaces aufnehmen.
