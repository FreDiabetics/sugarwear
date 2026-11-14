# AAPS Data Contract Audit

## Beste lokale Schnittstelle

Für eine unabhängige App ist der bewusst veröffentlichte Broadcast
`info.nightscout.androidaps.status` die beste lokale read-only Schnittstelle.
Die umfangreichere interne AAPS-Wear-`EventData`-Kommunikation ist keine stabile
öffentliche API. Der Sender ist `plugins/sync/.../tizen/TizenPlugin.kt`; in der
AAPS-Oberfläche heißt das Plugin **External Companion Apps**.

Der Sender fragt manifestierte Empfänger ab, adressiert jedes Zielpaket explizit
und verwendet `FLAG_INCLUDE_STOPPED_PACKAGES`. Belegt sind `glucoseMgdl`,
`glucoseTimeStamp`, `units`, `slopeArrow`, `deltaMgdl`, `avgDeltaMgdl`, `high`,
`low`, `bolusIob`, `basalIob`, `iob`, `cob`, `futureCarbs`, `phoneBattery`,
`rigBattery`, Suggested/Enacted samt Zeit, `baseBasal`, `profile`, Temp-Basal-
Felder sowie optionale Pumpenbatterie, Reservoir und Pumpenstatus.

Der Adapter akzeptiert kompatible Zahlentypen, ignoriert unbekannte Felder,
verwirft Glukose außerhalb 20–1000 mg/dl, fehlende/ungültige Zeitstempel und
mehr als fünf Minuten zukünftige Messungen. Fehlende Felder werden `null` und
erzeugen keine Capability.

## Capability-Matrix

| Feldgruppe | Broadcast | Modell |
|---|---:|---:|
| Glukose, Einheit, Trend, Messzeit | ja | vollständig normalisiert |
| Delta, AvgDelta, Zielgrenzen | ja | optional |
| IOB, Bolus-/Basal-IOB, COB, zukünftige KH | ja | optional |
| Basal und temporäre Basalrate | ja | absolut/Prozent/Dauer |
| Suggested/Enacted | ja | Zeit und Rohpayload, kein Rückkanal |
| `predBGs` in Suggested/Enacted | optional | IOB/COB/aCOB/UAM/ZT als echte Prognoseserien |
| Profil, Pumpenstatus, Reservoir, Batterien | ja | optionale Capabilities |
| Temporäres Ziel als sicherer eigener Zustand | nein | nicht behauptet |
| kompletter Loopmodus/Modus-Endzeit | nein | nicht behauptet |

Der Broadcast enthält keine App-Version. Die Mobile-App deklariert per
`<queries>` die fünf aktuellen AAPS-/Pumpcontrol-/Client-Pakete und ermittelt
daraus VersionName/VersionCode. `sourceContract` enthält getrennt
`AAPS_EXTENDED_STATUS_V1` oder `AAPS_LEGACY_STATUS`. Schema 1 wird beim Empfang
auf Schema 4 migriert. Listenfelder aus neueren Schemata besitzen leere
Standardwerte und fehlen bei älteren Zuständen ohne Fehler.

## Anzeigeverlauf und Prognosen

Der unveränderte öffentliche AAPS-Broadcast liefert keinen fertigen historischen
Graphen. Sugarlicious bleibt deshalb vollständig mit Stock-AAPS kompatibel und
sammelt nacheinander real empfangene Anzeigewerte lokal: maximal 24 Stunden und
300 Zeitpunkte, dedupliziert innerhalb eines 90-Sekunden-Fensters. IOB, COB und Basal werden nur dann
als Verlaufspunkt gespeichert, wenn der Broadcast den jeweiligen Wert enthält.

Sugarlicious erweitert oder patcht AndroidAPS ausdrücklich nicht und besitzt
keinen Nightscout-Backfill. Erfunden oder interpoliert wird kein CGM-Wert.

Prognosen werden nicht lokal berechnet. Der Adapter liest optional das
`predBGs`-Objekt aus dem vorhandenen Suggested-, ersatzweise Enacted-JSON,
akzeptiert nur endliche Werte zwischen 20 und 1000 mg/dl, begrenzt jede Serie
auf 96 Punkte und verwendet den AAPS-Zeitabstand von fünf Minuten. Fehlt das
Objekt oder ist es ungültig, bleibt `PREDICTIONS` aus der Capability-Menge weg.

Der letzte gültige Prognosesatz bleibt für kurze Quell- oder Verbindungsunterbrechungen
lokal erhalten und läuft nach spätestens drei Stunden ohne gültigen Punkt aus. Für die
reine Darstellung werden die unveränderten Zeitabstände so an die aktuelle Zeit
verschoben, dass der erste Prognosepunkt direkt rechts der Jetzt-Trennlinie liegt. Die
gespeicherten AAPS-Quelldaten selbst werden dabei nicht umgeschrieben.
