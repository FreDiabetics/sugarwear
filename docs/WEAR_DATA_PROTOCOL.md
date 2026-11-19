# Wear Data Protocol

- DataItem-Pfad: `/aaps-display/v1/state`
- Request-Pfad: `/aaps-display/v1/request`
- Capability: `aaps_display`
- Kodierung: UTF-8 JSON
- aktuelles Envelope-Protokoll: 6
- aktuelles `TherapyDisplayState`-Schema: 5
- aktuelle Watch-Konfiguration: 2

Der Pfad bleibt für kompatible Updates stabil; die Version steht im Envelope.
Unbekannte JSON-Felder werden ignoriert. Protokollversionen größer als 6 werden
abgewiesen. Beim Lesen von Protokoll/Schema 1 wird eine damalige
`AAPS_*`-Vertragskennung aus `sourceVersion` nach `sourceContract` migriert;
eine echte AAPS-App-Version bleibt separat.

Schema 3 ergänzt ausschließlich Anzeigeinformationen:

- `glucoseHistory`: lokal empfangene Glukosepunkte,
- `glucosePredictions`: echte AAPS-`predBGs`, getrennt nach IOB, COB, aCOB,
  UAM und Zero-Temp,
- `therapyHistory`: lokal empfangene IOB-/COB-/Basal-Anzeigepunkte.

Die Verlaufslisten sind optional und besitzen leere Standardwerte. Dadurch
bleiben Zustände aus Schema 1/2 lesbar. Die Mobile-App hält höchstens 24 Stunden
und 300 Messzeitpunkte; es entsteht keine separate Therapiedatenbank. Der
Empfänger erzeugt weder Prognosen noch fehlende Zwischenwerte.

Schema 4 ergänzt die Herkunft einzelner Glukosepunkte, getrennte Basis-/TBR-Raten und eine optionale, rein visuelle
Insulinaktivitäts-Schätzung. Protokoll 5 transportiert diese Felder sowie die
Watch-Konfiguration 2. Letztere enthält getrennte Graphrollen für Hintergrund,
Zielbereiche, CGM-Punkte, Trennlinie und Punktkontur. Alte Konfigurationen
erhalten sichere Standardfarben.

Schema 5 ergänzt optionale, ausschließlich lesende `smbUnits`- und SMB-
Zeitfelder im Loop-/Verlaufsmodell sowie die Capability `SMB`. Protokoll 6
transportiert sie. Die Daten stammen nur aus einem gültigen öffentlichen AAPS-
Enacted-Payload; ältere Zustände bleiben ohne Marker vollständig lesbar.

`DataClient` hält genau den aktuellen vollständigen Zustand. `MessageClient`
wird nur für eine explizite Wiederanfrage verwendet. `CapabilityClient` und
`NodeClient` erkennen erreichbare Gegenstellen. Die Uhr speichert den letzten
validen Zustand in DataStore und fordert ihn beim Neustart erneut an.

Frische wird zentral aus `glucose.measuredAtEpochMs` bestimmt: aktuell bis
6 Minuten, verzögert bis 12 Minuten, danach veraltet. Mehr als 5 Minuten in der
Zukunft liegende Messungen werden als keine Daten behandelt.
