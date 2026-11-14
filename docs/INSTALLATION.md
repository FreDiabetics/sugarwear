# Installation und Nutzung

## Was die Anwendung kann

Sie zeigt lokal von AndroidAPS gesendete Glukose-, Trend-, Delta-, IOB-, COB-,
Basal-, Profil-, Pumpen- und Batteriedaten auf Wear OS an. Sie kann keine
Therapie auslösen oder ändern. Die 35 Provider lassen sich auch in fremden
Watchfaces verwenden; deren Layout wird dann vom jeweiligen Watchface bestimmt.

## Voraussetzungen

- AndroidAPS mit aktivem Plugin **External Companion Apps** (historisch
  `TizenPlugin`)
- Android-Telefon mit Google Play Services for Wear OS
- gekoppelte Wear-OS-Uhr mit Wear OS 4 oder neuer; Zieltest ist Wear OS 6
- Installation aus unbekannter Quelle/ADB für das DIY-Vorschaupaket

## Reihenfolge

1. `apps/sugarlicious-mobile-debug.apk` auf dem Telefon installieren.
2. `apps/sugarlicious-wear-debug.apk` auf der gekoppelten Uhr installieren.
3. AndroidAPS öffnen und unter Konfiguration **External Companion Apps**
   aktivieren.
4. AndroidAPS einen aktuellen Status erzeugen lassen und die Mobile Bridge
   öffnen. Dort müssen AAPS-Version, Empfangszeit und Datenvertrag erscheinen.
5. Erst danach ein oder mehrere APKs aus `watchfaces/` auf der Uhr installieren.
6. Auf der Uhr das gewünschte Watchface auswählen. Falls Slots leer bleiben,
   das betreffende Watchface-Paket entfernen und nach der Wear-App erneut
   installieren oder die AAPS-Complications einmal manuell zuweisen.

Wichtig: Die sechs Sugarlicious-Watchfaces sind eigenständige WFF-Apps. Die
Installation von `app-wear-debug.apk` installiert sie nicht automatisch. Für
einen vollständigen lokalen Build mit Tests und Installation aller Apps und
Watchfaces genügt bei verbundenem Telefon und verbundener Watch:

```powershell
.\dev.ps1 all -Test
```

Das Skript erkennt Telefon und Watch anhand ihrer Android-Geräteart; USB- und
Wireless-Debugging-Seriennummern funktionieren gleichermaßen. Bei mehreren
Telefonen oder Watches kann die Auswahl mit `-PhoneSerial` beziehungsweise
`-WatchSerial` eindeutig vorgegeben werden.

Anschließend erscheinen `Sugarlicious Digital`, `Sugarlicious Analog`,
`Sugarlicious Orbit`, `Sugarlicious Rings`, `Sugarlicious Graph` und
`Vigil` in der Watchface-Auswahl. Das SugarWear-Watchface wird in
der Sugarlicious-Auswahl freigegeben, sobald die Dexcom-G7-Watch-Datenquelle
aktiviert oder als aktive Quelle erkannt ist. Falls Galaxy Wearable die Liste
noch zwischengespeichert hat, die Auswahl auf der Uhr durch langes Drücken des
Zifferblatts öffnen oder Galaxy Wearable neu starten.

Beim ersten Öffnen fragt Sugarlicious ab Android 13 nach der Erlaubnis für
Benachrichtigungen. Sie sollte zugelassen werden, damit die normale laufende
Hintergrund-Benachrichtigung sichtbar bleibt. Sie zeigt Glukosewert, Trend,
Datenalter und einen kleinen abgerundeten Verlauf; bei alten/fehlenden Daten
erscheint kein Wert als aktuell.

Unter **Einstellungen → Anzeige → Live-Benachrichtigung (One UI 8.5)** kann
auf Android 16 ein Live-Status angefordert werden. Wenn das System eine weitere
Freigabe verlangt, öffnet Sugarlicious die offizielle App-Einstellung dafür.
Der Live-Status verwendet dieselben aktuellen Anzeigedaten. Auf älteren oder
nicht freigeschalteten Systemen bleibt automatisch die normale
Benachrichtigung aktiv; die genaue Hervorhebung entscheidet One UI.

## Erwartete Anzeige

- bis 6 Minuten Messalter: `aktuell`
- über 6 bis 12 Minuten: `verzögert`
- älter als 12 Minuten: `veraltet`, Therapiewerte werden als `—` verborgen
- kein oder unplausibel zukünftiger Messzeitpunkt: `keine Daten`

Die Messzeit, nicht die Empfangszeit, entscheidet über die Frische. Ein alter
Wert wird nie unmarkiert als aktuell weitergeführt.

## Ohne eigenes Watchface

In der Watchface-Konfiguration können die Provider `1 Glucose compact` bis
`27 Full AAPS status` einzeln ausgewählt werden. Text, Titel und Ranged Value
sind semantisch; Farbe, Schrift und Anordnung bestimmt das fremde Watchface.
Für kontrollierte Optik stehen die Bild-/Graph-Provider und die mitgelieferten
WFF-Pakete bereit.

Die Originalpakete heißen Sugarlicious Digital, Analog, Orbit, Rings, Graph und
SugarWear. Die vier analogen Varianten besitzen kräftige eigene Baton-Zeiger,
AOD sowie Graph- und kreisförmig nutzbare `RANGED_VALUE`-Slots. Die Zeiger sind
eine Eigenentwicklung und keine kopierten Apple-Assets.

## DIY-Hinweis

Die bereitgestellten App-APKs sind Debug-/Entwicklerbuilds. Vor einer dauerhaften
Weitergabe sollte das Repository mit einem eigenen privaten Release-Key gebaut
werden. Niemals fremde Signierschlüssel in das Repository einchecken.
