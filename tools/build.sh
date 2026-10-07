#!/usr/bin/env bash
# Builds app/build/cr-elixir-tracker.apk without Gradle.
# Needs: JDK, aapt2, dx (or d8), zipalign, apksigner, and an android.jar (API 34).
#   Ubuntu/Debian: apt install aapt dalvik-exchange zipalign apksigner
#   android.jar:   ANDROID_JAR=/path/to/platforms/android-34/android.jar
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app"
SRC="$APP/src/main"
OUT="$APP/build"
ANDROID_JAR="${ANDROID_JAR:-/opt/android-jars/android-34.jar}"
APK="$OUT/cr-elixir-tracker.apk"

[ -f "$ANDROID_JAR" ] || { echo "android.jar not found: $ANDROID_JAR (set ANDROID_JAR)"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT"/{res,gen,classes,test-classes,dex}

echo "> unit tests (JVM)"
javac -nowarn -encoding UTF-8 -d "$OUT/test-classes" \
  "$SRC/java/com/runetpisun/croverlay/Card.java" \
  "$SRC/java/com/runetpisun/croverlay/Tracker.java" \
  "$SRC/java/com/runetpisun/croverlay/Stats.java" \
  "$SRC/java/com/runetpisun/croverlay/Insights.java" \
  "$APP/src/test/java/com/runetpisun/croverlay/TrackerTest.java"
java -Dstdout.encoding=UTF-8 -cp "$OUT/test-classes" com.runetpisun.croverlay.TrackerTest "$SRC/assets/stats.tsv"

echo "> resources"
aapt2 compile --dir "$SRC/res" -o "$OUT/res/compiled.zip"
aapt2 link -o "$OUT/unsigned.apk" -I "$ANDROID_JAR" \
  --manifest "$SRC/AndroidManifest.xml" --java "$OUT/gen" -A "$SRC/assets" \
  --min-sdk-version 26 --target-sdk-version 34 \
  "$OUT/res/compiled.zip"

echo "> javac"
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "> dex"
if command -v d8 >/dev/null; then
  d8 --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
else
  DX="$(command -v dx || command -v dalvik-exchange)"
  "$DX" --dex --min-sdk-version=26 --output="$OUT/dex/classes.dex" "$OUT/classes"
fi
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)

echo "> align + sign"
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
apksigner sign --ks "$ROOT/keystore/croverlay.jks" --ks-pass pass:android \
  --ks-key-alias croverlay --key-pass pass:android \
  --out "$APK" "$OUT/aligned.apk"
apksigner verify --print-certs "$APK" | head -1
echo "OK: $APK"
