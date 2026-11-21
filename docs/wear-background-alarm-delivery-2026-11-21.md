# SugarWear – Hintergrund-Alarmzustellung (21.11.2026)

## Zuständigkeiten
- Der bereits vorhandene Wear-OS-`CgmCollectorService` ist ein `connectedDevice`-Foreground-Service, unabhängig von `CgmWatchActivity`. `START_STICKY`, `CgmBootReceiver`, `CgmReconnectReceiver`, `CgmSignalLossReceiver` und der Window-Watchdog bilden die bestehende Wiederanlaufkette; keine zweite Service- oder Alarmzustandsmaschine.
- Die einzige Alarmquelle bleibt `CgmAlarmCoordinator`. Die Zustellung nutzt im Hintergrund Wear-OS-Systembenachrichtigungen, vorhandene Ton-/Vibrationslogik und `OngoingActivity`. Die eigene Illustrator-Activity wird nur bei bereits sichtbarer SugarWear-Oberfläche gestartet oder durch Tippen geöffnet.
- Das Notification-Gate prüft `POST_NOTIFICATIONS`, aktivierte App-Benachrichtigungen und Kanalwichtigkeit. Eine deaktivierte Benachrichtigung wird nicht mit erfolgreicher Anzeige gleichgesetzt. Abgeschwächte/deaktivierte Kanäle werden nicht gegen die Nutzerentscheidung reaktiviert.
- Der Bereich **Systemstatus → Zeitplanung und Rechte** zeigt die ermittelte Alarmzustellbarkeit. Nach Rückkehr aus der Berechtigungsverwaltung repostet `CgmPermissionsActivity` noch aktive Episoden ohne neue Ton-/Vibrationsauslösung oder erneuten Fullscreen-Aufruf.
- Ausfälle bei Kanalvorbereitung, Ton/Vibration, OngoingActivity-Metadaten oder NotificationManager-Aufruf werden protokolliert und unterbrechen nicht die Persistenz des Alarm-Snapshots. Der Notifier wird nicht zum Datenresolver.
- Ein vom System abgelehnter Foreground-Service-Start beim Boot wird protokolliert. Der bereits zuvor scharf geschaltete Reconnect-Alarm bleibt erhalten.

## Test- und Sicherheitsgrenzen
- Unit-Tests: fehlende Notification-Berechtigung, deaktivierte App-Mitteilungen, Kanal `NONE`/`DEFAULT`/`HIGH`, Alarmepisodenpersistenz trotz blockierter System-Mitteilungen und spätere Wiederherstellung. CI und Geräte-Tests bleiben erforderlich.
- Watch-Prüfung: App sichtbar, Watchface sichtbar, Bildschirm aus, App-Prozess beendet, Boot, BT-Reconnect, DND, Berechtigungen zurückgeben, Kanal deaktiviert, ACK/Snooze, Prioritätskonflikte, AOD, Ongoing-Icon und Alarm-Audio.
- Keine Zusage, dass Wear OS den Bildschirm automatisch aktiviert oder einen Alarm immer wahrnehmbar präsentiert. `USE_FULL_SCREEN_INTENT` wird auf Wear OS nicht unterstützt.
- Android-Dokumentation: https://developer.android.com/training/wearables/notifications
- Notification-Berechtigung: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Hintergrund-FGS-Einschränkungen: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Die bisher noch nicht vollständig eingebundenen Original-MP4-Alarmtöne und echte Geräteprüfungen sind separate Freigabeblocker. Bei Glukosewarnungen eine unabhängige zugelassene Alarmquelle beibehalten.
