# NotECAS

[![CI](https://github.com/M45t3r0fH4rdc0r3/NotECAS/actions/workflows/ci.yml/badge.svg)](https://github.com/M45t3r0fH4rdc0r3/NotECAS/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Android 8+](https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android)](https://developer.android.com/about/versions/oreo)

[Deutsch](#deutsch) · [English](#english)

---

<a id="deutsch"></a>
## Deutsch

**Moderne, lokale Verbrauchserfassung für Android mit Import und Export historischer ECAS-Daten.**

NotECAS verwaltet Zählerstände für Strom, Gas, Wasser, Produktion, Sensoren und weitere Verbrauchswerte. Die App wurde eigenständig neu entwickelt und setzt auf Kotlin, Jetpack Compose und Material 3.

> **Entwicklungsstand:** Version 0.1.9 ist eine Vorabversion. Die App läuft erfolgreich auf einem Samsung Galaxy S26 Ultra mit Android 17 und One UI 9; außerdem wurde eine ältere ECAS-Nutzerdatenbank erfolgreich importiert. Vor produktiver Nutzung trotzdem immer ein aktuelles Datenbank-Backup anlegen.

### Funktionen

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

### ECAS-Interoperabilität

NotECAS zielt auf die Übernahme historischer Datenbanken mit ECAS-Schemaversionen 1 bis 17. IDs und für virtuelle Zähler relevante Felder werden beim Import erhalten. Eingaben werden vor dem Austausch des aktuellen Datenbestands validiert.

Die technische Interoperabilitätsbeschreibung steht in [`analysis/ECAS_COMPATIBILITY.md`](analysis/ECAS_COMPATIBILITY.md).

**Wichtig:** „Kompatibel“ bezeichnet das angestrebte Datenformat. Eine vollständige Zertifizierung mit allen früheren ECAS-Versionen existiert nicht.

### Datenschutz und Berechtigungen

- keine Android-`INTERNET`-Berechtigung
- lokale Speicherung der Verbrauchsdaten
- Import und Export nur über den Android-Dateidialog
- Kamera-Berechtigung ausschließlich zur Steuerung der Taschenlampe
- automatische Cloud-Sicherung und Geräteübertragung der App-Daten deaktiviert
- Details: [`PRIVACY.md`](PRIVACY.md)

### Systemvoraussetzungen

- Android 8.0 / API 26 oder neuer
- Zielplattform: Android 17 / API 37
- Paketname: `de.mohcomputer.notecas`

### Installation

Der Quellcode ist öffentlich. Eine allgemein empfohlene APK wird erst als GitHub Release veröffentlicht, sobald ein dauerhafter Release-Schlüssel eingerichtet und die Geräteprüfung abgeschlossen ist. Debug-APKs sind nicht für eine dauerhafte Installation oder Aktualisierung gedacht.

### Lokal bauen und prüfen

Benötigt werden JDK 21 und ein Android SDK mit Plattform 37.

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

Die Debug-APK entsteht unter:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Der aktuelle Stand enthält 69 automatisierte Tests. GitHub Actions führt Tests, Android-Lint und den Debug-Build bei jedem Push und Pull Request erneut aus.

Dokumentierter Teststand: [`TESTING.md`](TESTING.md)

### Mitwirken

Fehlerberichte und Verbesserungsvorschläge sind willkommen. Bitte keine privaten Verbrauchsdaten oder unveränderten Datenbankexporte öffentlich hochladen.

- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`SECURITY.md`](SECURITY.md)
- [Issues](https://github.com/M45t3r0fH4rdc0r3/NotECAS/issues)

### Projektstatus

- [x] moderner Compose-/Material-3-Unterbau
- [x] Zähler-, Messwert- und Kostenverwaltung
- [x] historische Jahresanalyse
- [x] Datenbank- und CSV-Transfer
- [x] 69 automatisierte Tests und Android-Lint
- [x] erfolgreicher Import einer älteren realen ECAS-Nutzerdatenbank
- [x] physische Geräteprüfung auf Samsung Galaxy S26 Ultra · Android 17 · One UI 9
- [ ] signierter Release-Build und reproduzierbarer Release-Prozess

### Urheberrecht, Lizenz und Abgrenzung

Copyright © 2026 Wolfgang Lindner.

NotECAS ist unter der **GNU General Public License Version 3 oder später** veröffentlicht. Siehe [`LICENSE`](LICENSE). Drittanbieterkomponenten behalten ihre jeweiligen Lizenzen; eine Übersicht steht in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

NotECAS ist eine unabhängige Neuimplementierung zur Interoperabilität mit historischen Datenformaten. Das Projekt ist weder mit dem ursprünglichen ECAS-Entwickler verbunden noch von ihm unterstützt. Es enthält keinen ursprünglichen ECAS-Quellcode, keine ursprüngliche APK und keine ursprünglichen Grafiken. „ECAS“ kann ein Name oder Kennzeichen Dritter sein und wird hier ausschließlich zur Beschreibung der Datenkompatibilität verwendet.

**Programmiert von Wolfgang Lindner.**

---

<a id="english"></a>
## English

**A modern, local-first Android consumption tracker with import and export support for historical ECAS data.**

NotECAS tracks meter readings for electricity, gas, water, production, sensors, and other consumption data. It is an independently developed application built with Kotlin, Jetpack Compose, and Material 3.

> **Development status:** Version 0.1.9 is a pre-release. The app has been run successfully on a Samsung Galaxy S26 Ultra with Android 17 and One UI 9, and an older real-world ECAS user database was imported successfully. Always keep a current database backup when working with important data.

### Features

- cumulative, inverted, bidirectional, production, sensor, and virtual meters
- unit prices, annual fixed costs, scaling, resets, and rollover handling
- readings with timestamps, comments, and color markers
- historical yearly analysis, monthly values, and previous-year comparison
- trend, bar-chart, and table views
- smoothing affects only the displayed trend line; raw readings, consumption, and costs remain unchanged
- import and export of ECAS-compatible SQLite databases
- per-meter CSV import and export
- local data storage without Android internet permission
- optional flashlight while reading a physical meter

### ECAS interoperability

NotECAS aims to migrate historical databases using ECAS schema versions 1 through 17. IDs and fields required by virtual meters are preserved during import. Input data is validated before the current NotECAS dataset is replaced.

The technical interoperability notes are available in [`analysis/ECAS_COMPATIBILITY.md`](analysis/ECAS_COMPATIBILITY.md).

**Important:** “Compatible” describes the targeted data format. The project has not been certified against every historical ECAS version or dataset.

### Privacy and permissions

- no Android `INTERNET` permission
- consumption data is stored locally
- files are imported or exported only through Android's system file picker
- camera permission is used exclusively to control the flashlight
- automatic cloud backup and device transfer of app data are disabled
- detailed German privacy statement: [`PRIVACY.md`](PRIVACY.md)

### Requirements

- Android 8.0 / API 26 or newer
- target platform: Android 17 / API 37
- package name: `de.mohcomputer.notecas`

### Installation

The source code is public. A generally recommended APK will be published as a GitHub Release only after a permanent release signing key has been configured and physical device testing has been completed. Debug APKs are not intended for long-term installation or updates.

### Build and verify locally

JDK 21 and an Android SDK containing platform 37 are required.

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The current codebase contains 69 automated tests. GitHub Actions reruns the tests, Android Lint, and the debug build for every push and pull request.

Documented test status: [`TESTING.md`](TESTING.md)

### Contributing

Bug reports and improvements are welcome. Never upload private consumption data or an unmodified personal database export to a public issue.

- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`SECURITY.md`](SECURITY.md)
- [Issues](https://github.com/M45t3r0fH4rdc0r3/NotECAS/issues)

### Project status

- [x] modern Compose and Material 3 foundation
- [x] meter, reading, and cost management
- [x] historical yearly analysis
- [x] SQLite database and CSV transfer
- [x] 69 automated tests and Android Lint
- [x] successful import of an older real-world ECAS user database
- [x] physical device testing on Samsung Galaxy S26 Ultra · Android 17 · One UI 9
- [ ] signed release build and reproducible release process

### Copyright, license, and non-affiliation

Copyright © 2026 Wolfgang Lindner.

NotECAS is licensed under the **GNU General Public License, version 3 or later**. See [`LICENSE`](LICENSE). Third-party components remain subject to their respective licenses; see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

NotECAS is an independent reimplementation intended to interoperate with historical data formats. It is neither affiliated with nor endorsed by the original ECAS developer. The repository contains no original ECAS source code, APK, or artwork. “ECAS” may be a name or trademark of a third party and is used solely to describe data interoperability.

**Programmed by Wolfgang Lindner.**
