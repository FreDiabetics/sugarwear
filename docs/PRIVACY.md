# Datenschutz und Sicherheit

- ausschließlich lokaler AndroidAPS-Broadcast und lokaler Wear Data Layer
- keine Internet-Datenquelle, Cloud, Telemetrie, Werbung oder Analytics
- keine Therapie-, Pumpen- oder Loopbefehle im Modell oder Protokoll
- gespeichert werden der letzte normalisierte Zustand sowie begrenzte lokale
  Anzeigeverläufe; der eigenständige G7-Collector besitzt zusätzlich eine lokale
  Messwertdatenbank
- Backups und Klartext-Netzwerkverkehr sind für beide Apps deaktiviert
- die Diagnose-Datenbank ist auf 1000 Ereignisse und sieben Tage begrenzt. Sie enthält
  Ablaufstatus, stabile Fehlercodes, Zeitpunkte und Zählwerte von Smartphone und Watch,
  aber keine vollständigen Gesundheits-Payloads, Rohpakete oder Authentifizierungsschlüssel
- der G7-Sensorcode wird auf Wunsch in der lokalen Sensor-Dokumentation angezeigt,
  liegt verschlüsselt im Android Keystore und wird nicht in exportierbare Ereignislogs übernommen
- Health Connect wird nur nach einzeln erteilten Android-Berechtigungen verwendet. Sugarlicious
  schreibt ausschließlich eigene CGM-Messungen als Blutzucker und liest freigegebene Gesundheitsdaten
  in einen lokalen 24-Stunden-Snapshot. Importierte Health-Connect-Daten werden nicht zurückgeschrieben
- der Debug-Testdatenempfänger wird nur in Debug-Builds kompiliert; er enthält
  ausschließlich synthetische Daten und existiert nicht in Release-Varianten

Die Anwendung ist eine Anzeigehilfe und kein Medizinprodukt für
Therapieentscheidungen. Bei widersprüchlichen oder veralteten Angaben ist die
primäre AndroidAPS-/CGM-Anzeige maßgeblich.
