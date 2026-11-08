# Prüfung der Playlist-Widgets · 1.8.5-kids.5

Das Kids-Komplettpaket und die Widget-Edition ohne Stopptanz verwenden Versionscode 22, ihre bisherigen App-IDs und den bisherigen Signaturschlüssel.

## Darstellung und Auswahl

Ein Android-Testhost im virtuellen Galaxy Tab A8 (Android 14) hat sechs echte Widgets gleichzeitig dargestellt. Bei normaler Systemschrift haben Listenzeilen eine Mindesthöhe von 42 dp, Kacheln eine Breite von 76 dp und eine Mindesthöhe von 84 dp. Die Coverflächen messen 36 × 36 dp in der Liste und 40 × 40 dp in der Kachel. Die Namen verwenden 14 bzw. 12 sp und bis zu zwei Zeilen. Die nutzbaren Maße meldet der Launcher in dp. Seine tatsächliche Zahl der belegten Startbildschirmfelder steht der App nicht direkt zur Verfügung.

- Bei 96 × 84 dp waren zwei Listeneinträge oder eine Kachel vollständig sichtbar. Die größeren Gegenstücke zeigten bei gleicher Breite und doppelter Höhe vier Listeneinträge oder zwei Kacheln.
- Bei 280 dp Breite wurden zwei kleine Listen (je 252 dp Höhe) mit einer 504 dp hohen Liste und zwei Kachel-Widgets (je 168 dp Höhe) mit einem 336 dp hohen Kachel-Widget verglichen. Bei sechs plus sechs gegenüber zwölf Playlists stimmten Position und Größe aller zwölf Cover und Namen nach Abzug des horizontalen Vergleichsversatzes pixelgenau überein.
- Neun von zwölf: Mit eingeblendeter Überschrift erschienen im großen Kachel-Widget die ersten neun sortierten Playlists. Nach Speichern und Wiederöffnen blieben alle zwölf manuell ausgewählt. Nach Ausblenden der Überschrift erschienen wieder alle zwölf. Bei null freien Plätzen erschien ein Hinweis zum Vergrößern.
- Bei Systemschrift 150 % blieb mehr Platz für den Text reserviert. Das große Listen-Widget und das große Kachel-Widget zeigten jeweils neun Einträge. Nach Rückkehr zur Standardschrift waren wieder zwölf sichtbar.
- Beim ersten Größenwechsel konnte der Launcher eine veraltete Widget-Seite behalten. Beim Wechsel der Geometrie baut die App die Ansicht jetzt neu auf und aktualisiert die Daten. Verkleinern und erneutes Vergrößern wurden am laufenden Testhost mit denselben sechs Widget-IDs geprüft.
- Der Cover-Tipp startete die Wiedergabe; die Android-Mediensitzung meldete PLAYING. Ein Tipp auf den Playlist-Namen öffnete die Titelliste.

## Laufzeit, Build und Grenzen der Prüfung

Die Datenbankabfrage ist auf die Zahl der sichtbaren Einträge begrenzt. Nur deren Cover werden aufbereitet. Mehrfachanforderungen derselben Seite verwenden das bereits erzeugte Ergebnis bis zur nächsten Datenaktualisierung. Es wurde kein Zeitgewinn auf dem physischen Tablet gemessen.

Beide Release-Builds und die Signaturprüfung waren erfolgreich. 25 automatisierte Tests im Komplettpaket und 11 in der Widget-Edition bestanden. Das Komplettpaket wurde über die vorhandene Installation im Emulator installiert. Das Verhalten des Samsung-One-UI-Launchers auf dem physischen Tablet ist separat zu prüfen.
