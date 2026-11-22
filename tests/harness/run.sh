#!/bin/bash
# Emulator harness (any AVD at 1920x720 and 240 dpi, like the head unit's screen).
# Builds three stand-in apps under the stock apps' package names, each containing the stock pictures
# the mod looks for plus the mod's classes and the harness. It shows the stock picture and ours side
# by side in each of the three places, takes screenshots and reads the harness's checks from the log.
#   bash tests/harness/run.sh
# Set these in the environment or in local.env:
#   SHIFTER_APPS   folder with HKMC_VehicleSetting.apk, RightScreenHome.apk and HKMC_RearCamera.apk from your own car
#   SHIFTER_WORK   work folder outside this repository (stand-ins, screenshots and the log go there)
#   ANDROID_JAR    android.jar of Android API 19
# The stand-ins target a newer Android than the car's so the emulator shows them full width and without
# its "built for an older version" notice. The mod's classes are the same ones the car gets.
# The stock pictures belong to the car maker and stay out of this repository. Everything built here goes
# to the work folder, and the stand-ins are uninstalled from the emulator at the end.
set -eu
cd "$(dirname "$0")/../.."
[ -f local.env ] && . ./local.env
: "${SHIFTER_APPS:?Set SHIFTER_APPS to the folder with the three stock apps from your own car}"
: "${SHIFTER_WORK:?Set SHIFTER_WORK to a work folder outside this repository}"
: "${ANDROID_JAR:?Set ANDROID_JAR to Android API 19 android.jar}"
APPS=$SHIFTER_APPS
OUT=$SHIFTER_WORK
case "$OUT" in "$PWD"|"$PWD"/*) echo "SHIFTER_WORK must be outside this repository" >&2; exit 1 ;; esac
SDK=${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}
BT=$SDK/build-tools/35.0.0
ADB="$SDK/platform-tools/adb -e"
AJ=$ANDROID_JAR
SETTINGS=com.android.vehiclesettings
SPLIT=com.mobis.splitscreen.right
CAMERA=com.hkmc.system.app.rearcamera
$ADB get-state >/dev/null 2>&1 || { echo "no emulator running" >&2; exit 1; }

# clear only what this script creates, never the folder itself
mkdir -p "$OUT"
rm -rf "$OUT/settings" "$OUT/split" "$OUT/camera" "$OUT/cls" "$OUT/dex" "$OUT/shots" "$OUT/classes.txt" "$OUT/log.txt" \
    "$OUT/settings.apk" "$OUT/split.apk" "$OUT/camera.apk"
mkdir -p "$OUT/settings/res/drawable-nodpi" "$OUT/split/res/drawable-nodpi" "$OUT/camera/res/drawable-nodpi" "$OUT/cls" "$OUT/dex" "$OUT/shots"
(cd "$OUT/settings/res/drawable-nodpi" && /usr/bin/unzip -q -j "$APPS/HKMC_VehicleSetting.apk" \
    'res/drawable-nodpi/set_etc_drive_bg_*' 'res/drawable-nodpi/set_etc_drivemode_comfort_ck.png' \
    'res/drawable-nodpi/set_etc_mode_*_sedan.*' 'res/drawable-nodpi/set_etc_mode_sport_sedan_ikpe2.*' \
    'res/drawable-nodpi/usm_dm_008_01_n_sedan_*' 'res/drawable-nodpi/usm_dm_008_01_sedan_sport.*')
(cd "$OUT/split/res/drawable-nodpi" && /usr/bin/unzip -q -j "$APPS/RightScreenHome.apk" \
    'res/drawable-nodpi/split_bg_mode_*_n_sedan.png' 'res/drawable-nodpi/split_bg_mode_snow_sedan.png')
(cd "$OUT/camera/res/drawable-nodpi" && /usr/bin/unzip -q -j "$APPS/HKMC_RearCamera.apk" \
    'res/drawable-nodpi/cam_pdw_car_ckpe.*' 'res/drawable-nodpi/cam_pdw_car_sedan.*' \
    'res/drawable-nodpi/cam_ico_door_0[147].*' 'res/drawable-nodpi/cam_ico_door_1[35].*' 'res/drawable-nodpi/cam_ico_door_01_suv.*' \
    'res/drawable-nodpi/cam_ico_front_01*' 'res/drawable-nodpi/cam_ico_rear_ass_top_*' 'res/drawable-nodpi/cam_ico_front_sur_n.*' \
    'res/drawable-nodpi/cam_ico_assist.*' 'res/drawable-nodpi/cam_ico_3d_view_n.*')
mkdir -p "$OUT/camera/res/drawable" "$OUT/camera/res/values"
printf '%s\n' '<?xml version="1.0" encoding="utf-8"?>' '<resources><item type="id" name="camera_preview" /></resources>' > "$OUT/camera/res/values/ids.xml"
for button in front_01:front_01 rear_ass_top_view:rear_ass_top; do
    cat > "$OUT/camera/res/drawable/cam_ico_${button%%:*}_selector.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false" android:drawable="@drawable/cam_ico_${button##*:}_d" />
    <item android:state_selected="true" android:drawable="@drawable/cam_ico_${button##*:}_s" />
    <item android:drawable="@drawable/cam_ico_${button##*:}_n" />
</selector>
EOF
done

javac -source 8 -target 8 -nowarn -Xlint:-options -encoding UTF-8 -bootclasspath "$AJ" -d "$OUT/cls" \
    src/dev/stinger/imgshifter/Art.java src/dev/stinger/imgshifter/Resample.java src/dev/stinger/imgshifter/BodyPaint.java \
    src/dev/stinger/imgshifter/Maker.java src/dev/stinger/imgshifter/Swap.java src/dev/stinger/imgshifter/AroundPlan.java \
    src/dev/stinger/imgshifter/TopCar.java src/dev/stinger/imgshifter/Around.java src/dev/stinger/imgshifter/AroundLayers.java \
    tests/harness/Harness.java tests/harness/Show.java tests/harness/AvmMode.java
find "$OUT/cls" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 19 --lib "$AJ" --output "$OUT/dex" @"$OUT/classes.txt"
[ -f "$OUT/harness.jks" ] || keytool -genkeypair -keystore "$OUT/harness.jks" -storepass harness -keypass harness \
    -alias harness -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=imgshifter harness" >/dev/null 2>&1

for app in settings split camera; do
    pkg=$SETTINGS; [ $app = split ] && pkg=$SPLIT; [ $app = camera ] && pkg=$CAMERA
    cat > "$OUT/$app/AndroidManifest.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$pkg" android:versionCode="1" android:versionName="harness">
    <uses-sdk android:minSdkVersion="19" android:targetSdkVersion="28" />
    <application android:label="imgshifter harness" android:theme="@android:style/Theme.NoTitleBar.Fullscreen">
        <activity android:name="harness.Show" android:exported="true" android:screenOrientation="landscape"
            android:configChanges="orientation|screenSize|keyboardHidden" />
    </application>
</manifest>
EOF
    "$BT/aapt2" compile --dir "$OUT/$app/res" -o "$OUT/$app/res.zip"
    "$BT/aapt2" link -o "$OUT/$app/base.apk" -I "$AJ" --manifest "$OUT/$app/AndroidManifest.xml" \
        --min-sdk-version 19 --target-sdk-version 28 "$OUT/$app/res.zip"
    cp "$OUT/$app/base.apk" "$OUT/$app/unsigned.apk"
    (cd "$OUT/dex" && zip -q -j "../$app/unsigned.apk" classes.dex)
    "$BT/zipalign" -f 4 "$OUT/$app/unsigned.apk" "$OUT/$app/aligned.apk"
    "$BT/apksigner" sign --ks "$OUT/harness.jks" --ks-pass pass:harness --min-sdk-version 19 \
        --v1-signing-enabled true --v2-signing-enabled true --out "$OUT/$app.apk" "$OUT/$app/aligned.apk"
    $ADB uninstall $pkg >/dev/null 2>&1 || true
    $ADB install -r "$OUT/$app.apk" >/dev/null
done
trap 'for p in $SETTINGS $SPLIT $CAMERA; do $ADB uninstall $p >/dev/null 2>&1 || true; done' EXIT

: > "$OUT/log.txt"
# page <name> <package> <seconds to wait> <cells> [never] [warm] [more start-up extras]
page() {
    local name=$1 pkg=$2 wait=$3 cells=$4 never=${5:-} warm=${6:-false} more=${7:-}
    $ADB logcat -c
    local extra="$more"
    [ -n "$never" ] && extra="$extra --es never '$never'"
    $ADB shell "am start -S -W -n $pkg/harness.Show --es cells '$cells' $extra --ez warm $warm" >/dev/null
    sleep "$wait"
    $ADB exec-out screencap -p > "$OUT/shots/$name.png"
    echo "== $name" >> "$OUT/log.txt"
    $ADB logcat -d -v brief -s imgshifter:V AndroidRuntime:E | grep -v '^-----' >> "$OUT/log.txt" || true
}
row() {   # row <prefix> <suffix> <how> <y> <w> <h> <ours> <step> <mode>...
    local pre=$1 suf=$2 how=$3 y=$4 w=$5 h=$6 ours=$7 step=$8; shift 8
    local x=0 out=""
    for m in "$@"; do out="$out$pre$m$suf,$how,$x,$y,$w,$h,$ours;"; x=$((x + step)); done
    printf '%s' "$out"
}

# right-hand panel: stock picture on top, ours below
page split-1 $SPLIT 3 "$(row split_bg_mode_ _n_sedan image 10 600 330 0 660 eco comfort smart)$(row split_bg_mode_ _n_sedan image 370 600 330 1 660 eco comfort smart)" \
    "split_bg_mode_snow_sedan;split_bg_mode_nmode_n_sedan"
# same with the pictures built ahead of time, as the mod does in the panel
page split-2 $SPLIT 9 "$(row split_bg_mode_ _n_sedan image 10 600 330 0 660 sport sportplus custom)$(row split_bg_mode_ _n_sedan image 370 600 330 1 660 sport sportplus custom)" "" true
# settings pop-up at its real size (840 x 354 dp = 1260 x 531, fitXY): stock, then ours
page popup-stock $SETTINGS 3 "set_etc_mode_sport_sedan,imagexy,330,94,1260,531,0"
page popup-ours $SETTINGS 5 "set_etc_mode_sport_sedan,imagexy,330,94,1260,531,1" \
    "set_etc_mode_sport_sedan_ikpe2;set_etc_mode_deepsnow_sedan;set_etc_mode_nmode_nstandard_sedan;set_etc_drive_bg_sport;set_etc_drivemode_comfort_ck;usm_dm_008_01_sedan_sport;usm_dm_008_01_n_sedan_auto;usm_dm_008_01_n_sedan_nmode_custom"
# every mode's pop-up picture at half size
page popup-all $SETTINGS 6 "$(row set_etc_mode_ _sedan imagexy 60 630 265 1 640 eco comfort smart)$(row set_etc_mode_ _sedan imagexy 380 630 265 1 640 sport sportplus custom)"
# help picture: stock and ours as a background (the help pane), then every mode in an ImageView (the drive mode screen)
page guide-1 $SETTINGS 3 "usm_dm_008_01_n_sedan_sport,bg,0,90,640,540,0;usm_dm_008_01_n_sedan_sport,bg,640,90,640,540,1;usm_dm_008_01_n_sedan_eco,bg,1280,90,640,540,1"
page guide-2 $SETTINGS 3 "$(row usm_dm_008_01_n_sedan_ '' image 90 640 540 1 640 comfort smart custom)"
page guide-3 $SETTINGS 3 "$(row usm_dm_008_01_n_sedan_ '' image 90 640 540 1 640 sportplus eco sport)"
# the camera app's car picture: requested before it is ready it stays stock; then stock and repainted side by side
page camera-early $CAMERA 3 "cam_pdw_car_ckpe,bg,850,130,219,459,2" "cam_pdw_car_sedan" false "--es paint hichroma_red"
for colour in hichroma_red ascot_green aurora_black_pearl snow_white_pearl; do
    page camera-$colour $CAMERA 5 "cam_pdw_car_ckpe,bg,700,130,219,459,0;cam_pdw_car_ckpe,bg,1000,130,219,459,1" "" false \
        "--es paint $colour --es warmcar cam_pdw_car_ckpe"
done
# body colour left at stock: the picture isn't touched
page camera-stock $CAMERA 3 "cam_pdw_car_ckpe,bg,850,130,219,459,2" "cam_pdw_car_ckpe" false "--es warmcar cam_pdw_car_ckpe"
page signs-early $CAMERA 4 "cam_ico_door_07,bg,500,120,288,384,3,b;cam_ico_front_01_selector,image,900,180,207,261,3,b;cam_ico_assist,bg,1200,120,288,384,3,b" \
    "" false "--es paint micro_blue"
doors() { row cam_ico_door_ '' bg "$1" 192 256 "$2" 200 01 04 07 13 15 | sed "s/;/,$3;/g"; }
button() { printf '%s,image,1060,%s,138,174,%s,%s;%s,imagesel,1210,%s,138,174,%s,%s;' "$1" "$2" "$3" "$4" "$1" "$2" "$3" "$4"; }
page signs $CAMERA 5 "$(doors 20 0 n)$(doors 300 1 b)$(button cam_ico_front_01_selector 20 0 n)cam_ico_front_01_selector,imageoff,1360,20,138,174,0,n;\
$(button cam_ico_front_01_selector 300 1 b)cam_ico_front_01_selector,imageoff,1360,300,138,174,1,n;\
$(button cam_ico_rear_ass_top_view_selector 500 1 b)cam_ico_front_sur_n,bg,1360,500,138,174,1,b;\
cam_ico_assist,bg,1560,20,192,256,0,n;cam_ico_assist,bg,1560,300,192,256,1,b" \
    "cam_ico_door_01_suv;cam_ico_front_01;cam_ico_front_01_d;cam_ico_3d_view_n;cam_pdw_car_sedan" false "--es paint micro_blue --ez warmsigns true"
page around $CAMERA 11 "" "" false "--es paint micro_blue --ez around true"
page around-rear $CAMERA 11 "" "" false "--es paint micro_blue --ez around true --ei last 16"
# same in layer mode (experimental): left in the view ahead, in the view behind, and zoomed out two steps
page around-layer $CAMERA 11 "" "" false "--es paint hichroma_red --ez around true --ez layered true"
page around-layer-rear $CAMERA 11 "" "" false "--es paint hichroma_red --ez around true --ez layered true --ei last 16"
page signs-stock $CAMERA 3 "cam_ico_door_07,bg,700,120,288,384,2,n;cam_ico_front_01_selector,image,1100,180,207,261,2,n" \
    "cam_ico_door_07;cam_ico_front_01_selector" false "--ez warmsigns true"

cat "$OUT/log.txt"
echo
if grep -q "CHECK FAIL\|FATAL EXCEPTION" "$OUT/log.txt"; then echo "FAILED (see $OUT/log.txt)"; exit 1; fi
# an enlarged picture (the pop-up's) is built twice: quickly, then sharp in the background, and the view is given the sharp one
sharp=$(grep -c "sharpened popup" "$OUT/log.txt" || true)
handed=$(grep -c "settled set_etc_mode_.* exchanged=true" "$OUT/log.txt" || true)
kept=$(grep "settled " "$OUT/log.txt" | grep -v "set_etc_mode_" | grep -c "exchanged=false" || true)
others=$(grep "settled " "$OUT/log.txt" | grep -vc "set_etc_mode_" || true)
if [ "$sharp" != 6 ] || [ "$handed" != 7 ] || [ "$kept" != "$others" ]; then
    echo "FAILED: sharpened $sharp pop-up pictures (6 expected), handed to $handed views (7 expected); $kept of $others other views kept theirs"
    exit 1
fi
n=$(grep -c "CHECK ok" "$OUT/log.txt" || true)
echo "pages checked ok: $n of 21; pop-up pictures sharpened: $sharp, handed to $handed views; screenshots in $OUT/shots"
[ "$n" = 21 ] || { echo "FAILED: not every page was checked"; exit 1; }
