# SugarWear: Wear-OS-Alarmanzeige und Original-Töne (20.11.2026)

## Isolation
Alarm-Fix auf Basis von `main`, unabhängig vom experimentellen Dexcom-Share-PR #19. Bestehende G7-Erfassung, Resolver, In-App-Alarmleiste, Glukosekachel, Quittierung und Alarm-Artdesign bleiben unangetastet.

## Befund und Behebung
- In `main` fehlt seit 18.11.2026 der vormals vorhandene Full-Screen-Intent am Alarm-PendingIntent. `CgmAlarmNotifier.show` setzt ihn wieder **nur beim erstmaligen Auslösen**. Aktualisierung oder Wiederherstellung starten den Bildschirm nicht erneut; der Benachrichtigungstap bleibt immer verfügbar.
- Google dokumentiert `setFullScreenIntent()` / `USE_FULL_SCREEN_INTENT` als **nicht auf Wear OS unterstützt**: https://developer.android.com/training/wearables/notifications . Die Wiederherstellung ist darum **Best-Effort**, nicht systemseitig garantierte automatische Vollbildanzeige. Normale hoch priorisierte lokale Alarmbenachrichtigungen bleiben der unterstützte Fallback. Keine Hintergrundstart- oder DND-Umgehungen.
- Hoch/Tief, dringend Tief und dringend Tief bald: Auslösewert **und Trend zum Auslösezeitpunkt** werden im Alarm-Snapshot (Sensor-/Session-bezogen, über Neustart) gespeichert; die Vollbildansicht nutzt diese statt neuer CGM-Werte. Andere Anzeigen bleiben live.
- Bestehende Grenzen durch Benachrichtigungsberechtigung, benutzergesteuerte Kanäle, DND, Watch-System und Alarm-Snooze bleiben wirksam.
- Der bisherige Collector-Fehlerkanal `direct_watch_collector_errors_v5` spielte zusätzlich zum zentralen technischen Alarm einen Ton/Vibration. Ein neuer stummer v6-Kanal verhindert Doppelalarmierung, ohne die Meldungsanzeige zu entfernen.

## Original MP4-Alarmtöne
Folgende unveränderte Benutzerdateien wurden geprüft (MP4/AAC, 48 kHz, Stereo), jeweils unter `app-sugarwear/src/main/assets/alarms/`:
- Dringend Tief: `alerts_sounds_medium_urgent_low.mp4`
- Dringend Tief bald: `alerts_sounds_medium_urgent_low_soon.mp4`
- Tief: `alerts_sounds_medium_low.mp4`
- Hoch: `alerts_sounds_medium_high.mp4`
- Schnell steigend: `alerts_sounds_medium_rising_rate.mp4`
- Schnell fallend: `alerts_sounds_medium_falling_rate.mp4`
- Signalverlust: `alerts_sounds_medium_signal_loss.mp4`
- Temporäres Sensorproblem: `alerts_sounds_medium_bsi.mp4`
- Technisches Problem: `alerts_sounds_medium_technical_alerts.mp4`
- System Alert: `alerts_sounds_medium_system_alert.mp4` (bewusst keine neue separate Alarmquelle erfunden; Datei für späteren fachlichen Owner reserviert).

**Binärdateien sind über den aktuell verfügbaren textbasierten GitHub-Connector nicht in das Repository übertragbar.** Deshalb enthält diese Commitfolge die zentrale bevorzugte Tonzuordnung sowie den sicheren Legacy-Fallback; die zehn Originaldateien liegen getrennt im vom Benutzer bereitgestellten ZIP `SugarWear_Alarmtoene_Original_MP4.zip`. Das Archiv wird am Repository-Root entpackt, bevor `installDebug` für den Test ausgeführt wird. Ohne die zehn neuen MP4-Assets spielt SugarWear weiter die bisherigen gebündelten Töne. Erfolg der Source-Code-CI bedeutet nicht, dass diese Original-Töne auf einer Watch installiert wurden.

## Gate und Gerätestatus
CI: Architektur, detekt/ktlint, Unit-Tests, Debug-Build, Lint, Watch Face Format und Vigil prüfen. Realgerätepflicht: neuen Alarm auslösen, Notification bei App im Vordergrund und Hintergrund prüfen, durch Tippen Vollbild öffnen, Snapshot bei Folgemesswerten stabil, Ton je Alarm prüfen, ACK/Snooze und DND testen. Keine garantierte automatische Vollbildanzeige auf Wear OS behaupten.
