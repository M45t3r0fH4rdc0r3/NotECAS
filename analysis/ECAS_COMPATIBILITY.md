# ECAS-Dateninteroperabilität

Diese Beschreibung dokumentiert ausschließlich die für den Import und Export historischer Nutzerdaten erforderlichen Formate und Verhaltensregeln. NotECAS ist eine unabhängige Neuimplementierung und enthält keine ursprünglichen Programmdateien oder Grafiken.

## Datenbank

- SQLite-Datenbank mit den Tabellen `meter` und `reading`
- unterstütztes Ziel der Migration: Schemaversionen 1 bis 17
- `PRAGMA user_version=17` beim kompatiblen Export
- Primärschlüssel und Zähler-IDs bleiben erhalten, weil virtuelle Zähler darauf verweisen
- ältere Datenbanken können Spalten ohne feste SQLite-Typdeklaration enthalten; Werte werden daher anhand ihres Inhalts gelesen

### Tabelle `meter`

| Feld | Bedeutung |
|---|---|
| `_id` | stabile Zähler-ID |
| `name`, `units`, `comment` | Anzeigeinformationen |
| `vsf`, `tsf` | Glättungsparameter im Bereich 0 bis 100 |
| `cost`, `fcost` | Preis pro Einheit und jährliche Fixkosten |
| `invert` | invertierter beziehungsweise Prepaid-Zähler |
| `vmsetup` | Quellen und Faktoren eines virtuellen Zählers |
| `type` | kumulativer Zähler oder Sensor |
| `currency` | optionale Währung |
| `scaling`, `phyunits` | Skalierung und physische Einheit |
| `bidir`, `prod` | bidirektionaler beziehungsweise Produktionszähler |

### Tabelle `reading`

| Feld | Bedeutung |
|---|---|
| `_id`, `meterid` | Messwert-ID und zugehörige Zähler-ID |
| `date` | lokaler Zeitstempel `yyyy-MM-dd'T'HH:mm:ss` ohne Zeitzone |
| `value`, `comment` | Rohwert und optionale Notiz |
| `color` | Farbcode 0 bis 4 |
| `cost` | kumulierter Kostenwert virtueller Messwerte |
| `vcor`, `ccor` | Korrekturwerte virtueller Messwerte |
| `first` | Beginn beziehungsweise Reset; der Übergang zu diesem Wert verbraucht null |

Für `reading` gilt die Eindeutigkeit aus Zähler-ID und Zeitstempel. Der kompatible Export legt außerdem einen Index über `meterid` an.

## Verbrauch und Kosten

Für Rohwerte `alt`, `neu` und Skalierung `s` gilt:

- skalierte Werte: `alt × s` und `neu × s`
- normaler Verbrauch: `neuSkaliert - altSkaliert`
- invertierter Zähler: `altSkaliert - neuSkaliert`
- trägt der neuere Messwert die Reset-Markierung, ist der Verbrauch dieses Übergangs null
- negative Differenzen normaler kumulativer Zähler werden auf null begrenzt
- Sensoren, virtuelle und bidirektionale Zähler dürfen negative Differenzen behalten
- bei einem bidirektionalen Dezimalüberlauf wird die Differenz anhand der zur Stellenzahl passenden Zehnerpotenz korrigiert
- variable Kosten: `Verbrauch × PreisProEinheit`
- anteilige Fixkosten: `Jahresfixkosten × verstricheneZeit / 365 Tage`

Kalenderauswertungen verwenden reale lokale Monats- und Jahresgrenzen. Werte an einer Periodengrenze werden linear zwischen benachbarten kumulativen Messwerten interpoliert.

Sensorstatistiken sind nicht kumulativ. Für die historische Kompatibilitätsansicht wird der Mittelpunkt aus Minimum und Maximum verwendet; die Spanne ist `Maximum - Minimum`.

## Virtuelle Zähler

`vmsetup` enthält abwechselnd Quellen-ID und Faktor. Ungültige IDs und Faktoren von null werden ignoriert; Zyklen sind unzulässig.

- Wertbeitrag: Summe aus `Quellenverbrauch × Faktor`
- Kostenbeitrag: Summe aus `Quellenverbrauch × Quellenpreis × Vorzeichen(Faktor)`
- unterschiedliche Messzeitpunkte werden linear interpoliert
- vorhandene Korrekturwerte werden beim verlustarmen Datentransfer erhalten

## CSV

- UTF-8, Komma als Trennzeichen, CRLF, keine Kopfzeile
- Zeilenformat: `YYYY-MM-DD,HH:MM:SS,Wert,Kommentar`
- Kommentar optional
- Dezimalpunkt im kompatiblen Export
- CSV-Anführungszeichen und verdoppelte eingebettete Anführungszeichen werden unterstützt
- doppelte Kombinationen aus Zähler und Zeitpunkt ersetzen den bisherigen Wert

NotECAS behält intern `Double`-Genauigkeit. Ein ECAS-kompatibler Export darf bei ausdrücklich gewähltem Kompatibilitätsmodus die historische Float32-Rundung nachbilden.

## Sicherheitsregeln beim Import

1. Ausgewählte Datei zunächst in einen temporären Bereich kopieren.
2. SQLite-Signatur und Integrität prüfen.
3. Schemaversion, Tabellen, Spalten und IDs validieren.
4. Neuere unbekannte Versionen standardmäßig ablehnen.
5. Zahlen, Zeitstempel, Wertebereiche, Fremdschlüssel und virtuelle Zyklen prüfen.
6. Import vollständig in einer Transaktion durchführen.
7. Bestehende NotECAS-Daten bis zum erfolgreichen Abschluss unangetastet lassen.
8. Ergebnis mit importierten, ersetzten und übersprungenen Datensätzen melden.

## Funktionsumfang

- kumulative, Sensor-, Produktions-, invertierte, bidirektionale, skalierte und virtuelle Zähler
- Messwerte mit Zeitstempel, Kommentar, Farbe und Reset-Markierung
- Verbrauch, Kosten und normalisierte Raten
- Trend-, Kalender- und Jahresvergleichsansichten
- variable und jährliche Fixkosten
- CSV-Transfer pro Zähler
- vollständiger SQLite-Datenbanktransfer

Diese Spezifikation beschreibt das angestrebte interoperable Verhalten. Sie ist keine Zertifizierung sämtlicher historischer App-Versionen oder Datenbestände.
