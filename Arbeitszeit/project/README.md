# Arbeitszeit 1.3 – Code Assist

Android-App zur persönlichen Arbeitszeit- und Lohnerfassung, bewusst ohne externe UI-Bibliotheken.

## Neu in 1.3
- **Homescreen-Widget:** heutige Netto-Arbeitszeit (läuft live), Verdienst und Pause-Zeit sowie Tasten für Start/Weiter, Pause und Stop. Tippen auf die Anzeige öffnet die App. Der Verdienst im Widget wird bei jeder Aktion und, solange gearbeitet wird, ungefähr einmal pro Minute aktualisiert.
- **CSV-Export** eines Monats (Semikolon, deutsches Zahlenformat, UTF-8 mit BOM – öffnet direkt in Excel/LibreOffice).
- **Backup und Wiederherstellung** aller Daten als JSON-Datei (Datei speichern/laden über den Systemdialog).
- **Monatsauswahl** in der Übersicht (Monat vor/zurück), Tagesliste und Monatssumme beziehen sich auf den gewählten Monat.
- **Alle offenen Tage eines Monats als bezahlt markieren**, inklusive Zahlungsdatum pro Tag.
- **Notiz pro Tag** (Kunde, Projekt, Ort …), erscheint in der Übersicht und im CSV.
- **Rückgängig** nach dem Löschen eines Arbeitsblocks oder einer Pause (statt Rückfrage); Rückfrage vor „Stop“.
- **Zeiten über Mitternacht** lassen sich manuell eintragen (Ende vor Start): der Eintrag wird automatisch auf beide Tage aufgeteilt.
- **Dark Mode** (folgt der Systemeinstellung).
- Zurück-Taste führt jetzt Detail → Übersicht → Heute; Drehen des Geräts behält die aktuelle Ansicht.

## Behobene Fehler
- Im Detail-Screen des heutigen Tages wurden die Listen jede Sekunde neu aufgebaut, dadurch gingen Taps auf „Ändern“/„Löschen“ verloren. Jetzt werden nur die Zahlen aktualisiert.
- Der Standard-Stundenlohn wurde als `float` gespeichert (Rundungsartefakte wie 12,34 → 12,3400001). Jetzt als exakter Wert; vorhandene Werte werden beim Update auf Cent gerundet.
- Der Tageslohn wird auf Cent gerundet, bevor Summen gebildet werden.
- Live-Start prüft, dass kein bereits erfasster Block überlappt wird.
- Defekte gespeicherte Daten werden nicht mehr stillschweigend überschrieben, sondern unter `days_json_corrupt` gesichert.
- Der Tageszustand (läuft/Pause/gestoppt) wird beim Laden aus den offenen Einträgen abgeleitet und ist dadurch immer konsistent.

## Aufbau
- `MainActivity` – Oberfläche
- `Store` – Datenbestand und Zeiterfassung (gemeinsam von App und Widget genutzt), Speicherung in SharedPreferences (JSON)
- `Intervals`, `DayTime`, `Fmt`, `Export`, `DayJson`, `DayRecord`, `TimeEntry` – reine Java-Logik ohne Android-Abhängigkeiten, per JUnit getestet
- `TimerWidgetProvider`, `WidgetActionReceiver` – Widget (die Tasten laufen über einen nicht exportierten Receiver)

## Tests
Die Unit-Tests liegen unter `app/src/test`. Sie brauchen kein Android-SDK:

    tools/run_tests.sh

## Build
- Java 8 Source-Kompatibilität
- minSdk 23, targetSdk 35, compileSdk 35
- Keine externen App-Bibliotheken
- VersionCode 3 / VersionName 1.3.0

Projektordner in Code Assist öffnen und Gradle synchronisieren/builden. Das Widget fügst du über langes Drücken auf den Homescreen → „Widgets“ → „Arbeitszeit“ hinzu.

Hinweis: Die App verwendet weiterhin dasselbe applicationId und dieselben SharedPreferences-Schlüssel wie die vorherige Version. Bei einem normalen Update bleiben vorhandene Daten erhalten, sofern die APK mit demselben Signaturschlüssel installiert wird. Lege vor dem Update trotzdem ein Backup an (neu in 1.3, daher in 1.2 noch nicht möglich): Die Datenmigration auf Version 3 ist nur aufwärtskompatibel.
