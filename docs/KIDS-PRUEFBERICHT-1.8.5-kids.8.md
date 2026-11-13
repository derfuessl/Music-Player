# Prüfung für 1.8.5-kids.8

## Verhalten

Alle vier Ausgaben blenden das Löschen von Musikdateien standardmäßig aus. Die Einstellung **Löschen von Musikdateien erlauben** gibt die bisherigen Löschaktionen frei. **Aus Playlist entfernen** betrifft nur die Zuordnung. Das Entfernen einer ganzen Playlist löscht bei aktivem Schutz ausschließlich die Playlist.

Ein Medienscan entfernt nicht erreichbare Dateien aus der aktuell verfügbaren Bibliothek und bewahrt die Einträge in den Playlists einschließlich ihrer Position. Bei der Wiederkehr ordnet der Player die Aufnahme anhand ihres vollständigen Pfades zu. Ein fehlender Eintrag lässt sich weiterhin von Hand aus der Playlist entfernen. Ein anderer Speicherort oder Dateiname zählt als anderer Pfad.

Im Stopptanz zeigen **Zurück** und **Weiter** den ausgewählten Titel an. Während einer Tanzpause wird nur die Auswahl vorgemerkt. Der Player übernimmt sie am Ende des bereits laufenden Pausenintervalls. Der nächste Titel richtet sich nach der aktuellen Warteschlange einschließlich ihrer Zufallsreihenfolge.

Liste und große Liste verwenden im Widget feste Cover (40 bzw. 60 dp) und Zeilen mit einer Mindesthöhe von 42 bzw. 64 dp. Die Systemschrift kann für die zwei Textzeilen mehr Platz benötigen. Beim Ändern der Widget-Höhe wächst nur die Zahl der sichtbaren Zeilen. Das kompakte Einzelfeld verwendet ein festes Icon mit eigener, größerer Tippfläche.

## Lasttest

Die Datenbankabfrage zum Erkennen fehlender Titel wurde auf dem Entwicklungsrechner mit 3.000 Bibliothekstiteln und 22.500 Playlist-Zuordnungen getestet. Der Median aus sieben Läufen betrug bei allen Quellen online 10,14 ms und bei einem fehlenden Titel mit acht Zuordnungen 10,21 ms. Gemessen wurde die einzelne SQLite-Abfrage im Arbeitsspeicher.

## Bedienprüfung im Galaxy-Tab-A8-Emulator mit Android 14

- Der Dateilöschbefehl war standardmäßig verborgen. Nach ausdrücklicher Freigabe war er sichtbar. Nach erneutem Sperren blieb nur das Entfernen der Zuordnung verfügbar. Auch der Dialog zum Entfernen einer ganzen Playlist blendete die Option für die Musikdateien aus.
- Eine gesicherte Test-MP3 wurde aus dem emulierten Speicher und Android-Medienindex entfernt. Nach einem Scan blieb sie in der Playlist sichtbar. Am ursprünglichen Pfad wiederhergestellt, erhielt sie die Medien-ID 1000000041 statt 1000000040. Die Prüfsumme stimmte, und die Wiedergabe aus der Playlist funktionierte.
- Der erste Stopptanz-Entwurf suchte den nächsten Titel bereits während der Pause. Dabei konnte der Player den laufenden Tanz beenden. Im geprüften Update merkt der Dienst die Auswahl vor. Während 32 Sekunden Pause zeigte der Test nach Weiter, Zurück und Weiter die drei passenden Titelzustände. Android meldete nach 32,26 Sekunden die Wiedergabe des zuletzt gewählten Titels.
- Sechs Widgets wurden bei einer Bildschirmdichte von 1,5 getestet. Die Cover beider Listen blieben nach Größenänderung 60 bzw. 90 Pixel breit und hoch. Bei gleicher Covergröße sank die sichtbare Anzahl in der normalen Liste von sechs auf zwei, in der großen Liste von drei auf eins. Das kompakte Einzelfeld zeigte ein 42 × 42 Pixel großes Bild.
- Automatisierte Tests je Ausgabe: Komplettpaket 29, Stopptanz 17, Bilder mit Widgets 14, Bilder 8. Alle bestanden. Die Bedienprüfung erfolgte mit dem Komplettpaket. Eine physische SD-Karte und das echte Samsung-Tablet wurden nicht verwendet.
