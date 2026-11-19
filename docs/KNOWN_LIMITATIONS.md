# Known Limitations

Stand: 2026-09-20. Diese Datei beschreibt ausschließlich den aktuellen Stand;
historische Prüfergebnisse stehen in `TEST_REPORT.md`.

## Plattform und Veröffentlichung

- Der Foreground-Service erhöht mit einer sichtbaren Benachrichtigung die
  Hintergrundpriorität und fordert nach Neustart oder App-Update einen
  Wiederanlauf an. Android darf den Prozess bei Systemdruck trotzdem beenden;
  nach einem erzwungenen App-Stopp ist kein automatischer Neustart erlaubt.
- Darstellung und Hervorhebung der Android-16-Live-Benachrichtigung liegen
  teilweise beim System beziehungsweise OEM. Die Logik ist automatisiert
  geprüft, die aktuelle One-UI-Darstellung aber noch nicht als visueller
  Realgerätetest dokumentiert.
- Der Foreground-Service-Typ `specialUse` benötigt für eine Veröffentlichung
  eine passende Play-Console-Deklaration. Das lokale DIY-/ADB-Paket ist davon
  nicht blockiert.
- Die Release-Artefakte besitzen noch keine Produktionssignatur. Store-Rollout,
  Rollback-Probe und Crash-/ANR-Telemetrie benötigen externe Konten,
  Zugangsdaten und eine Datenschutzentscheidung.
- PinkFloydTheWall wird wegen ungeklärter Rechte an Drittmotiven und Marke nicht
  gebaut oder verteilt.

## Datenquellen

- Der öffentliche AAPS-Broadcast liefert Zielgrenzen, aber keinen verlässlich
  gekennzeichneten temporären Zielzustand und keinen vollständigen
  offen/geschlossen/pausiert-Loopmodus. Suggested/Enacted wird nur angezeigt.
- Der AAPS-Broadcast besitzt keine kryptografische Absenderauthentisierung. Die
  App prüft Paketinstallation, Pflichtfelder, Wertebereiche und Zeitstempel,
  kann einen lokal absichtlich gefälschten Broadcast aber nicht sicher
  unterscheiden.
- Stock-AAPS liefert über den verwendeten öffentlichen Broadcast keinen
  vollständigen historischen Graphen. Fehlende Werte werden weder erfunden
  noch interpoliert.
- Der öffentliche AAPS-Vertrag liefert keine vollständige Insulinaktivitätskurve.
  Die dargestellte Aktivität ist eine gekennzeichnete Display-Schätzung aus
  vorhandenen IOB-Punkten und darf nicht für Therapieentscheidungen verwendet
  werden.
- Historische SMB-Marker entstehen aus empfangenen Enacted-Daten. Nach einer
  Neuinstallation werden zuvor fehlende Marker nicht rekonstruiert.
- AAPS-`predBGs` werden nur bei vorhandenem und gültigem Payload dargestellt;
  Sugarlicious berechnet keine Ersatzprognosen.
- Der vollständige, geräteübergreifende Abgleich lokal auf der Standalone-Uhr
  gespeicherter G7-Historie mit Mobile/AAPS ist noch ein Hardware- und
  Integrationstestpunkt.

## Wear OS und Watchfaces

- Der Telefon-Uhr-Data-Layer wurde auf Samsung-Hardware geprüft. Gezielte
  Bluetooth-Unterbrechung, anschließende Wiederverbindung, Sensorwechsel
  zwischen Galaxy Watch und Pixel Watch sowie zwei konkurrierende Uhren sind
  noch nicht durch einen aktuellen Abschlusslauf belegt.
- Für die aktuelle Version fehlen dokumentierte aktive/AOD-Goldens auf Galaxy
  Watch Ultra und Pixel Watch sowie ein identischer Original-vs.-Port-Vergleich.
- WFF unterstützt höchstens acht Complication-Slots. CWF-`dynPref`, `dynData`,
  Twin-View und einige analoge oder animierte Funktionen sind deshalb nur
  bestmöglich abgebildet; Details stehen in der Paritätsmatrix.
- Die Provider-App muss vor separaten WFF-Paketen installiert werden. Bei
  umgekehrter Reihenfolge können bestehende Favoriten `NoDataSource` behalten
  und müssen erneut angelegt werden.
- Die zwei aktiven Produkt-WFFs Digital und Vigil sowie das technische
  Testface werden separat gebaut, offiziell validiert und auf DEX-Freiheit
  geprüft. Ausgemusterte Watchfaces sind keine aktiven Build- oder
  Release-Abhängigkeiten mehr. Diese automatischen Gates ersetzen keine
  optische Abnahme auf realer Hardware.
- `galaxy_watch_ultra_mockup_exact.svg` enthält ein eingebettetes Rasterbild.
  Sehr starke Vergrößerung erzeugt deshalb keine zusätzliche Vektorauflösung;
  Herkunft und Freigabe sind in `LICENSES/USER_SUPPLIED_ASSETS.md` dokumentiert.
