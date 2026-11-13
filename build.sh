#!/bin/sh
# msgbus-android 构建：javac -> d8 -> aapt2 link -> zip -> zipalign -> apksigner
# 都不随仓库分发，需要自备：
#   ANDROID_JAR   Android platform 的 android.jar（如 $ANDROID_HOME/platforms/android-34/android.jar）
#   AAPT2_JAR     可选：只给 aapt2 -I 用的 framework jar。某些 aapt2 版本读不了高版本 jar 的
#                 资源表（报 "entry offsets overlap"），这时指一个低版本 framework jar 即可
#   D8_JAR        r8.jar（内含 D8），或把 d8 放进 PATH 并设 D8=d8
#   aapt2 zipalign apksigner zip keytool java javac 在 PATH（build-tools + JDK）
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ANDROID_JAR=${ANDROID_JAR:-${ANDROID_HOME:-}/platforms/android-34/android.jar}
[ -f "$ANDROID_JAR" ] || { echo "缺少 ANDROID_JAR（例如 \$ANDROID_HOME/platforms/android-34/android.jar）" >&2; exit 2; }

AAPT2_JAR=${AAPT2_JAR:-$ANDROID_JAR}
[ -f "$AAPT2_JAR" ] || { echo "缺少 AAPT2_JAR" >&2; exit 2; }

V=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$HERE/AndroidManifest.xml" | head -1)
VC=$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' "$HERE/AndroidManifest.xml" | head -1)
[ -n "$V" ] && [ -n "$VC" ] || { echo "清单里读不到 versionName/versionCode" >&2; exit 2; }
OUT=${1:-$HERE/dist/msgbus-$V.apk}

B=$(mktemp -d)
trap 'rm -rf "$B"' EXIT
mkdir -p "$B/classes" "$B/dex" "$(dirname "$OUT")"

echo "[1/5] javac"
find "$HERE/src" -name '*.java' > "$B/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -nowarn -cp "$ANDROID_JAR" -d "$B/classes" @"$B/sources.txt"

echo "[2/5] dex"
if [ -n "${D8:-}" ]; then
    $D8 --min-api 28 --lib "$ANDROID_JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
else
    [ -f "${D8_JAR:-}" ] || { echo "需要 D8=d8 或 D8_JAR=r8.jar" >&2; exit 3; }
    java -cp "$D8_JAR" com.android.tools.r8.D8 --min-api 28 --lib "$ANDROID_JAR" \
        --output "$B/dex" $(find "$B/classes" -name '*.class')
fi

echo "[3/5] aapt2 link"
aapt2 link -o "$B/base.apk" --manifest "$HERE/AndroidManifest.xml" -I "$AAPT2_JAR" \
    --min-sdk-version 28 --target-sdk-version 33

echo "[4/5] zip + zipalign"
touch -t 198001010000 "$B/dex/classes.dex"
( cd "$B/dex" && zip -qX "$B/base.apk" classes.dex )
zipalign -f 4 "$B/base.apk" "$B/aligned.apk"

echo "[5/5] sign"
KS=${KEYSTORE:-$HERE/keystore.jks}
if [ ! -f "$KS" ]; then
    P=$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c1-16)
    keytool -genkeypair -keystore "$KS" -storepass "$P" -keypass "$P" -alias app \
        -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=msgbus, O=msgbus, C=CN"
    printf '%s\n' "$P" > "$KS.pass"
    chmod 600 "$KS" "$KS.pass"
    echo "  已生成签名 $KS（口令在同目录 .pass，不要提交）"
fi
P=$(tr -d '\r\n' < "$KS.pass")
apksigner sign --ks "$KS" --ks-pass "pass:$P" --key-pass "pass:$P" \
    --v4-signing-enabled false --out "$OUT" "$B/aligned.apk"
apksigner verify "$OUT"

echo
echo "产物: $OUT"
echo "版本: $V (versionCode $VC)  大小 $(du -h "$OUT" | cut -f1)"
