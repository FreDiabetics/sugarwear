# SugarWear – Alarmpräsentation auf Wear OS (20.11.2026)

## Geltende Auslieferungswege

1. **Hintergrund:** Die bestehende zentrale Alarm-Engine erzeugt die lokale Benachrichtigung mit hoher Kanalwichtigkeit (IMPORTANCE_HIGH), Kategorie ALARM, originalem auslösungsgebundenem Snapshot und der bereits vorhandenen Sound-/Vibrationslogik. Tippen öffnet unverändert die Illustrator-basierte `CgmAlarmActivity`.
2. **App sichtbar:** Der Application-Lifecycle-Beobachter startet bei einer **neu aktivierten, bisher nicht gezeigten** Episode die bestehende `CgmAlarmActivity`. Beim bloßen Wiederöffnen der App und bei späteren Sensorwerten wird kein bereits aktiver Alarm erneut gestartet. Mehrere gleichzeitige neue Episoden priorisieren die höchste; andere bleiben im Notification-Center.
3. **Bis zur Quittierung:** Die zu jeder aktiven Episode gehörende, bestehende laufende Notification erhält Wear OS `OngoingActivity`-Metadaten und ein antippbares Symbol. Nach ACK, Snooze, Auflösung, Sensortrennung oder Deaktivierung entfernt der bisherige Coordinator die Notification und damit auch den Hinweis.

Es gibt weiterhin **eine** Alarmzustandsmaschine/Ownership. Kein Hintergrundstart einer Activity, keine BLE-/Resolver-Änderung, keine neuen WakeLocks und keine zusätzlichen Poller. Die Trigger-Glukose und der Trigger-Trend bleiben über die vorhandene Session-/Episodenpersistenz unverändert.

## Plattformgrenzen

`USE_FULL_SCREEN_INTENT` und `setFullScreenIntent()` sind **auf Wear OS nicht unterstützt** und werden entfernt. Hohe Kanalwichtigkeit, Ongoing Activity, Sound und Vibration erzwingen weder ein Heads-up-Fenster noch die Bildschirmaktivierung bei DND, fehlender Berechtigung oder OEM-Beschränkungen. Ongoing Activity ist eine Rückkehrmöglichkeit, keine Garantie, dass ein Alarm wahrgenommen wurde.

Offizielle Dokumentation:
- https://developer.android.com/training/wearables/notifications
- https://developer.android.com/training/wearables/notifications/ongoing-activity

## Qualitäts-Gate

Automatische Regressionstests: Nur neue Episode öffnet im Vordergrund; Wiederholung, Refresh und App-Wiederaufnahme nicht; priorisierte gleichzeitige Ereignisse; Notification bleibt mit unverändertem ContentIntent/Actions/Kategorie und ohne unsupported FSI. Zusätzlich vor Freigabe auf Galaxy Watch und Pixel Watch prüfen: App-Übersicht und Settings sichtbar, Hintergrund/Watchface, AOD, DND, gesperrter Bildschirm, Notification-Tap, Ongoing-Chip, Quittierung, Snooze, Sessionwechsel, Reconnect, Prozessneustart und deaktivierte Benachrichtigungen.

Wichtig: Quellcodeprüfung/CI sind kein Beweis einer automatisch sichtbaren System-Benachrichtigung oder medizinischer Alarmsicherheit.
