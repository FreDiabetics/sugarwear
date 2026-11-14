# WFF Generator

Konservativer CWF-zu-WFF-Generator. Er ordnet bekannte AAPS-Felder den lokalen
Complication-Providern zu, rendert die Uhrzeit deklarativ und begrenzt jedes
Ergebnis auf acht Slots. Sobald dynamische oder sichtbare CWF-Elemente nicht
verlustfrei abgebildet werden können, bricht er standardmäßig ab.

Eine bewusst degradierte technische Vorschau erfordert eine ausdrückliche
Freigabe und listet jede Warnung und jedes ausgelassene Element auf:

```text
gradlew :tools:wff-generator:run --args="AAPS_V2.zip output/watchface.xml --allow-degraded"
pwsh -File tools/wff-validator/validate.ps1 -WatchfaceFiles output/watchface.xml
```
