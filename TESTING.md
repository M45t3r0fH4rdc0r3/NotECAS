# Verifizierter Teststand / Verified test status

Stand / Last updated: **2026-11-07**

## Deutsch

### Automatisiert

- 76 Unit-, Robolectric- und Compose-Tests: erfolgreich
- Android-Lint: 0 Fehler; 1 unkritischer Aktualisierungshinweis für Gradle 9.9
- GitHub Actions: letzter veröffentlichter Stand erfolgreich; neue Änderungen werden zusätzlich im Pull Request geprüft
- CodeQL: letzter veröffentlichter Stand für GitHub Actions sowie Java/Kotlin erfolgreich analysiert

### Physisch bestätigt

Durch Wolfgang Lindner auf einem realen Gerät bestätigt:

- Android 17
- Samsung One UI 9
- Samsung Galaxy S26 Ultra
- App startet und ist bedienbar
- ältere reale ECAS-Nutzerdatenbank erfolgreich importiert

Nicht als vollständig geprüft gelten damit automatisch alle historischen ECAS-Versionen, sämtliche Sonderfälle oder ein vollständiger Import-/Export-Roundtrip.

---

## English

### Automated

- 76 unit, Robolectric, and Compose tests: passed
- Android Lint: 0 errors; 1 non-critical Gradle 9.9 update notice
- GitHub Actions: latest published revision passed; new changes are additionally verified in the pull request
- CodeQL: latest published revision passed for GitHub Actions and Java/Kotlin

### Physically confirmed

Confirmed by Wolfgang Lindner on a real device:

- Android 17
- Samsung One UI 9
- Samsung Galaxy S26 Ultra
- app starts and is usable
- an older real-world ECAS user database was imported successfully

This does not automatically certify every historical ECAS version, every edge case, or a complete import/export round trip.
