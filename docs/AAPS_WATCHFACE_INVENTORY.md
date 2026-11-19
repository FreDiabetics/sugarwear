# AAPS Watchface Inventory

| Gruppe | Namen | Upstream-Technik | Lokaler Stand |
|---|---|---|---|
| klassisch | Circle, Digital Style | ausführbare Services | codefreie WFF-v1-Pakete gebaut |
| Custom | AAPS Custom/Standard V1/V2 | Kotlin-Renderer, ZIP/JSON | Standard portiert; Parser/Generator vorhanden |
| WFF | AAPS V4 | codefreies Push-APK, 5 Slots | Original-WFF übernommen, Provider entkoppelt |

AndroidAPSdocs enthält am gepinnten Commit 20 offizielle Austausch-ZIPs:
`AAPS V2`, `AAPS`, `AIMICO`, `AAPS BigChart`, `AAPS Large`, `AAPS NoChart`,
`Analog G-Watch`, `AAPS Cockpit`, `Digital G-Watch`, `DigitalBigGraph`, `Gears`,
`Gota`, `LuckyLoopKoeln`, `P-Zero`, `PinkFloydTheWall`, `Robby Watchface`,
`SimpleDigital`, `AAPS SteamPunk`, `Blue Ring`, `AAPSV2 + TT DarkOnly`.

19 sind durch eigenständige WFF-Pakete abgedeckt. Zusammen mit Circle, Digital
Style, Standard und AAPS V4 entstehen 23 veröffentlichbare Pakete.
PinkFloydTheWall ist wegen ungeklärter Drittmotiv-/Markenrechte blockiert.

Vier bekannte Quellfehler werden SHA-gepinnt repariert: nachgestellte ZIP-Daten
bei Digital G-Watch, JSON-Kommentare bei Gota, eine fehlerhafte Chartposition
bei LuckyLoopKoeln und fehlende Hintergrundmaße bei P-Zero. Das Skript
`tools/repair-known-official-cwf.ps1` verweigert unbekannte Revisionen.
