#!/bin/bash
# Build tanpa Gradle: aapt2 + javac + d8 + zipalign + apksigner
# Kebutuhan: JDK 11+, Android build-tools 34, platform android-34
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/home/user/sdk}}
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-34/android.jar
LIBS=$(ls libs/*.jar | tr '\n' ':')
export JAVA_TOOL_OPTIONS="-Xmx500m"
rm -rf build && mkdir -p build/gen build/classes build/dex


$BT/aapt2 compile --dir res -o build/res.zip || exit 1
$BT/aapt2 link -o build/base.apk -I $AJ --manifest AndroidManifest.xml -A assets \
  --java build/gen --min-sdk-version 28 --target-sdk-version 34 \
  --version-code 31 --version-name 7.0.1 build/res.zip || exit 1

javac -encoding UTF-8 --release 11 -Xlint:-options -nowarn -classpath "$AJ:$LIBS" -d build/classes \
  $(find build/gen src -name "*.java") || exit 1

$BT/d8 --release --min-api 28 --lib $AJ --output build/dex \
  $(find build/classes -name "*.class") libs/*.jar || exit 1

cp build/base.apk build/unsigned.apk
(cd build/dex && python3 -c "
import zipfile
with zipfile.ZipFile('../unsigned.apk','a',zipfile.ZIP_DEFLATED) as z: z.write('classes.dex','classes.dex')
")
$BT/zipalign -p -f 4 build/unsigned.apk build/aligned.apk || exit 1

KS=${KEYSTORE:-$HOME/sesibrowser.keystore}
[ -f $KS ] || keytool -genkeypair -keystore $KS -storepass sesibrowser -keypass sesibrowser \
  -alias sesi -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Sesi Browser, O=Kuli, C=ID" >/dev/null 2>&1

$BT/apksigner sign --ks $KS --ks-pass pass:sesibrowser --key-pass pass:sesibrowser \
  --out SesiBrowser-MAXMODE.apk build/aligned.apk || exit 1
$BT/apksigner verify SesiBrowser-MAXMODE.apk && echo "OK -> SesiBrowser-MAXMODE.apk" && ls -la SesiBrowser-MAXMODE.apk
