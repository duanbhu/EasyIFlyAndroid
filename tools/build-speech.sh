#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
is_jdk_17() {
    [[ -x "$1/bin/java" ]] || return 1
    local speech_java_version
    speech_java_version=$("$1/bin/java" -version 2>&1) || return 1
    [[ "$speech_java_version" == *'version "17.'* || "$speech_java_version" == *'version "17"'* ]]
}

if [[ -n "${SPEECH_JAVA_HOME:-}" ]]; then
    export JAVA_HOME="$SPEECH_JAVA_HOME"
elif [[ -n "${JAVA_HOME:-}" ]] && is_jdk_17 "$JAVA_HOME"; then
    export JAVA_HOME
elif is_jdk_17 /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
elif [[ -x /usr/libexec/java_home ]] && speech_jdk_home=$(/usr/libexec/java_home -v 17 2>/dev/null) && is_jdk_17 "$speech_jdk_home"; then
    export JAVA_HOME="$speech_jdk_home"
fi
if [[ -z "${JAVA_HOME:-}" ]] || ! is_jdk_17 "$JAVA_HOME"; then
    echo 'This build requires JDK 17. Set SPEECH_JAVA_HOME or JAVA_HOME to its installation directory.' >&2
    exit 1
fi
# Pass --offline when all Gradle/Android dependencies are already cached.
./gradlew -Dorg.gradle.java.home="$JAVA_HOME" -Pkotlin.compiler.execution.strategy=in-process :library:bundleAar :library:testReleaseUnitTest :library:lintRelease :library:verifyKotlin18Consumer :library:publishReleasePublicationToBundleRepository --console=plain "$@"
./gradlew -Dorg.gradle.java.home="$JAVA_HOME" -Pkotlin.compiler.execution.strategy=in-process -PwithSample :sample:assembleDebug :sample:assembleRelease --console=plain "$@"
python3 - <<'PY'
import hashlib
import io
import json
import shutil
from pathlib import Path
from zipfile import ZipFile

artifact = Path('dist/xbd-speech-recognition-1.0.0.aar')
payload = artifact.read_bytes()
checksum = hashlib.sha256(payload).hexdigest()
with ZipFile(io.BytesIO(payload)) as aar:
    abis = sorted({name.split('/')[1] for name in aar.namelist() if name.endswith('/libmsc.so')})
    with ZipFile(io.BytesIO(aar.read('classes.jar'))) as classes:
        names = classes.namelist()
        bundled_sdk = 'com/iflytek/cloud/SpeechRecognizer.class' in names
        assert bundled_sdk, 'AAR is missing the bundled speech SDK'
        assert 'com/xbdlib/speech/SpeechRecognitionSession.class' in names
assert abis == ['arm64-v8a', 'armeabi-v7a'], abis
info = dict(artifact=artifact.name, bytes=len(payload), sha256=checksum,
            classes=sum(name.endswith('.class') for name in names),
            abis=abis, bundledSdkClasses=bundled_sdk)
artifact.with_name('artifact-info.json').write_text(json.dumps(info, indent=2) + '\n')
artifact.with_name('SHA256SUMS').write_text(f'{checksum}  {artifact.name}\n')
sample_dir = Path('dist/sample')
sample_dir.mkdir(exist_ok=True)
for variant in ('debug', 'release'):
    shutil.copy2(Path('sample/build/outputs/apk') / variant / f'sample-{variant}.apk', sample_dir)
print(f'Verified {artifact.name}: {len(payload)} bytes, SHA-256 {checksum}')
PY
