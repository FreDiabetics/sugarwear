# AAPS Wear Architecture Audit

Baseline: `18101c8a2c0204a08d417f3d5fbac3e9ceae380f`.

## Architektur

AAPS enthält eine ausführbare Wear-App. `DataLayerListenerServiceWear` nutzt
primär `MessageClient` für serialisierte interne `EventData` in beide
Richtungen und `CapabilityClient` zur Knotenauswahl. `DataHandlerWear` verteilt
Zustände an klassische Watchfaces, Tiles und Complications. Die Wear-App enthält
auch Therapieaktionen. Dieses Projekt übernimmt weder diese privaten Modelle
noch irgendeinen Befehls-/Therapierückkanal.

## Watchfaces und Speicherung

- klassisch ausführbar: `CircleWatchface`, `DigitalStyleWatchface`,
  `CustomWatchface`
- Custom V1/V2: ZIP/JSON, Bilder/SVG, `dynData`, `dynPref`, Twin-View und AOD
- WFF: `wear/watchfacepush` ist ein getrenntes, codefreies AAPS-V4-Ressourcen-
  APK mit fünf Complication-Slots
- Flavours: AAPS/full, Pumpcontrol, AAPSClient und weitere Client-IDs; die
  Default-Provider-ID wird beim Build eingesetzt

AAPS nutzt eigene Stores und einen `ComplicationStore`. Das unabhängige Projekt
verwendet eine eigene versionierte DataStore-Ablage und transportiert niemals
private AAPS-Klassen auf die Uhr.

## Watch Face Push

`WatchFacePushHelper` kapselt Installation und Aktivierung. Seit dem Delta
`593e78f..18101c8` wird das eingebettete AAPS-V4-Paket einmal pro App-Version
automatisch installiert, aber nicht aktiviert. Die eigenständige Suite liefert
stattdessen 23 separat installierbare WFF-APKs mit eindeutigen Application-IDs.
Auf Wear OS muss die Provider-App zuerst installiert sein, damit neue
Watchface-Favoriten ihre Default-Provider übernehmen.

## WFF-Grenzen

WFF kann keine AAPS-Kotlin-Logik ausführen und unterstützt höchstens acht Slots.
Dynamische Diagramme werden als Bild-Complications geliefert. `dynData`,
`dynPref`, Twin-View und komplexe analoge Logik erfordern deklarative oder
bildbasierte Ersatzlösungen. Bei fremden Watchfaces kontrolliert das fremde
Layout die Darstellung semantischer Complications.

## Delta-Audit 2026-08-02

Sieben neue Merge-/Feature-Commits änderten interne `EventData`-/
`LoopStatusData`-Modelle, LongStatus/SGV-Formatierung, COB-Begrenzung,
Client-Predictions und Watchface-Push. `EventData` toleriert jetzt unbekannte
JSON-Felder; `LoopStatusData` besitzt `modeEndTime`. Diese Felder stehen im
öffentlichen External-Companion-/Tizen-Broadcast nicht zur Verfügung.
`TizenPlugin.kt` war im Delta unverändert, daher musste der eigene öffentliche
Datenvertrag nicht geändert werden.
