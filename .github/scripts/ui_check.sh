#!/usr/bin/env bash
# Install the APK on the running emulator and take screenshots of the main screens.
# Usage: ui_check.sh <apk> <output folder>
set -uo pipefail
APK="$1"
OUT="$2"
PKG="com.vincent_falzon.discreetlauncher"
mkdir -p "$OUT"

shot() { sleep 2 ; adb exec-out screencap -p > "$OUT/$1.png" ; echo "screenshot $1" ; }

# Center of the first UI element whose text matches, using uiautomator
center_of() {
	adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
	adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1
	python3 - "$OUT/ui.xml" "$1" <<'EOF'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
for node in re.finditer(r'<node [^>]*>', xml):
    n = node.group(0)
    t = re.search(r' text="([^"]*)"', n)
    if t and t.group(1) == sys.argv[2]:
        b = list(map(int, re.findall(r'\d+', re.search(r'bounds="([^"]*)"', n).group(1))))
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
        break
EOF
}

adb install -r "$APK"
adb shell cmd package set-home-activity "$PKG/.ActivityMain" || true
adb shell settings put global window_animation_scale 0
adb shell input keyevent KEYCODE_HOME
sleep 5
adb shell input keyevent KEYCODE_HOME
shot 01_home

# Tap on the home search field
POS=$(center_of "Search…")
echo "search field at: $POS"
if [ -n "$POS" ] ; then
	adb shell input tap $POS
	shot 02_search_open
	adb shell input text "set"
	shot 03_search_set
	adb shell input keyevent KEYCODE_BACK
	adb shell input keyevent KEYCODE_BACK
	sleep 1
	# Swipe up starting on the search field must still open the drawer
	read -r X Y <<< "$POS"
	adb shell input swipe "$X" "$Y" "$X" $((Y - 700)) 150
	shot 04_drawer_from_field
	adb shell input keyevent KEYCODE_BACK
fi

# Favorites panel (swipe down)
SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*} ; H=${SIZE#*x}
adb shell input swipe $((W / 2)) $((H / 3)) $((W / 2)) $((H * 2 / 3)) 150
shot 05_favorites
adb shell input keyevent KEYCODE_BACK

# Settings activity (action bar and system bars)
adb shell am start -n "$PKG/.settings.ActivitySettingsOperation"
shot 06_settings_operation
adb shell input keyevent KEYCODE_BACK

# Same home screen with the 3-button navigation bar
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton || true
sleep 3
adb shell input keyevent KEYCODE_HOME
shot 07_home_3buttons
POS=$(center_of "Search…")
if [ -n "$POS" ] ; then
	adb shell input tap $POS
	adb shell input text "ca"
	shot 08_search_3buttons
fi

# Crash check
adb logcat -d -b crash > "$OUT/crash.log" || true
rm -f "$OUT/ui.xml"
exit 0
