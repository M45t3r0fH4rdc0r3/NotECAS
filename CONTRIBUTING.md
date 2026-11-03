# Zu NotECAS beitragen

Danke für dein Interesse. Änderungen an Verbrauchsberechnungen und Datenmigration können echte Nutzerdaten betreffen; deshalb gilt hier lieber einmal sauber testen als dreimal halb.

## Fehler melden

- vorhandene Issues zuerst durchsuchen
- App-Version, Android-Version und Gerätemodell angeben
- Schritte zur Reproduktion sowie erwartetes und tatsächliches Verhalten beschreiben
- niemals private Zählerstände, Adressen oder unveränderte Datenbankexporte öffentlich anhängen
- für Kompatibilitätsfehler möglichst einen anonymisierten Minimaldatensatz anbieten

## Entwicklung

Voraussetzungen:

- JDK 21
- Android SDK mit Plattform 37
- Git

Prüfung vor einem Pull Request:

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

## Pull Requests

1. Ein Issue für größere Änderungen anlegen.
2. Einen klar benannten Branch verwenden.
3. Änderungen klein und nachvollziehbar halten.
4. Berechnungs-, Import- und Exportänderungen mit Tests absichern.
5. Keine ursprünglichen ECAS-Dateien, dekompilierten Inhalte, privaten Datenbanken, Schlüssel oder Zugangsdaten einreichen.
6. Beschreiben, welche Datenpfade betroffen sind und wie Rückwärtskompatibilität geprüft wurde.

Mit einem Beitrag bestätigst du, dass du den Beitrag selbst erstellen oder unter einer kompatiblen Lizenz einreichen darfst. Beiträge werden unter GPLv3 oder später veröffentlicht.
