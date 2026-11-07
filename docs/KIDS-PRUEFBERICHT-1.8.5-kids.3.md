# Prüfung von Fossify Music Player Kids 1.8.5-kids.3

- Release-Build erfolgreich. 21 automatisierte Tests erfolgreich (14 Stopptanz, 6 Cover, 1 Playlist-Suche).
- Das Update wurde im virtuellen Galaxy Tab A8 mit Android 14 über die signierte Kids-Version 1.8.5-kids.2 installiert. Die App-ID org.fossify.musicplayer.kids, das Datum der Erstinstallation und das eingerichtete Kachel-Widget blieben erhalten. Der Versionscode stieg von 19 auf 20. SHA-256 des Signaturzertifikats 6e7dcab34eb47c90067193b60741bfb7b56590a161b3ae1782cb23e7f2023ae9.
- Beim ersten Test konnte die Widget-Aktivität schließen, bevor die Wiedergabe bereit war. Sie wartet jetzt auf die Vorbereitung des angeforderten Titels. Nach Installation der korrigierten APK startete das Listen-Widget die Wiedergabe, der Launcher blieb im Vordergrund, Android meldete PLAYING.
- Kachel und Liste im Launcher geprüft. Beim Kachel-Widget lag das erste Cover 12 Bildpunkte unter der Oberkante, beim Listen-Widget 9 Bildpunkte. Die Inhalte wurden gegenüber dem abgerundeten Hintergrund eingerückt.
- Im Android-Medienfeld wurden Titel, Playlist und Interpret angezeigt, Android meldete ein Vorschaubild. Vor, zurück, Pause und Öffnen der Titelansicht wurden im Emulator geprüft. Der Sperrbildschirm von Samsung One UI und die Lautsprecher des echten Tablets wurden nicht getestet.
- Die drei Einzelvarianten sind die bereits veröffentlichten APKs aus 1.8.5-kids.2. Ihre SHA-256-Prüfsummen wurden mit dem vorherigen Release-Ordner verglichen.
