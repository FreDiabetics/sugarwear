# Screenshot Comparator

Deterministischer PNG-Vergleich für Golden-Screenshot-Tests. Das Werkzeug prüft
identische Bildgrößen, berechnet MAE, RMSE, den Anteil abweichender Pixel sowie
die größte Kanalabweichung und schreibt ein rotes Differenzbild.

```powershell
.\gradlew :tools:screenshot-comparator:run --args='original.png neu.png diff.png 0'
```

Der optionale Schwellwert gilt pro Farbkanal (0 bis 255). Automatisch erzeugte
Watchface-Screenshots benötigen weiterhin einen Emulator oder ein reales Gerät;
dieses Werkzeug bewertet die anschließend vorliegenden PNG-Dateien.
