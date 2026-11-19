# Watchface and Complication Parity Matrix

Alle aufgeführten Pakete bauen als WFF v1 und bestehen den offiziellen
Validator. Die bis 0.5.1 veröffentlichten Pakete besitzen aktive plus
Doze/AOD-Screenshots mit synthetischen Daten auf Wear OS 6. Für die drei neuen
0.6.0-Varianten steht dieser Bildlauf noch aus; die vier 0.6.0-Analogpakete
wurden separat als DEX-frei geprüft.

| Watchface | Status | Wesentliche Abweichung |
|---|---|---|
| AAPS V4 | visuell identisch, technisch anders | originales WFF-Layout; eigene read-only Provider |
| AAPS V2 | teilweise abweichend | 8-Slot-Limit, dynPref/Twin-View ersetzt |
| AAPS Standard | teilweise abweichend | CWF-Dynamik entfällt; analoge Zeiger deklarativ |
| AAPS Circle | visuell identisch, technisch anders | Ring/Zeiger WFF statt Service |
| AAPS Digital Style | teilweise abweichend | laufzeitwählbare Rahmenoptionen fehlen |
| AAPS BigChart | teilweise abweichend | dynPref/Twin-View nicht direkt möglich |
| AAPS Large | teilweise abweichend | dynPref nicht direkt möglich |
| AAPS NoChart | teilweise abweichend | dynPref/Twin-View nicht direkt möglich |
| AAPS Cockpit | teilweise abweichend | LED-dynData/Twin-View ersetzt |
| AAPSV2 + TT DarkOnly | teilweise abweichend | TempTarget/AvgDelta konkurrieren am Slotlimit |
| AAPS Community | teilweise abweichend | dynamische Präferenzen statisch kuratiert |
| AIMICO | teilweise abweichend | sichtbare Felder auf 7 Slots normalisiert |
| Analog G-Watch | funktional gleichwertig | generische deklarative Zeiger |
| Blue Ring | funktional gleichwertig | deklarative Zeiger, Originalressourcen erhalten |
| DigitalBigGraph | teilweise abweichend | Split-Zeit als WFF-DigitalClock |
| Digital G-Watch | teilweise abweichend | repariertes Archiv; 8-Slot-Grenze |
| Gears | funktional gleichwertig | deklarative Zeiger, Originalhintergrund |
| Gota | teilweise abweichend | kommentiertes JSON reproduzierbar repariert |
| LuckyLoopKoeln | teilweise abweichend | fehlerhafte Chartposition repariert |
| P-Zero | teilweise abweichend | fehlende Hintergrundmaße ergänzt |
| Robby | funktional gleichwertig | generische deklarative Zeiger |
| SimpleDigital | teilweise abweichend | Split-Zeit in WFF-DigitalClock zusammengeführt |
| SteamPunk | funktional gleichwertig | Originalbilder plus deklarative Zeiger/Slots |
| Sugarlicious Digital | identisch | neues Originaldesign; keine Portierung |
| Sugarlicious Analog | identisch | neues Originaldesign; eigenständige deklarative Zeiger |
| Sugarlicious Orbit | identisch | neues Originaldesign; eigenständige deklarative Zeiger |
| Sugarlicious Rings | identisch | neues Originaldesign; eigenständige deklarative Zeiger |
| Sugarlicious Graph | identisch | neues Originaldesign; eigenständige deklarative Zeiger |
| PinkFloydTheWall | blockiert | geschützte Drittmotive/Marke ungeklärt |

| Complications | Status |
|---:|---|
| 1–17 | funktional gleichwertig; Text-, Ranged- und Bildvarianten getestet |
| 18 | teilweise abweichend; kein sicheres Temp-Target-Flag im Broadcast |
| 19–27 | funktional gleichwertig im verfügbaren Vertrag; Loop auf Suggested/Enacted begrenzt |

Die vorhandenen Goldens sind Rendering- und Regressionsnachweise. Pixelgenaue
Original-vs.-Port-Messungen auf identischer realer Uhr bleiben offen.
