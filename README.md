# NotECAS

[![CI](https://github.com/M45t3r0fH4rdc0r3/NotECAS/actions/workflows/ci.yml/badge.svg)](https://github.com/M45t3r0fH4rdc0r3/NotECAS/actions/workflows/ci.yml)
[![Lizenz: GPL v3](https://img.shields.io/badge/Lizenz-GPLv3-blue.svg)](LICENSE)
[![Android 8+](https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android)](https://developer.android.com/about/versions/oreo)

**Moderne, lokale Verbrauchserfassung für Android mit Import und Export historischer ECAS-Daten.**

NotECAS verwaltet Zählerstände für Strom, Gas, Wasser, Produktion, Sensoren und weitere Verbrauchswerte. Die App wurde eigenständig neu entwickelt und setzt auf Kotlin, Jetpack Compose und Material 3.

> **Entwicklungsstand:** Version 0.1.8 ist eine Vorabversion. Vor produktiver Nutzung immer ein aktuelles Datenbank-Backup anlegen. Ein Test mit einer realen anonymisierten ECAS-Datenbank und die Geräteabnahme auf Samsung One UI 9 stehen noch aus.

## Funktionen

- kumulative, invertierte, bidirektionale, Produktions-, Sensor- und virtuelle Zähler
- Kosten pro Einheit, jährliche Fixkosten, Skalierung, Reset und Überlaufbehandlung
- Messwerte mit Zeitstempel, Kommentar und Farbmarkierung
- Jahresauswertung, Monatswerte und Vergleich mit dem Vorjahr
- Trend-, Balken- und Tabellenansicht
- Glättung ausschließlich für die dargestellte Trendkurve; Rohwerte, Verbrauch und Kosten bleiben unverändert
- Import und Export von ECAS-kompatiblen SQLite-Datenbanken
- Import und Export von CSV-Dateien pro Zähler
- lokale Datenspeicherung ohne Internet-Berechtigung
- optionale Taschenlampe beim Ablesen eines Zählers

## ECAS-Interoperabilität

NotECAS zielt auf die Übernahme historischer Datenbanken mit ECAS-Schemaversionen 1 bis 17. IDs und für virtuelle Zähler relevante Felder werden beim Import erhalten. Eingaben werden vor dem Austausch des aktuellen Datenbestands validiert.

Die technische Interoperabilitätsbeschreibung steht in [`analysis/ECAS_COMPATIBILITY.md`](analysis/ECAS_COMPATIBILITY.md).

**Wichtig:** „Kompatibel“ bezeichnet das angestrebte Datenformat. Eine vollständige Zertifizierung mit allen früheren ECAS-Versionen existiert nicht.

## Datenschutz und Berechtigungen

- NotECAS besitzt keine `INTERNET`-Berechtigung.
- Verbrauchsdaten werden in der lokalen App-Datenbank gespeichert.
- Dateien werden nur über den Android-Dateidialog an vom Nutzer ausgewählten Orten importiert oder exportiert.
- Die Kamera-Berechtigung wird ausschließlich zur Steuerung der Taschenlampe verwendet.
- Details: [`PRIVACY.md`](PRIVACY.md)

## Systemvoraussetzungen

- Android 8.0 / API 26 oder neuer
- Zielplattform: Android 17 / API 37
- Paketname: `de.mohcomputer.notecas`

## Installation

Der Quellcode ist öffentlich. Eine allgemein empfohlene APK wird erst als GitHub Release veröffentlicht, sobald ein dauerhafter Release-Schlüssel eingerichtet und die Geräteprüfung abgeschlossen ist. Debug-APKs sind nicht für eine dauerhafte Installation oder Aktualisierung gedacht.

## Lokal bauen und prüfen

Benötigt werden JDK 21 und ein Android SDK mit Plattform 37.

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

Die Debug-APK entsteht unter:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Der aktuelle Stand enthält 64 automatisierte Tests. GitHub Actions führt Tests, Android-Lint und den Debug-Build bei jedem Push und Pull Request erneut aus.

## Mitwirken

Fehlerberichte und Verbesserungsvorschläge sind willkommen. Bitte keine privaten Verbrauchsdaten oder unveränderten Datenbankexporte öffentlich hochladen.

- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`SECURITY.md`](SECURITY.md)
- [Issues](https://github.com/M45t3r0fH4rdc0r3/NotECAS/issues)

## Projektstatus und offene Punkte

- [x] moderner Compose-/Material-3-Unterbau
- [x] Zähler-, Messwert- und Kostenverwaltung
- [x] historische Jahresanalyse
- [x] Datenbank- und CSV-Transfer
- [x] automatisierte Tests und Android-Lint
- [ ] Test mit einer anonymisierten realen ECAS-Nutzerdatenbank
- [ ] physische Geräteprüfung auf Samsung One UI 9
- [ ] signierter Release-Build und reproduzierbarer Release-Prozess

## Urheberrecht und Lizenz

Copyright © 2026 Wolfgang Lindner.

NotECAS ist unter der **GNU General Public License Version 3 oder später** veröffentlicht. Siehe [`LICENSE`](LICENSE). Drittanbieterkomponenten behalten ihre jeweiligen Lizenzen; eine Übersicht steht in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Abgrenzung

NotECAS ist eine unabhängige Neuimplementierung zur Interoperabilität mit historischen Datenformaten. Das Projekt ist weder mit dem ursprünglichen ECAS-Entwickler verbunden noch von ihm unterstützt. Es enthält keinen ursprünglichen ECAS-Quellcode, keine ursprüngliche APK und keine ursprünglichen Grafiken. „ECAS“ kann ein Name oder Kennzeichen Dritter sein und wird hier ausschließlich zur Beschreibung der Datenkompatibilität verwendet.

**Programmiert von Wolfgang Lindner.**
