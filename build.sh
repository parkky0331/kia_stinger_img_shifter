#!/bin/sh
# Builds Stinger Image Shifter as an HMG-API mod package: hooks.jar, mod.json and settings.json, signed with your author key.
#   sh build.sh        -> dist/stinger-imgshifter-<version>.zip
# Set these in the environment or in a local.env file next to this script (ignored by git):
#   HMG_API       a checkout of https://github.com/Open-HMG/HMG-API (hook API to compile against, and the signer)
#   ANDROID_JAR   android.jar of Android API 19
#   D8            d8 from the Android SDK build-tools
#   SIGNING_KEY   your author key (HMG-API's Signing keygen). Keep it private.
# The package contains no pictures. The mod builds them on the car from the stock app's own.
set -eu
cd "$(dirname "$0")"
root=$PWD
[ -f local.env ] && . ./local.env
: "${HMG_API:?Set HMG_API to the HMG-API source directory}"
: "${ANDROID_JAR:?Set ANDROID_JAR to Android API 19 android.jar}"
: "${D8:?Set D8 to the Android SDK d8 executable}"
: "${SIGNING_KEY:?Set SIGNING_KEY to your author key}"
id=$(sed -n 's/^  "id": "\([^"]*\)".*/\1/p' mod.json)
version=$(sed -n 's/^  "version": "\([^"]*\)".*/\1/p' mod.json)
case "$version" in
    ''|*[!0-9A-Za-z._-]*) echo "build.sh: can't read one version from mod.json" >&2; exit 1 ;;
esac
sh tools/check.sh
archive=$root/dist/$id-$version.zip
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/rs" "$tmp/rt" "$tmp/classes" "$tmp/test" "$tmp/dex" "$tmp/mod" dist
find "$HMG_API/core/runtime/stubs" -name '*.java' > "$tmp/rs.args"
find "$HMG_API/core/runtime/java" -name '*.java' > "$tmp/rt.args"
find src -name '*.java' > "$tmp/src.args"
javac --release 8 -nowarn -Xlint:-options -cp "$ANDROID_JAR" -d "$tmp/rs" @"$tmp/rs.args"
javac --release 8 -nowarn -Xlint:-options -cp "$ANDROID_JAR:$tmp/rs" -d "$tmp/rt" @"$tmp/rt.args"
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$ANDROID_JAR:$tmp/rt" -d "$tmp/classes" @"$tmp/src.args"
# check which cars the package targets, using HMG-API's own checker
javac --release 8 -nowarn -Xlint:-options -encoding UTF-8 -cp "$tmp/rt" -d "$tmp/test" tests/SystemTest.java
java -cp "$tmp/rt:$tmp/test" SystemTest mod.json
jar cf "$tmp/classes.jar" -C "$tmp/classes" dev
"$D8" --release --min-api 19 --lib "$ANDROID_JAR" --classpath "$tmp/rt" --output "$tmp/dex" "$tmp/classes.jar"
jar cMf "$tmp/mod/hooks.jar" -C "$tmp/dex" classes.dex
cp mod.json settings.json LICENSE "$tmp/mod/"
java -cp "$tmp/rt" hmgapi.engine.Signing sign "$tmp/mod" "$SIGNING_KEY"
java -cp "$tmp/rt" hmgapi.engine.Signing verify "$tmp/mod"
rm -f "$archive"
(cd "$tmp/mod" && find . -type f | LC_ALL=C sort | zip -q -X -D "$tmp/package.zip" -@)
mv "$tmp/package.zip" "$archive"
echo "ok: $archive $(wc -c < "$archive" | tr -d ' ') bytes"
shasum -a 256 "$archive" | cut -c1-64
