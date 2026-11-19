# AAPS CWF Parser

Sicherer Parser und Validator für AAPS-Custom-Watchface-ZIPs. Er blockiert
Pfadtraversal, doppelte Dateinamen, übergroße Archive, fehlende Pflichtdateien,
ungültige JSON-Typen und außerhalb der Grenzen liegende Layoutwerte. Bekannte
dynamische CWF-Funktionen werden als explizite Feature-Flags ausgegeben.

```text
gradlew :tools:aaps-cwf-parser:run --args="AAPS_V2.zip"
```
