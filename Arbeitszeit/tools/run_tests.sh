#!/usr/bin/env bash
# Führt die JUnit-Tests der reinen Java-Logik (ohne Android-SDK) aus.
# Lädt JUnit, Hamcrest und org.json einmalig von Maven Central nach tools/.libs.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBS="$ROOT/tools/.libs"
SRC="$ROOT/project/app/src/main/java/de/kalkoen/arbeitszeit"
TST="$ROOT/project/app/src/test/java/de/kalkoen/arbeitszeit"
OUT="$(mktemp -d)"
mkdir -p "$LIBS"
M2=https://repo1.maven.org/maven2
fetch() { [ -f "$LIBS/$2" ] || curl -sSfL -o "$LIBS/$2" "$M2/$1/$2"; }
fetch junit/junit/4.13.2 junit-4.13.2.jar
fetch org/hamcrest/hamcrest-core/1.3 hamcrest-core-1.3.jar
fetch org/json/json/20240303 json-20240303.jar
CP="$LIBS/junit-4.13.2.jar:$LIBS/hamcrest-core-1.3.jar:$LIBS/json-20240303.jar"
javac -nowarn -encoding UTF-8 -cp "$CP" -d "$OUT" \
  "$SRC"/{TimeEntry,DayRecord,Fmt,DayTime,Intervals,DayJson,Export}.java "$TST"/*.java
java -Dfile.encoding=UTF-8 -cp "$OUT:$CP" org.junit.runner.JUnitCore \
  de.kalkoen.arbeitszeit.IntervalsTest de.kalkoen.arbeitszeit.DayTimeTest \
  de.kalkoen.arbeitszeit.ExportTest de.kalkoen.arbeitszeit.DayJsonTest
