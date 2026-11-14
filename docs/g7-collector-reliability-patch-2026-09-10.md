# SugarWear collector reliability patch (2026-09-10)

## Ausgangsstand

- Branch: `feature/sugarwear-product-and-vigil-recovery`
- HEAD vor dem Patch: `a2d23c1b1c1f7876128149843af9d5984ad99017`
- Remote-Abgleich: Branch identisch mit `origin/feature/sugarwear-product-and-vigil-recovery`
- Working Tree vor dem Patch: sauber
- SugarWear: `app.aapswear.g7watch`, Version `0.1.11` (`12`)
- Reading-DB: Version `6`; keine Schemaänderung für diesen Patch erforderlich

## Root Causes und Fixes

### Silent Expected Windows

Symptom: Nach einem Prozess-/Service-Lifecycle-Wechsel konnte der bestehende Reconciler den
nächsten Alarm reparieren, rekonstruierte aber die dazwischen vollständig fehlenden Expected
Windows nicht. Ein offener Attempt wurde nur generisch als `HUNG` abgeschlossen.

Fix: Der zentrale `G7RuntimeReconciler` schließt einen nach Prozessverlust offenen Lauf als
`PROCESS_INTERRUPTED`, rekonstruiert ausschließlich innerhalb der belegten Sensor-Session jeden
fehlenden Fünf-Minuten-Slot als Gap und plant danach weiterhin genau den vorhandenen zentralen
Recovery-Pfad. Ein BLE-Fehler bleibt dadurch vom nächsten Scheduler-Slot entkoppelt.

### Ledger-Duplikation und Identität

Symptom: Die bisherige ID bestand nur aus `expectedAt`. Damit fehlte die im Datenmodell notwendige
Sensor-/Session-Grenze und Reconcile-Aufrufe waren nach einem Sensorwechsel nicht kanonisch
zuordenbar.

Fix: Die ID ist jetzt `sensor + session + expectedAt`. Upserts aktualisieren weiterhin denselben
Window-Record und behalten dessen Lifecycle-Felder; mehrfaches Reconcile erzeugt keinen zweiten
kanonischen Window-Datensatz.

### NO_CALLBACK

Symptom: Der Collector unterschied nur den fehlenden Connect-Callback. Fehlende Discovery-,
Descriptor- und Write-Callbacks liefen in allgemeinere GATT-/Session-Timeouts und konnten deshalb
nicht gezielt ausgewertet oder über die bereits begrenzte Close/Settle/New-Generation-State-Machine
wiederholt werden.

Fix: Alle vier Callback-Phasen besitzen stabile Fehlercodes. Sie laufen durch denselben begrenzten
Direct-Retry (alte Generation invalidieren, GATT schließen, 2,5 s Settle, neue Generation), danach
höchstens durch den bereits vorhandenen einmaligen Scan-Fallback und schließlich in einen
Terminalfehler. Stale Callbacks bleiben durch `G7GattGenerationRegistry` ausgeschlossen.

### Backfill-Latenz

Symptom: LIVE löste History bereits direkt aus, aber die Window-Diagnostik verband Gap, erstes
nachfolgendes LIVE, Request, Response und Insert nicht. Damit war die Recovery-Latenz nachträglich
nicht zuverlässig berechenbar.

Fix: Der LIVE-vor-Backfill-Ablauf bleibt erhalten. Offene Windows derselben Sensor-/Session-ID
erhalten jetzt `nextLiveMeasuredAt`, `nextLiveReceivedAt`, `liveCommittedAt`,
`backfillRequestedAt`, `backfillResponseAt`, `backfillInsertedAt` und `recoveredMeasuredAt`.
Zuordnung und Deduplizierung bleiben measuredAt-/Sensor-/Session-basiert; `receivedAt` beeinflusst
keine Freshness.

### Process-Diagnostik und Retention

Jede Prozessinstanz erhält eine UUID. Runtime-Events enthalten UUID, PID, Startzeit, Uptime,
App-Version, Collector-Status sowie Sensor/Session. Attempt-, Slot- und Window-Retention wurden auf
2.304 Records erhöht. Das entspricht bei fünf Minuten Takt acht Tagen nominaler Windows und bietet
damit Reserve über das 5–7-Tage-Ziel hinaus. Die Speicherung bleibt begrenzt; Reading-Retention war
bereits auf 30 Tage beziehungsweise 2.000 Datensätze begrenzt.

## Sicherheits- und Architekturgrenzen

- Kein Mobile-BLE-Collector, kein zweiter Scheduler und kein zweiter GATT-Orchestrator.
- Keine DB-Migration und keine destruktive Migration.
- Keine Änderung am Source Resolver, an Complications oder Watchfaces.
- Freshness bleibt ausschließlich eine Funktion von `measuredAt`.
- Kein Sensor-/Bond-/Pairing-/App-Daten-Reset und kein automatischer Merge.

## Hardwarestatus

Die Änderung ist noch nicht auf echter Galaxy-Watch-Hardware verifiziert. Verbesserungen an
Window Execution, NO_CALLBACK Recovery und Backfill-Latenz müssen in einer neuen mehrtägigen
Hardwarephase anhand der erweiterten Diagnostik gemessen werden.
