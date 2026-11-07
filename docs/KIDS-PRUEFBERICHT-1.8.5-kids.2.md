# Prüfung der Kids-Editionen 1.8.5-kids.2 (Versionscode 19)

## Befunde und Korrekturen

| Bisheriges Verhalten | Korrektur und Nachweis |
| --- | --- |
| Die Widget-Einrichtung legte für jede Playlist sofort ein Kontrollkästchen an. Filtern war nicht möglich. | Liste mit wiederverwendeten Zeilen, Namenssuche auf einem Hintergrundthread, vorbereitete Suchtexte. Im Emulator mit 50.001 Playlists wurde der letzte Titel gefunden. Auswahl gespeichert, wieder geöffnet, nach einem anderen Begriff gesucht und Abbrechen geprüft. |
| Jeder Widget-Refresh lud und sortierte alle Playlists, selbst wenn nur eine ausgewählt war. | Die Datenbank liefert maximal sechs Zeilen. Bei manueller Auswahl fragt das Widget direkt nach den gespeicherten IDs. Kontrolle mit beiden Widget-Arten. |
| Lange Playlists erzeugten lange Cache-Schlüssel aus sämtlichen Dateipfaden. Ein Ergebnis ohne Bild wurde mit einem Byte berechnet. | Schlüssel aus einem inkrementellen SHA-256-Digest. Schlüssel und Einträge ohne Bild erhalten ein separates Speicherbudget innerhalb der Cache-Größenberechnung. Vier unterschiedliche MP3-Cover, manuelle Wahl und Zurücksetzen im Emulator geprüft. |
| Beim Test der neuen Suche blieb über einzelnen Treffern der Platz des ausgeblendeten Einstellungsteils frei. | Ein umschließender Container lässt den Listenkopf vollständig zusammenfallen. Im abschließenden UI-Test stand der Treffer unmittelbar unter dem Suchfeld. |

Die Auswahl bleibt an Datenbank-IDs gebunden. Filtern oder wiederverwendete Bildschirmzeilen ändern keine gespeicherten Haken. Der automatische Modus verwendet weiterhin höchstens sechs Playlists. Die Ersteinrichtung liest Namen und IDs einmal ein. Der Aufwand für den Suchindex wächst mit der Bibliothek.

## Reproduzierte Prüfungen

- **Belastung:** Isolierte App im Tablet-Emulator mit 50.000 erzeugten Playlists und der vorhandenen Standardplaylist. Zu Beginn waren nur rund 25 Playlist-Zeilen im sichtbaren UI-Baum. Nach „50000“ blieb der korrekte Treffer. Bei einer erfolglosen Suche blieb die zuvor gewählte ID beim Speichern erhalten. Ein siebter Haken wurde abgewiesen. Die getrennte Test-App mit ihren Daten wurde wieder entfernt.
- **Automatisierte Tests:** Komplettpaket 21, Playlist-Bilder mit Widgets 7, Playlist-Bilder 6, Stopptanz 14. Alle bestanden. Gemeinsame Tests werden in mehreren Editionen ausgeführt. Der Suchtest umfasst 50.000 Einträge, gleichnamige Playlists mit unterschiedlichen IDs, Groß-/Kleinschreibung mit Umlauten, Leerzeichen, wörtliche Sonderzeichen und leere Trefferlisten.
- **Wiedergabe und Stopptanz:** Test mit zwei Sekunden Musik und zwei Sekunden Pause. Android meldete für denselben Titel PLAYING, PAUSED und erneut PLAYING. Während der Pause blieb die gemeldete Position bei 13.596 ms. Nach Stop blieb der Player über die Pausendauer hinaus pausiert. Unit-Tests prüfen zusätzlich unter anderem veraltete Timer, Positionswechsel, ungültige Zeiten und nicht verfügbare Wiedergabe.
- **Cover und Widgets:** Fünf Test-MP3s mit vier verschiedenen Covern. Vier unterschiedliche Bildquadranten, ein einzelnes manuell gewähltes blaues Cover und anschließend pixelgleich wiederhergestellte Collage an den vier Prüfpunkten. In der Widget-Liste sowie im signierten Kachel-Widget startete das Cover die acht Titel der gewählten Playlist. Der Name öffnete die Titelansicht.
- **Auslieferung:** Alle vier Release-APKs gebaut, Signaturen und erwartete Funktionen im Paket geprüft. Jede Variante im Emulator installiert und gestartet. Das Komplettpaket aktualisierte Version 18 auf 19 mit identischem Erstinstallationszeitpunkt. Der Launcher zeigte das Kachel-Widget der signierten Version; die Suche und beide Tippaktionen funktionierten.

## Grenzen

Die Gerätetests liefen auf dem Tablet-Emulator. Das physische Galaxy Tab A8, das Verhalten bei einem echten Anruf und eine feste Antwortzeit auf Samsung-Hardware wurden nicht getestet. Die Zahl von 50.001 beschreibt die geprüfte Bibliothek, keine zugesicherte Obergrenze oder Laufzeit.
