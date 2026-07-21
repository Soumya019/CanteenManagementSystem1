#!/bin/bash
# Build GariaElectricPrices.apk without Gradle, using the Android SDK
# build tools directly (aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner).
#
# Requirements: ANDROID_HOME with platforms/android-34 and build-tools/34.0.0,
# plus a JDK (17+). Run:  ./build_apk.sh
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-/opt/android-sdk}"
BT="$SDK/build-tools/35.0.0"   # d8 in 34.0.0 cannot read JDK-21 class files
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT=build
APP=app

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/dex"

python3 export_items.py

"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/app.unsigned.apk" \
    -I "$PLATFORM" \
    --manifest "$APP/AndroidManifest.xml" \
    -A "$APP/assets" \
    --java "$OUT/gen" \
    --min-sdk-version 24 --target-sdk-version 34 \
    "$OUT/res.zip"

find "$APP/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -bootclasspath "$PLATFORM" \
    -d "$OUT/obj" @"$OUT/sources.txt" 2> >(grep -v 'bootstrap class path\|source value 8\|target value 8\|deprecat' >&2 || true)

jar cf "$OUT/obj.jar" -C "$OUT/obj" .
"$BT/d8" --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" "$OUT/obj.jar"

(cd "$OUT/dex" && zip -q -u ../app.unsigned.apk classes.dex)

"$BT/zipalign" -f 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"

KS=debug.keystore
if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -alias shopkey -keyalg RSA \
        -keysize 2048 -validity 10000 -storepass gariashop -keypass gariashop \
        -dname "CN=Garia Electric Hub, L=Kolkata, C=IN"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:gariashop \
    --out GariaElectricPrices.apk "$OUT/app.aligned.apk"

"$BT/aapt" dump badging GariaElectricPrices.apk | head -4
echo "OK: $(pwd)/GariaElectricPrices.apk"
