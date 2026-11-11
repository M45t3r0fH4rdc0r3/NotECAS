# Datenschutz bei NotECAS

Stand: 11. November 2026

NotECAS ist eine lokal arbeitende Android-App zur Erfassung und Auswertung von Zählerständen.

## Verarbeitete Daten

Je nach Nutzung speichert die App lokal:

- Bezeichnungen und Eigenschaften von Zählern
- Messzeitpunkte und Messwerte
- Kommentare, Kosten- und Währungsangaben
- Einstellungen für Darstellung und Berechnung

Diese Daten können Rückschlüsse auf Haushalts- oder Nutzungsgewohnheiten zulassen und sollten entsprechend geschützt werden.

## Datenübertragung

NotECAS besitzt keine Android-`INTERNET`-Berechtigung und überträgt keine Zählerdaten an den Entwickler oder einen Analysedienst. Es werden keine Konten, Tracker oder Werbenetzwerke verwendet.

Import und Export erfolgen nur nach einer Nutzeraktion über den Android-Dateidialog. Der gewählte Speicheranbieter kann eigenen Datenschutzbedingungen unterliegen.

## Berechtigungen

- **Kamera:** ausschließlich zum Ein- und Ausschalten der Taschenlampe beim Ablesen. Es werden keine Fotos oder Videos aufgenommen oder gespeichert.
- **PayPal-App-Erkennung:** Android darf prüfen, ob die PayPal-App installiert ist, damit der freiwillige Spendenhinweis keinen Browser öffnet. NotECAS verarbeitet keine Zahlungsdaten.

## Sicherung und Löschung

Automatische Cloud-Backups bleiben für NotECAS vollständig ausgeschlossen. Ab Android 9 darf das Betriebssystem bei einer vom Nutzer gestarteten direkten Android-Geräteübertragung ausschließlich die lokale NotECAS-Datenbank auf das neue Gerät übernehmen. Andere interne Dateien und Einstellungen werden nicht dafür freigegeben. Unter Android 8 bleibt auch die direkte Übertragung gesperrt, weil sich dort Cloud-Backup und Geräteübertragung nicht sicher getrennt freigeben lassen. Ob eine konkrete Herstellersoftware wie Samsung Smart Switch diese Android-Regeln unterstützt und die Daten tatsächlich übernimmt, hängt von Gerät und Softwareversion ab; ein manueller Datenbankexport bleibt deshalb die verlässlichste zusätzliche Sicherung.

Exporte werden nur am vom Nutzer ausgewählten Ziel abgelegt.

App-Daten können über die Android-Systemeinstellungen oder durch Deinstallation gelöscht werden. Selbst exportierte Dateien müssen am jeweiligen Speicherort separat gelöscht werden.

## Kontakt

Fragen zum Datenschutz: **service@moh-computer.de**
