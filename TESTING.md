# Verifizierter Teststand / Verified test status

Stand / Last updated: **2026-11-04**

## Deutsch

### Automatisiert

- 64 Unit-, Robolectric- und Compose-Tests: erfolgreich
- Android-Lint: keine Beanstandungen
- GitHub Actions: Tests, Lint und Debug-Build erfolgreich
- CodeQL: GitHub Actions sowie Java/Kotlin erfolgreich analysiert

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

- 64 unit, Robolectric, and Compose tests: passed
- Android Lint: no issues
- GitHub Actions: tests, lint, and debug build passed
- CodeQL: GitHub Actions and Java/Kotlin analysis passed

### Physically confirmed

Confirmed by Wolfgang Lindner on a real device:

- Android 17
- Samsung One UI 9
- Samsung Galaxy S26 Ultra
- app starts and is usable
- an older real-world ECAS user database was imported successfully

This does not automatically certify every historical ECAS version, every edge case, or a complete import/export round trip.
