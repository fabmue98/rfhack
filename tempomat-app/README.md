# Tempomat-Anzeige (Android-Radio)

Android-App (Kotlin, Jetpack Compose) für das AJ/Hiworld-Autoradio im VW Crafter 2E.
Sie verbindet sich mit dem ESP32 des DIY-Tempomats und zeigt Zustand, Sollgeschwindigkeit,
aktuelle Geschwindigkeit sowie Kupplung und Bremse an. Außerdem lassen sich Komfort-Einstellungen
(Schrittweite, Mindest- und Höchstgeschwindigkeit) ändern.

## Sicherheitsgrenze

Die App ist **nur Anzeige und Einstellung**. Sie sendet keine Befehle zum Einschalten, Setzen,
Beschleunigen oder Wiederaufnehmen. Alles Sicherheitsrelevante liegt im ESP32:

- Kupplung und Bremse schalten immer im ESP32 ab, unabhängig von der App.
- Der ESP32 darf nie auf die App angewiesen sein. Fällt die Verbindung weg, regelt er normal weiter oder schaltet nach seiner eigenen Logik ab.
- `SET` nimmt der ESP32 nur an, wenn der Tempomat `OFF` ist, und prüft die Werte selbst. Die App sperrt das Senden zusätzlich, solange kein frischer `OFF`-Status vorliegt.
- Ist der letzte Status älter als 2 s oder die Verbindung weg, zeigt die App „– –“ statt alter Werte.

## Verbindung

| Weg | Details |
|---|---|
| Bluetooth LE (Standard) | Nordic UART Service, Service `6e400001-b5a3-f393-e0a9-e50e24dcca9e`, ESP32 → App per Notify auf TX `…0003`, App → ESP32 per Write auf RX `…0002`. ESP32 einmal in den Einstellungen per Suche auswählen. |
| WLAN | TCP-Client zum ESP32, Standard `192.168.4.1:3333` (ESP32 als Access Point). |
| Demo | Spielt eine Beispielfahrt ab, oben deutlich als DEMO markiert. |

Die App verbindet sich nach Abbrüchen alle 2 s neu.

## Protokoll v1

Eine ASCII-Zeile pro Nachricht, abgeschlossen mit `\n`:

```
$TYP;schlüssel=wert;schlüssel=wert*CS
```

`CS` ist das XOR aller Bytes zwischen `$` und `*` als zwei Hex-Ziffern (wie NMEA). Die App sendet
die Prüfsumme immer. Empfangene Zeilen ohne `*CS` werden akzeptiert, Zeilen mit falscher Prüfsumme verworfen.

### ESP32 → App

| Nachricht | Felder | Wann |
|---|---|---|
| `STAT` | `st` = `OFF`/`STANDBY`/`ACTIVE`/`OVERRIDE`/`FAULT`, `set` Soll km/h (0 = keins), `v` Ist km/h, `clu` Kupplung 0/1, `brk` Bremse 0/1, `err` Fehlercode, optional `why` = `BRAKE`/`CLUTCH`/`LEVER`/`SPEED`/… | mindestens 5× pro Sekunde |
| `CFG` | `step` (1, 2, 5, 10), `min`, `max` in km/h | auf `GET` und nach erfolgreichem `SET` |
| `ACK` | `cmd`, `ok` 0/1, `msg` | auf `SET` |
| `HELLO` | `fw` Firmware-Version, `proto` = 1 | nach Verbindungsaufbau |

Beispiel: `$STAT;st=ACTIVE;set=90;v=89.6;clu=0;brk=0;err=0*49`

### App → ESP32

| Nachricht | Felder |
|---|---|
| `GET` | `what=cfg` |
| `SET` | `step`, `min`, `max` (Bereich 30–140 km/h, `min` < `max`) |

## Bauen und aufs Radio bringen

```
cd tempomat-app
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

GitHub Actions baut bei jeder Änderung unter `tempomat-app/` und lädt das APK als Artefakt
`tempomat-app-release-apk` hoch. Das APK per USB-Stick oder Download auf dem Radio installieren
(„Unbekannte Quellen“ erlauben). Die App ist auf Querformat und dunkles Design ausgelegt und hält
den Bildschirm an, solange sie im Vordergrund ist.

Mindestversion ist Android 8.0 (API 26).
