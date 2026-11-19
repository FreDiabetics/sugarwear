# Repository-Schutz und Recovery

## Zielzustand für `main`

`main` ist der Integrationszweig und wird ausschließlich über Pull Requests verändert.
Verbindliche GitHub-Regeln:

- Pull Request vor Merge verpflichtend.
- Status-Check `verify` aus `.github/workflows/build.yml` verpflichtend; Branch muss aktuell sein.
- Force-Push deaktivieren.
- Branch-Löschung deaktivieren.
- Conversation resolution vor Merge verlangen.
- Administratoren/Owner nicht von den Regeln ausnehmen.
- CODEOWNERS ist aktiv; sobald mindestens ein unabhängiger Maintainer vorhanden ist, mindestens eine Pflichtfreigabe aktivieren.

Bei einem Single-Maintainer-Repository bleibt die Zahl verpflichtender Freigaben vorerst bei `0`, damit eigene PRs nicht unmergebar werden. PR- und CI-Pflicht gelten trotzdem.

Die geprüfte Einmal-Konfiguration kann mit folgendem Repository-Skript angewendet und anschließend automatisch verifiziert werden:

```powershell
pwsh -File tools/protect-main.ps1
```

Das Skript benötigt eine mit Repository-Administrationsrechten authentifizierte GitHub CLI (`gh`).

## Tags und Releases

- Releases nur aus einem grünen Commit auf `main` erzeugen.
- Versions-Tags nicht verschieben oder wiederverwenden.
- Bevorzugt signierte/annotierte Tags verwenden.
- Release-Artefakte zusammen mit Prüfsummen veröffentlichen.
- Fehlerhafte Releases durch eine neue Version korrigieren, nicht durch stilles Ersetzen bestehender Tags oder Assets.

## Backup außerhalb dieses Repositorys

Ein Branch oder Tag im selben Repository schützt nicht vor Repository-Löschung. Mindestens ein Backup muss außerhalb von `FreDiabetics/sugarlicious` liegen.

Empfohlene Strategie:

1. Regelmäßig einen vollständigen Mirror erzeugen: `git clone --mirror` bzw. `git remote update`.
2. Den Mirror auf einem zweiten, getrennten Speicherziel sichern, z. B. privates Backup-Repository oder verschlüsseltes lokales/NAS-Backup.
3. Zusätzlich Releases, Tags und GitHub-Metadaten dokumentieren.
4. Monatlich prüfen, ob aus dem Mirror ein neues Repository vollständig wiederhergestellt werden kann.
5. Vor riskanten Repository-/Org-Änderungen einen aktuellen Mirror und einen Export der relevanten Einstellungen anlegen.

Recovery eines Git-Mirrors erfolgt grundsätzlich durch Anlegen eines neuen leeren Repositorys und anschließendes `git push --mirror` aus dem geprüften Backup.

## Secrets

- Keine API-Tokens, Passwörter, privaten Schlüssel, Keystores oder produktiven Zugangsdaten committen.
- Lokale Geheimnisse ausschließlich über nicht versionierte Dateien, Umgebungsvariablen oder GitHub Actions Secrets bereitstellen.
- Ein versehentlich veröffentlichter Secret-Wert gilt als kompromittiert: sofort widerrufen/rotieren; bloßes Löschen aus dem letzten Commit reicht nicht.
- Vor Releases und bei verdächtigen Änderungen Repository-Historie auf Secret-Muster prüfen.

## Notfalländerungen

Ein direkter Schreibzugriff auf `main` ist kein normaler Entwicklungsweg. Falls ein echter Recovery-Fall eine Ausnahme erfordert, muss anschließend ein nachvollziehbarer Commit/PR dokumentieren, warum die Ausnahme nötig war und welcher Zustand wiederhergestellt wurde.
