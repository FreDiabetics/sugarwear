# Native G7-Kalibrierung

Stand SugarWear 8190e619. AndroidSensorBle.kt:1065 schreibt über Android GATT. SensorProtocol.kt:65 speichert einen Zustand aus dem Glukosepaket. Beide Ereignisse allein erlauben keine Aussage, ob der Sensor einen konkreten Fingerstick verwendet hat.

## xDrip+ als technische Referenz

[Quellstand cb3a3a8](https://github.com/NightscoutFoundation/xDrip/tree/cb3a3a8be3a2ad8af9d8723227893bbc7f2a2b55), App-Modul, Package com.eveningoutpost.dexdrip. Das Repository enthält die GPL Version 3. Kein Quellcode wurde übernommen.

- calibrations.NativeCalibrationPipe.addCalibration prüft Messwert und Messalter und verteilt an mehrere Collector-Pfade.
- g5model.Ob1G5StateMachine.addCalibration (Zeile 1461) prüft acceptCommands(), rechnet den Messzeitpunkt mit DexTimeKeeper um und reiht CalibrateTxMessage ein. queued schreibt an Control (Zeile 1510). Teile der Authentifizierung erfolgen über ein Plugin.
- CalibrateTxMessage (Zeile 16) kodiert Glukose und Sensorzeit. CalibrateRxMessage (Zeile 18) prüft Länge, Opcode und CRC. accepted() fasst OK, zweite Kalibrierung erforderlich und Duplikat zusammen. Ob1G5StateMachine (Zeile 904) verarbeitet das Ergebnis. Ein Duplikat belegt keine neue Kalibrierung.
- pendingCalibration und deleteFirstQueueCalibration verwalten die Warteschlange. Daraus ergibt sich kein Nachweis einer an die SugarWear-Session gebundenen Antwort nach Reconnect.

## Eingabe und Freigabe

Das [G7-Handbuch](https://s3.us-west-2.amazonaws.com/dexcompdf/G7/AW-00046-06_REV005_UG_G7_OUS_en-SA_MGDL.pdf) unterscheidet Protokollieren und Kalibrieren. Für die Kalibrierung nennt es 40–400 mg/dl innerhalb von fünf Minuten nach dem Fingerstick. NativeCalibrationPipe erlaubt bis zu einer Stunde. Für die Bedienung gilt das G7-Zeitfenster.

Die [G7-Verbindungsanleitung](https://navid200.github.io/xDrip/docs/Dexcom/G7.html) erklärt Pairing und Empfang. Die [Beschreibung des nativen Algorithmus](https://navid200.github.io/xDrip/docs/Native-Algorithm) erklärt die Berechnung im Sensor. Offen ist der durchgängige Nachweis von Schreibfähigkeit, Zeitbasis und zuordenbarer Antwort auf der jeweiligen G7-Firmware in SugarWear.

Hardwareprüfung ausstehend: Annahme, Ablehnung, Unterbrechung nach dem Schreiben, Neustart, verspätete Antwort und Sensorwechsel. Ein möglicherweise zugestellter Wert darf nach Wiederverbindung nicht erneut automatisch gesendet werden. Unklare Ergebnisse müssen als solche erhalten bleiben.
