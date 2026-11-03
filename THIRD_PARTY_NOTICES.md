# Hinweise zu Drittanbieterkomponenten

NotECAS verwendet Open-Source-Komponenten. Deren Urheberrechte und Lizenzbedingungen bleiben unberührt.

## Laufzeit- und Buildkomponenten

- AndroidX Core, Activity, Lifecycle, Navigation, Test und Compose – Apache License 2.0
- Jetpack Compose Material 3 und Material Icons – Apache License 2.0
- Kotlin und Kotlin Gradle Plugin – Apache License 2.0
- Android Gradle Plugin – Apache License 2.0
- Gradle – Apache License 2.0

## Testkomponenten

- JUnit 4 – Eclipse Public License 1.0
- Robolectric – MIT License
- Espresso / AndroidX Test – Apache License 2.0

Die vollständigen Abhängigkeitsversionen stehen in `build.gradle.kts` und `app/build.gradle.kts` beziehungsweise im von Gradle aufgelösten Abhängigkeitsgraphen:

```bash
./gradlew :app:dependencies
```

Lizenztexte:

- Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- Eclipse Public License 1.0: https://www.eclipse.org/legal/epl-v10.html
- MIT License: https://opensource.org/license/mit

Diese Übersicht ersetzt nicht die Lizenztexte der jeweiligen Projekte. Bei einer Release-Erstellung müssen die Hinweise der tatsächlich enthaltenen transitiven Abhängigkeiten erneut geprüft werden.
