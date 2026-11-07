# Playlist-Widgets mit zwölf Einträgen

Das Komplettpaket und die Edition mit Playlist-Bildern und Widgets verwenden Versionscode 21. Die Auswahl und Darstellung sind für beide Widget-Typen auf zwölf Playlists je Instanz eingestellt. Bei automatischer Auswahl erscheinen die ersten zwölf verfügbaren Playlists in der Sortierung der Auswahl. Eine gespeicherte manuelle Auswahl bleibt beim Update erhalten.

## Vergleich auf dem virtuellen Galaxy Tab A8 mit Android 14

Ein Android-Testhost stellte echte Widget-Instanzen nebeneinander. Links standen zwei gleich breite Widgets mit je sechs verschiedenen Playlists übereinander, rechts eines mit denselben zwölf Playlists. Jedes kleine Widget war 280 × 252 dp groß, das große 280 × 504 dp. Überschriften waren ausgeblendet.

- Beim ersten Vergleich verwendeten kleine Kachel-Widgets drei Spalten, das große nur zwei. Ursache war die Rundung der verfügbaren Zeilen pro Widget. Die Spaltenzahl richtet sich jetzt nach der Breite. Die möglichen Spaltenzahlen 1, 2, 3 und 6 erlauben eine vollständige Zeile am Übergang nach sechs Einträgen.
- Bei beiden Typen wurden die vier Kanten jedes Covers und jedes Playlist-Namens gemessen. Für jedes der zwölf Paare stimmten die Koordinaten nach Abzug des horizontalen Vergleichsversatzes exakt überein. Vertikale Wischgesten änderten die Positionen nicht.
- Die Prüfung erfasst den von der App gestalteten Innenbereich. Fügt der Launcher jedem Widget Außenabstände hinzu, bleibt eine sichtbare Trennung möglich. Bei sehr kleinen Widgets sind die Namen und Berührungsflächen entsprechend klein. Die Lesbarkeit auf dem physischen Samsung-Tablet wurde nicht gemessen.
- Zwölf Playlists konnten im Dialog ausgewählt werden. Bei zwölf markierten Einträgen blieb die dreizehnte Playlist nach Antippen abgewählt.
- Im Emulator startete die Wiedergabe über das Cover. Ein Tipp auf den Namen öffnete die Titelliste.
- Das signierte Komplettpaket ließ sich als Update installieren. 23 automatisierte Tests im Komplettpaket und 9 in der Widget-Edition bestanden, einschließlich der neuen Tests zur Rasterberechnung.
