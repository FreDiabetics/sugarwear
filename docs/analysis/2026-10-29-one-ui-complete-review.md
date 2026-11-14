# Vollständige One-UI-Durchsicht — 2026-10-29

Alle 35 Seiten aus der linken One-UI-Navigation wurden vollständig gelesen (34 Detailseiten plus Übersicht). Die Website beschreibt allgemeine One-UI-Prinzipien; sie ist keine pixelgenaue oder vollständige One UI Watch 9 API-Spezifikation. Fremde rechtliche Texte oder externe, weiterführende Android-Dokumentation gehören nicht automatisch zu diesen 35 Seiten.

## Abgleich je Seite

| Gelesene Seite | Für Sugarlicious/SugarWear relevante Anwendung |
|---|---|
| [Overview](https://developer.samsung.com/one-ui/overview.html) | Kurze Wege, gut lesbare Inhalte und anpassungsfähige Oberflächen. |
| [Basic structure](https://developer.samsung.com/one-ui/structure/basic.html) | System-Sperre respektieren; die App darf eigene Inhalte freigeben, aber keine PIN umgehen. |
| [Visual depth](https://developer.samsung.com/one-ui/structure/visual-depth.html) | Keine konkurrierenden Schatten- oder Konturebenen; Hierarchie durch ruhige Flächen. |
| [Basic layout](https://developer.samsung.com/one-ui/layout/basic.html) | Inhalte logisch gruppieren und Aktionen gut erreichbar halten. |
| [Grid system](https://developer.samsung.com/one-ui/layout/grid.html) | Phone-Ränder und Ausschnitte berücksichtigen; Zahlenwerte nicht ungeprüft auf Wear übertragen. |
| [App bar](https://developer.samsung.com/one-ui/comp/app-bar.html) | Passende kompakte/erweiterte Header und Erhalt der Position beim Zurückkehren. |
| [Bottom bar](https://developer.samsung.com/one-ui/comp/bottom-bar.html) | Wenige klar bezeichnete Hauptaktionen; keine zusätzliche Toolbar ohne Bedarf. |
| [Bottom navigation](https://developer.samsung.com/one-ui/comp/bottom-navigation.html) | Phone-Navigation nicht als Bottom-Bar auf ein rundes Wear-Display kopieren. |
| [Buttons](https://developer.samsung.com/one-ui/comp/button.html) | Eine konsistente Button-Art je Kontext; klare Aktionen und begrenzte Hervorhebung. |
| [Dialog](https://developer.samsung.com/one-ui/comp/dialog.html) | Entscheidungen mit klaren Aktionen; Position nach Plattform statt blindem Phone-Layout. |
| [Lists](https://developer.samsung.com/one-ui/comp/list.html) | Kurze Hauptbegriffe, logische Gruppen und Schalter rechts. Erklärungen auf die Detailseite. |
| [Search](https://developer.samsung.com/one-ui/comp/search.html) | Bei vorhandener Suche Synonyme berücksichtigen; daraus folgt kein Zwang zu einem neuen Suchfeature. |
| [Toasts](https://developer.samsung.com/one-ui/comp/toast.html) | Kurzes, unkritisches Feedback per Toast; wichtige Gesundheitszustände dauerhaft sichtbar halten. |
| [Color system and usage](https://developer.samsung.com/one-ui/color/system.html) | Ruhige neutrale Flächen, kleine Akzente; klinische Warnfarben und freigegebene Markenfarbe erhalten. |
| [Theme](https://developer.samsung.com/one-ui/color/theme.html) | Hell und Dunkel mit lesbaren Text-/Flächenkontrasten unterstützen. |
| [Background](https://developer.samsung.com/one-ui/iconography/background.html) | Adaptive Systemmaske und weiche Icon-Hintergründe verwenden. |
| [Symbol](https://developer.samsung.com/one-ui/iconography/symbol.html) | Gleichwertige Symbolgrößen, klare vollständige Silhouetten. |
| [Icon color](https://developer.samsung.com/one-ui/iconography/color.html) | Kontrollierter Verlauf und gute Symbol-/Hintergrundtrennung; Nutzervorlagen erhalten. |
| [Motion introduction](https://developer.samsung.com/one-ui/motion/intro.html) | Unmittelbare nachvollziehbare Reaktion ohne unterbrochene Gesten. |
| [Motion basics and usage](https://developer.samsung.com/one-ui/motion/basic.html) | Natürliche kontinuierliche Bewegung; Textwechsel ohne überlagerte Glyphen. |
| [Sound](https://developer.samsung.com/one-ui/sound-and-haptic/sound.html) | Nur notwendige Töne, klarer Zweck; ein Alarm darf keinen zweiten Systemton erzeugen. |
| [Haptic](https://developer.samsung.com/one-ui/sound-and-haptic/haptic.html) | Haptik sparsam, passend zur Aktion und zu den Systemeinstellungen. |
| [Focused and purposeful](https://developer.samsung.com/one-ui/writing/focused-and-purposeful.html) | Pro Schritt eine klare Aufgabe; unnötige technische Beschreibungen entfernen. |
| [Simple and human](https://developer.samsung.com/one-ui/writing/simple-and-human.html) | Einfache natürliche Begriffe; keine unnötigen Warnungen oder wiederholten Popups. |
| [Empowering and engaging](https://developer.samsung.com/one-ui/writing/empowering.html) | Konkrete Hilfe und nächste Aktion statt einer Sackgasse. |
| [Accessibility introduction](https://developer.samsung.com/one-ui/accessibility/intro.html) | Zugänglichkeit für Sehen, Hören und Motorik als Grundanforderung. |
| [Screen reader](https://developer.samsung.com/one-ui/accessibility/screen-reader.html) | Name und Zustand für Bedienelemente; rein dekorative Icons nicht doppelt vorlesen. |
| [Focus order](https://developer.samsung.com/one-ui/accessibility/focus-order.html) | Logische Fokusreihenfolge und zusammengehörige Elemente gruppieren. |
| [Color and contrast](https://developer.samsung.com/one-ui/accessibility/color-contrast.html) | Nicht allein über Farbe informieren; kleine Texte ausreichend kontrastreich gestalten. |
| [Layout and typography](https://developer.samsung.com/one-ui/accessibility/layout-and-typo.html) | Systemskalierung und genügend Platz, bis 200 Prozent Textgröße berücksichtigen. |
| [Interaction and control](https://developer.samsung.com/one-ui/accessibility/interaction-and-control.html) | Scrollen von Änderungen trennen; Fehler zeigen und einen Weg zurück anbieten. |
| [Content](https://developer.samsung.com/one-ui/accessibility/content.html) | Text und visuelles Feedback ergänzen Ton; keine unnötigen blinkenden Effekte. |
| [Designing for large screens](https://developer.samsung.com/one-ui/largescreen-and-foldable/intro.html) | Phone/Tablet-Layouts nach Fenstergröße; kein Mehrspaltenzwang für Wear. |
| [Layout design for large screens](https://developer.samsung.com/one-ui/largescreen-and-foldable/large_screen_layout.html) | Breite Ansichten nicht nur strecken; Raster und Kontext passend anordnen. |
| [Designing for foldables](https://developer.samsung.com/one-ui/largescreen-and-foldable/designing_for_foldable.html) | Zustand, Eingaben und Scrollposition über Größenänderungen erhalten. |

## In diesem Auftrag umgesetzt bzw. berücksichtigt

- SugarWear registriert die bestehenden kanonischen Sugarlicious-Glukoseprovider, statt Logik und Darstellungen neu zu duplizieren. Native Vorschau-XML wird beim Build aus denselben Quellen bezogen. Kurze Namen bleiben nach der letzten Nutzerentscheidung ohne zusätzliche Icon-Überschriften.
- Übersicht: originale Delta-/Alters-Symbole ohne Hintergrund; Text bleibt echter systemskalierter Text.
- Unbenutzte Vorhersage-Farbregler im reinen Watch-Collector entfallen. Gemeinsame Protokollfelder werden aus Kompatibilitätsgründen nicht entfernt.
- Settings-Hauptnavigation: kurze Begriffe, keine wiederholten erklärenden Untertitel bei Navigation zur Detailseite. Neutrale Kacheln und gemeinsame flache Aktionsbuttons.
- Alarmkanal-Berechtigungen sind ein gemeinsamer Schritt bzw. eine gemeinsame Kachel. Eigene Alarmwiedergabe ist vom stillen Statushinweis getrennt; Nutzer bestätigte nur einen SugarWear-Ton.
- Bestehende zentrale Schalter, scrollsichere Regler, Systemschrift und echte sp-Texte werden wiederverwendet.

## Prüfgrenzen und offene Nachweise

Durchsicht ist nicht gleich vollständig physisch überprüfte App. Builds, gezielte Tests und statische Checks belegen Codepfade; Sichttests belegen nur die tatsächlich geprüften Seiten/Geräte. Ein vollständiger Geräteabgleich aller Mobile-, Widget- und Wear-Unterseiten bei jeder Systemskalierung ist damit nicht behauptet. Große-Display- und Foldable-Prinzipien sind für Mobile relevant; eine neue Tablet-Navigation wird durch diese Prüfung nicht ungefragt eingeführt. Fremde Samsung-/Google-Complications behalten ihre eigenen Sperr-/Datenschutzregeln.
