#!/usr/bin/env bash
# Выпуск Taxi Radar: bash .claude/skills/release/release.sh 1.17
set -euo pipefail

NAME="${1:?Укажи versionName, например 1.17}"
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
GRADLE=app/build.gradle.kts
KEYS="$ROOT/../TaxiRadar-keys"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
export ANDROID_HOME="$SDK"

[ "$(git branch --show-current)" = main ] || { echo "Нужна ветка main"; exit 1; }
[ -d "$KEYS" ] || { echo "Нет папки $KEYS"; exit 1; }

OLD_CODE=$(sed -nE 's/.*versionCode = ([0-9]+).*/\1/p' "$GRADLE")
CODE=$((OLD_CODE + 1))
sed -i -E "s/versionCode = [0-9]+/versionCode = $CODE/; s/versionName = \"[^\"]+\"/versionName = \"$NAME\"/" "$GRADLE"
echo "Версия: $NAME (code $CODE, было $OLD_CODE)"

./gradlew.bat assembleRelease -q

APK=app/build/outputs/apk/release/app-release.apk
APKSIGNER=$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)apksigner.bat
CERT=$("$APKSIGNER" verify --print-certs "$APK" | sed -nE 's/.*SHA-256 digest: ([0-9a-f]+).*/\1/p' | head -1)
case "$CERT" in
  6d955dae*2f80da70) echo "Подпись: release-ключ ✓" ;;
  *) echo "ЧУЖАЯ подпись: $CERT"; exit 1 ;;
esac

OUT="$KEYS/TaxiRadar-$NAME.apk"
cp "$APK" "$OUT"
echo "Файл: $OUT"
echo "Размер: $(du -h "$OUT" | cut -f1)"
echo "SHA-256: $(sha256sum "$OUT" | cut -d' ' -f1)"
