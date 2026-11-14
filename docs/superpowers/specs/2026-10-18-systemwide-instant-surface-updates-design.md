# Systemweite sofortige Oberflächenaktualisierung ohne Ladezustände

## Ziel

Jeder neue, kanonisch gespeicherte Sugarlicious-Zustand muss ohne periodisches Warten an alle sichtbaren Oberflächen verteilt werden. Mobile-Widgets, Wear-App, Wear-Tiles, Complications und Watchfaces zeigen bis zur vollständig fertigen neuen Darstellung immer den letzten gültigen Zustand. Ein Lade-, Leer- oder Platzhalterzustand darf einen bereits vorhandenen gültigen Zustand niemals ersetzen.

## Geltungsbereich

- Sugarlicious Mobile App Widgets
- Mobile-zu-Wear-Transport
- Sugarlicious Wear App
- Sugarlicious Wear Tiles
- alle Sugarlicious Complication-Provider und davon gespeiste Watchfaces
- direkter Watch-G7-Zustand, soweit er dieselben Wear-Oberflächen versorgt

Der fachliche Resolver, die Quellenauswahl und die Validierung der eingehenden Daten bleiben unverändert. Der Auftrag optimiert ausschließlich Zustandsverteilung, Invalidierung, Rendering und Wiederherstellung.

## Verbindlicher Datenfluss

```text
kanonischer Commit
  -> ein revisionsbehaftetes Surface-Update-Ereignis
     -> Mobile Widgets (parallel)
     -> MessageClient (sofort, wenn Watch erreichbar)
     -> DataClient (dauerhaft, parallel)

Wear-Empfang
  -> Reihenfolge/Deduplizierung prüfen
  -> atomar persistieren
  -> Last-known-good Snapshot veröffentlichen
  -> Wear App + betroffene Tiles + betroffene Complications parallel invalidieren
```

Die Oberfläche darf nie vor dem erfolgreichen Commit invalidiert werden. Ein fehlgeschlagener Renderlauf verändert den aktuell sichtbaren Zustand nicht.

## Latest-wins- und Coalescing-Regel

Surface-Updates erhalten eine monotone Revision aus dem kanonischen Zustand. Pro Oberfläche darf höchstens ein Renderlauf aktiv und ein neuester ausstehender Auftrag gespeichert sein. Treffen während eines Renderlaufs mehrere Revisionen ein, wird anschließend ausschließlich die neueste Revision gerendert. Eine ältere Revision darf keine neuere Darstellung überschreiben.

Coalescing dient nur dazu, überholte Arbeit zu verwerfen. Es darf keinen künstlichen Debounce und keine zusätzliche Wartezeit einführen.

## Mobile Widgets

Der bereits committed `TherapyDisplayState` wird dem Widget-Updatepfad direkt übergeben. Der Renderpfad darf denselben Zustand nicht erneut blockierend aus DataStore laden. Alle Widgetklassen werden unabhängig und parallel aktualisiert; ein langsames Graph-Widget blockiert weder Glukose- noch Therapie-Widget.

Vor der Übergabe an den Launcher wird die neue Darstellung vollständig erzeugt. Bis dahin bleibt die vorherige `RemoteViews`-/Glance-Darstellung sichtbar. Die Initiallayouts enthalten keine Ladebeschriftung, keinen Spinner und keine simulierten Daten. Existiert ein persistierter letzter Widget-Snapshot, wird er bei Rebind/Process-Recovery wiederverwendet. Nur ohne jemals vorhandenen gültigen Zustand ist eine neutrale `keine Daten`-Darstellung zulässig.

Periodische Widget-Updates bleiben ausschließlich für zeitabhängige Alters-/Freshness-Grenzen und als Recovery-Sicherheitsnetz bestehen. Sie sind nicht der primäre Datenaktualisierungspfad.

### Widget-Einstellungen

Änderungen in `WidgetConfigurationActivity` werden sofort persistent gespeichert, dürfen aber nicht für jede Slider-Zwischenposition einen vollständigen Launcher-Renderauftrag aufstauen. Die Vorschau reagiert lokal direkt. Der Widget-Updatekoordinator führt schnelle Änderungen nach der Latest-wins-Regel zusammen.

Sobald die Widget-Einstellungen verlassen werden (`onStop`/Back/Done), muss die zuletzt gespeicherte Konfiguration synchron als neueste Revision an den Updatekoordinator übergeben und vollständig abgearbeitet werden. Beim Zurückkehren zum Launcher muss die echte betroffene Widget-Instanz bereits die letzte Einstellung zeigen. Ein noch laufender älterer Renderauftrag darf dieses Ergebnis anschließend nicht zurücksetzen. Auch dabei bleibt bis zur fertigen neuen Darstellung der letzte gültige Widgetzustand sichtbar; ein Loading- oder Leerzustand ist unzulässig.

## Mobile-zu-Wear-Transport

`MessageClient` und dringender `DataClient` bleiben parallele, unabhängige Garantien:

- `MessageClient`: minimale Latenz bei erreichbarer Watch
- `DataClient`: dauerhafte Zustellung bei Verbindungsunterbrechung

Der schnelle Pfad darf nicht auf DataClient, Widget-Rendering oder andere Oberflächen warten. Beide Transporte tragen dieselbe Revision. Doppelte oder verspätete Zustellungen werden auf der Watch dedupliziert; eine ältere DataItem-Zustellung darf einen neueren Message-Zustand nicht zurückrollen.

## Wear-Persistenz und Fan-out

Nach erfolgreicher Annahme wird der neue Watch-Zustand atomar gespeichert. Danach startet ein gemeinsamer Wear-Surface-Fan-out:

- Event für die geöffnete Wear-App
- selektive Tile-Invalidierung
- selektive Complication-Invalidierung
- erforderliche Watchface-Aktualisierung über die Complication-Provider

Diese Verbraucher werden parallel gestartet. Langsame oder fehlerhafte Systemcallbacks einer Oberfläche dürfen die anderen nicht blockieren. Diagnostik erfasst Commit-, Invalidierungs- und Abschlusszeiten getrennt.

Direkte G7-Watch-Werte verwenden denselben Surface-Fan-out, nachdem der lokale kanonische Resolverzustand feststeht.

## Last-known-good-Vertrag

Für jede Oberfläche gilt:

1. Ein gültiger Zustand wird vollständig gerendert und als letzter erfolgreicher Snapshot markiert.
2. Ein neuer Renderlauf ersetzt den Snapshot erst nach erfolgreicher Fertigstellung.
3. Fehler, Cancellation, Prozesswechsel oder Reconnect lassen den alten Snapshot bestehen.
4. Stale/Signalverlust verändert die fachliche Darstellung des letzten Werts, ist aber kein Ladezustand.
5. `null`, Decoderfehler oder eine unvollständige neue Revision dürfen einen gültigen Snapshot nicht löschen.

## Entfernung der Ladezustände

Produktive Oberflächen dürfen weder `Loading`, Spinner, Striche als Ladeersatz noch temporäre leere Container anzeigen. Bestehende Lade-Branches und Ressourcen werden entfernt oder durch den Last-known-good-Vertrag unerreichbar gemacht. Previews dürfen Beispielwerte zeigen, produktive Initiallayouts jedoch nur einen wiederhergestellten Snapshot oder neutral `keine Daten`.

## Prozess- und Neustartverhalten

Der letzte erfolgreich sichtbare Zustand wird so gespeichert, dass Mobile-Launcher und Wear-Prozess ihn nach Process Death oder Neustart sofort wieder anzeigen können, ohne zuerst auf neue Daten zu warten. Anschließend wird im Hintergrund die aktuelle kanonische Revision abgeglichen. Ein Neustart darf keinen Loading-Zwischenzustand erzeugen.

## Ressourcenverbrauch

- keine Polling-Schleifen für Datenaktualisierung
- keine künstlich kurzen Timer
- keine parallelen Renderläufe derselben Oberfläche
- Bitmap-/Graph-Rendering nur für tatsächlich betroffene Oberflächen
- veraltete Revisionen werden vor teurer Rendering-Arbeit verworfen
- periodische Minute-/Freshness-Updates bleiben gebündelt

## Tests

Automatisiert werden mindestens geprüft:

- kanonischer Commit startet Mobile-Widget- und Wear-Fan-out ohne gegenseitige Blockierung
- alle Mobile-Widgettypen werden parallel invalidiert
- direkter Zustandsparameter verhindert erneutes blockierendes Store-Lesen
- mehrere schnelle Revisionen rendern nur `aktuell + neueste ausstehende`
- ältere Revision kann neuere Darstellung nicht überschreiben
- Renderfehler behält letzten gültigen Snapshot
- Initiallayouts und produktiver Code enthalten keinen Loading-Zustand
- Message- und Data-Pfad starten parallel
- Wear persistiert vor Surface-Invalidierung
- Wear-App, Tiles und Complications werden aus demselben Commit-Ereignis invalidiert
- ein langsamer Tile-/Complication-Callback blockiert andere Oberflächen nicht
- verspäteter DataItem-Zustand kann neueren Message-Zustand nicht zurückrollen
- Process-Recovery stellt Last-known-good vor dem ersten neuen Wert dar
- schnelle Slider-Änderungen stauen keine vollständigen Widget-Renderaufträge auf
- Verlassen der Widget-Einstellungen wartet auf beziehungsweise erzwingt die neueste echte Widget-Darstellung

## Validierung

`CODE-VALIDIERT` und `BUILD-VALIDIERT` erfordern relevante Unit-/Integrationstests sowie `test`, Debug-/Release-Builds, `lint`, `detekt` und `ktlintCheck`.

`HARDWARE-VALIDIERT` erfordert auf Smartphone und Watch eine neue Datenrevision mit Zeitmessung bis zu Mobile-Widget, geöffneter Wear-App, Tile und Complication sowie Tests bei schnellem Folgewert, Display aus, Process Death und Reconnect. Bis dahin bleibt die Hardwarevalidierung offen.

## Definition of Done

- Jede neue kanonische Revision löst sofort einen ereignisgetriebenen Surface-Fan-out aus.
- Kein Surface wartet auf einen periodischen Updatezyklus, um neue Daten zu sehen.
- Mobile-Widgets, Wear-App, Tiles und Complications aktualisieren unabhängig voneinander.
- Überholte Renderaufträge werden verworfen; die neueste Revision gewinnt.
- Während jeder Aktualisierung bleibt der letzte gültige Zustand sichtbar.
- Kein produktiver Ladezustand, Spinner oder temporärer Leerzustand existiert mehr.
- Process Death, Reconnect und Neustart stellen zuerst Last-known-good wieder her.
- Nach Verlassen der Widget-Einstellungen zeigt die Launcher-Instanz sofort die zuletzt gespeicherte Konfiguration.
- alle automatisierten Quality Gates sind grün.
- reale Latenz und fehlende Loading-Zustände sind getrennt als Hardwaretest dokumentiert.
