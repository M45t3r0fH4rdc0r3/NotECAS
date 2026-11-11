# Verifizierter Teststand / Verified test status

Stand / Last updated: **2026-11-11**

## Deutsch

### Automatisiert

- 83 Unit-, Robolectric- und Compose-Tests: erfolgreich
- Android-Lint: 0 Fehler; 1 unkritischer Aktualisierungshinweis für Gradle 9.9.1
- Backup-Regeln für Android 8, Android 9–11 und Android 12+ automatisiert geprüft: Cloud-Sicherung ausgeschlossen, lokale Datenbank ab Android 9 für direkte Geräteübertragung freigegeben
- GitHub Actions: letzter veröffentlichter Stand erfolgreich; neue Änderungen werden zusätzlich im Pull Request geprüft
- CodeQL: letzter veröffentlichter Stand für GitHub Actions sowie Java/Kotlin erfolgreich analysiert

### Physisch bestätigt

Durch Wolfgang Lindner auf einem realen Gerät bestätigt:

- Android 17
- Samsung One UI 9
- Samsung Galaxy S26 Ultra
- App startet und ist bedienbar
- ältere reale ECAS-Nutzerdatenbank erfolgreich importiert

Nicht als vollständig geprüft gelten damit automatisch alle historischen ECAS-Versionen, sämtliche Sonderfälle, ein vollständiger Import-/Export-Roundtrip oder eine reale Übertragung mit Samsung Smart Switch.

---

## English

### Automated

- 83 unit, Robolectric, and Compose tests: passed
- Android Lint: 0 errors; 1 non-critical Gradle 9.9.1 update notice
- backup rules verified automatically for Android 8, Android 9–11, and Android 12+: cloud backup excluded and local database enabled for direct device transfer starting with Android 9
- GitHub Actions: latest published revision passed; new changes are additionally verified in the pull request
- CodeQL: latest published revision passed for GitHub Actions and Java/Kotlin

### Physically confirmed

Confirmed by Wolfgang Lindner on a real device:

- Android 17
- Samsung One UI 9
- Samsung Galaxy S26 Ultra
- app starts and is usable
- an older real-world ECAS user database was imported successfully

This does not automatically certify every historical ECAS version, every edge case, a complete import/export round trip, or an actual Samsung Smart Switch transfer.
