# Widget-Prüfung für 1.8.5-kids.7

## Bedienung

- Die neue **Playlist-Liste groß** verwendet 64 dp Mindesthöhe je Eintrag und 60 dp große Cover als Ausgangsgröße. Die Namen sind fett und können zwei Zeilen belegen.
- Die normale **Playlist-Liste** beginnt bei 42 dp Zeilenhöhe mit größeren Covern. Beide Listen teilen freie Höhe unter den sichtbaren Einträgen auf. Die Zeilen wachsen bis auf das Eineinhalbfache der gemessenen Grundhöhe. Auf Android 12 und neuer wächst auch das Bild mit. So füllt eine einzelne Playlist in einem hohen Listenwidget nicht die gesamte Fläche.
- Die **Playlist-Kacheln** sind im Widget-Menü mit einer Zielgröße von 1×1 registriert. Ist zu wenig Platz für eine beschriftete Kachel, erscheint ein direkt antippbares Cover. Es startet die erste sichtbare Playlist. Weitere ausgewählte IDs bleiben für eine spätere Vergrößerung gespeichert.
- **Keine** leert die gesamte Auswahl auch bei laufender Suche. **OK** speichert, **Abbrechen** verwirft die Änderungen. Beim Bearbeiten einer älteren automatischen Auswahl übernimmt der Dialog die bisherigen Vorschläge als Vorauswahl.

## Erneutes Anzeigen

Der Player hält fertige 128-Pixel-Vorschaubilder einschließlich fehlender Cover in einem auf 6 MiB begrenzten Arbeitsspeicher. Solange das Bild dort verfügbar ist, überspringt er die Abfrage der Titel, Dateizugriffe und Collagenberechnung. Bei Änderungen der Playlists und Bilder verwirft er veraltete Ergebnisse. Der Android-Startbildschirm kann nach einer Drehung weiterhin neue Ansichten anfordern. Nach Prozessende oder Verdrängung eines Bildes ist ein neuer Ladevorgang möglich.

## Builds

Komplettpaket und Bilder-mit-Widgets verwenden Versionscode 24. Beide signierten Release-APKs verwenden den bisherigen Update-Schlüssel. Die 28 automatisierten Tests des Komplettpakets und die 14 Tests der Widget-Ausgabe umfassen auch die Höhenverteilung bei unterschiedlich vielen sichtbaren Einträgen. Die zwei Ausgaben ohne Widgets liegen im gemeinsamen Download weiterhin in Version 1.8.5-kids.6 vor.

## Prüfung im Tablet-Emulator

Das signierte Komplettpaket wurde über die bestehende App im Android-14-Emulator installiert. Die Testansicht enthielt sechs echte Widget-Instanzen. Die normale Liste zeigte bei 280 × 252 dp sechs Cover, die große Liste bei gleicher Größe drei. Eine 52 × 52 dp große Kachel zeigte genau ein Cover. Ein Tipp darauf startete die ausgewählte Testplaylist, bestätigt durch die laufende Android-Mediensitzung mit dem richtigen Titel und Playlist-Namen.

Die Testansicht zeigte insgesamt 40 Cover. Ein vollständiger Aufbau nach der Installation benötigte in einem Durchlauf 10,20 Sekunden, das erneute Öffnen der Testansicht 3,84 Sekunden. Gemessen wurde ab Einrichtung der Testansicht bis zur Sichtbarkeit aller 40 Cover. Dies ist ein Einzelvergleich unter Android 14 mit reduziertem virtuellem Arbeitsspeicher. Die Ladezeit auf dem physischen Samsung-Tablet mit One UI kann davon abweichen.

Bei einer Drehung und anschließender Rückkehr ins Hochformat meldete die Testansicht alle 40 Cover nach 704 bzw. 189 Millisekunden. Die einzelne Kachel behielt ihre Auswahl. Nach **Keine → OK** wurde die Kachel leer. Erneutes Öffnen der Konfiguration bestätigte null ausgewählte Playlists.
