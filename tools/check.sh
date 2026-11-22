#!/bin/sh
# Offline checks for names, tinting, resizing and body paint (no Android needed).
#   sh tools/check.sh
# With SHIFTER_PIECES set (in the environment or in local.env) it also tints the real car drawing
# and compares each mode's picture with a reference. SHIFTER_PIECES is a folder containing vs/
# (the stock floor pictures and car drawing from your own car's vehicle settings app) and rebuild/
# (the reference pictures). Those pictures belong to the car maker and are never kept in this
# repository.
set -eu
cd "$(dirname "$0")/.."
[ -f local.env ] && . ./local.env
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
javac --release 8 -Xlint:-options -encoding UTF-8 -d "$tmp" src/dev/stinger/imgshifter/Art.java src/dev/stinger/imgshifter/Resample.java \
    src/dev/stinger/imgshifter/BodyPaint.java src/dev/stinger/imgshifter/AroundPlan.java src/dev/stinger/imgshifter/TopCar.java tests/ArtTest.java
pieces=${SHIFTER_PIECES:-}
if [ -n "$pieces" ] && [ -f "$pieces/vs/set_etc_drivemode_comfort_ck.png" ] && [ -d "$pieces/rebuild" ]; then
    java -cp "$tmp" dev.stinger.imgshifter.ArtTest "$pieces"
else
    java -cp "$tmp" dev.stinger.imgshifter.ArtTest
fi
