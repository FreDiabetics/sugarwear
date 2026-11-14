# Release Checklist

## Erfüllt

- [x] aktuelle AndroidAPS-Dev-Baseline und Delta dokumentiert
- [x] read-only Broadcastadapter ohne private Datenbank/API
- [x] versioniertes Modell, Capability-Erkennung und Payloadvalidierung
- [x] DataClient/MessageClient/CapabilityClient und Watch-DataStore
- [x] 35 Complication-Provider mit jeweils genau einem Ausgabetyp
- [x] Digital und Vigil plus technisches Testface: 3/3 codefrei und offiziell validiert
- [x] AOD-Emulator-Goldens für den bis 0.5.1 veröffentlichten Stand
- [x] 19 offizielle Community-Quellen inventarisiert und zugeordnet
- [x] AGPL-/MIT-Nachweise, Datenschutz, Installationsanleitung, CI
- [x] reproduzierbares DIY-ZIP mit SHA-256-Manifest
- [x] kein Internet-Permission, keine Cloud/Telemetrie/Therapiekommandos
- [x] realer Telefon-Uhr-Data-Layer-Einzeltest
- [x] Galaxy Watch / One UI Watch 8
- [x] öffentliches GitHub-Repository mit Android-Studio-Importanleitung

## Vor Version 1 zwingend offen

- [ ] Bluetooth-Unterbrechung/Reconnect und mehrere gekoppelte Uhren
- [ ] mindestens ein reales Nicht-Samsung-Wear-OS-Gerät
- [ ] Sensorwechsel Galaxy Watch ↔ Pixel Watch mit erhaltener Historie
- [ ] LIVE-/Backfill-SLA auf realer Hardware einschließlich Prozessneustart
- [ ] Original-vs.-Port-Goldens mit kontrollierter identischer Testmatrix
- [ ] neuer aktiver/AOD-Bildlauf aller aktuellen Sugarlicious-Watchfaces
- [ ] eigener Produktionssignierschlüssel und reproduzierbarer signierter Build
- [ ] Crash-/ANR-Telemetrie, gestaffelter Rollout und Rollback-Probe
- [ ] Play-Console-Deklaration für den Foreground-Service-Typ `specialUse`
- [ ] Community-Review

## Bewusst ausgeschlossen

- [x] PinkFloydTheWall nicht veröffentlicht, solange Drittrechte ungeklärt sind
- [x] keinerlei Therapie-, Pumpen- oder Loopsteuerung
