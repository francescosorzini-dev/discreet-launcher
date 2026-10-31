#!/usr/bin/env bash
# Install the APK on the running emulator and take screenshots of the main screens.
# Usage: ui_check.sh <apk> <output folder>
set -uo pipefail
APK="$1"
OUT="$2"
RELEASE_APK="${3:-}"
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
    c = re.search(r' content-desc="([^"]*)"', n)
    if (t and t.group(1) == sys.argv[2]) or (c and c.group(1) == sys.argv[2]):
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
# The swipe can be lost right after closing the drawer: retry until the menu button appears
MENU=""
for attempt in 1 2 3 ; do
	sleep 1
	adb shell input swipe $((W / 2)) $((H / 3)) $((W / 2)) $((H * 2 / 3)) 150
	sleep 1
	MENU=$(center_of "Menu")
	[ -n "$MENU" ] && break
done
shot 05_favorites

# Settings activity opened from the menu (action bar and system bars)
echo "menu at: $MENU"
if [ -n "$MENU" ] ; then
	adb shell input tap $MENU
	sleep 2
	OPERATION=$(center_of "Operation")
	echo "operation at: $OPERATION"
	[ -n "$OPERATION" ] && adb shell input tap $OPERATION
	shot 06_settings_operation
	adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
	adb pull /sdcard/ui.xml "$OUT/06_settings_operation.xml" > /dev/null 2>&1
	# Reset of the usage statistics (confirmation dialog)
	RESET=$(center_of "Reset app usage")
	echo "reset at: $RESET"
	if [ -n "$RESET" ] ; then
		adb shell input tap $RESET
		shot 06c_reset_dialog
		CONFIRM=$(center_of "Reset") ; [ -z "$CONFIRM" ] && CONFIRM=$(center_of "RESET")
		echo "confirm at: $CONFIRM"
		[ -n "$CONFIRM" ] && adb shell input tap $CONFIRM
		sleep 1
	fi
	# Scroll to the end of the settings to check the last items above the navigation bar
	adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 5)) 200
	adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 5)) 200
	shot 06b_settings_operation_end
	adb shell input keyevent KEYCODE_BACK
fi
adb shell input keyevent KEYCODE_HOME

# Same home screen with the 3-button navigation bar
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton || true
sleep 3
adb shell input keyevent KEYCODE_HOME
shot 07_home_3buttons
POS=$(center_of "Search…")
if [ -n "$POS" ] ; then
	adb shell input tap $POS
	sleep 2
	adb shell input text "ca"
	shot 08_search_3buttons
fi

# ---------- Search field placement with different clock settings ----------
PREFS="shared_prefs/${PKG}_preferences.xml"
set_string() { adb shell "run-as $PKG sed -i 's#<string name=\"$1\">[^<]*</string>#<string name=\"$1\">$2</string>#' $PREFS" ; }
set_bool() { adb shell "run-as $PKG sed -i 's#<boolean name=\"$1\" value=\"[a-z]*\" />#<boolean name=\"$1\" value=\"$2\" />#' $PREFS" ; }
# Key events leave touch mode (the focused view gets a highlight): touch an empty spot like a user would
restart_home() { adb shell am force-stop "$PKG" ; sleep 1 ; adb shell input keyevent KEYCODE_HOME ; sleep 4 ; adb shell input tap $((W - 5)) $((H / 2)) ; sleep 1 ; }
tap_field() {
	local P
	P=$(center_of "Search…")
	echo "field at: $P"
	[ -n "$P" ] && adb shell input tap $P && sleep 2 && adb shell input text "ma"
}

set_string clock_format datetime
set_string clock_position middle
restart_home
shot 09_clock_middle
tap_field
shot 10_clock_middle_search
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK

set_string clock_position bottom
restart_home
shot 11_clock_bottom

set_string clock_position top-left
restart_home
shot 12_clock_top_left

set_string clock_position middle
set_bool reverse_interface true
set_bool touch_targets true
restart_home
shot 13_reverse_targets_clock_middle

set_string clock_format none
restart_home
shot 14_reverse_targets_no_clock

set_bool reverse_interface false
set_bool touch_targets false
set_string clock_format none

# ---------- One-handed use ----------
set_string clock_format datetime
set_string clock_position middle
for HAND in right left ; do
	set_string one_handed $HAND
	restart_home
	P=$(center_of "Search…")
	if [ -n "$P" ] ; then
		adb shell input tap $P ; sleep 2
		shot 40_${HAND}_search_empty
		adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
		adb pull /sdcard/ui.xml "$OUT/40_${HAND}_search_empty.xml" > /dev/null 2>&1
		adb shell dumpsys window windows | grep -E "Window #|mFrame=|mAttrs=|PopupWindow|InputMethod" > "$OUT/40_${HAND}_windows.txt" 2>/dev/null || true
		adb shell input text "a" ; sleep 1
		shot 41_${HAND}_search_a
		adb shell input keyevent KEYCODE_BACK ; adb shell input keyevent KEYCODE_BACK ; sleep 1
	fi
	adb shell input swipe $((W / 2)) $((H * 2 / 3)) $((W / 2)) $((H / 4)) 150
	shot 42_${HAND}_drawer
	adb shell input keyevent KEYCODE_BACK
done
# ---------- Finger-like tap (press, hold, release) on a result in one-handed mode ----------
# An instant "input tap" hides problems that a real finger shows: the press lasts a while and the
# click is lost if the item moves under the finger. Check that the app really opens.
for HAND in right left ; do
	set_string one_handed $HAND
	restart_home
	P=$(center_of "Search…")
	if [ -n "$P" ] ; then
		adb shell input tap $P ; sleep 3
		T=$(center_of "Calendar")
		echo "calendar at: $T"
		if [ -n "$T" ] ; then
			read -r TX TY <<< "$T"
			adb shell input swipe "$TX" "$TY" "$TX" "$TY" 250
			sleep 3
			TOP=$(adb shell dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity")
			echo "$HAND: $TOP" >> "$OUT/finger_tap.txt"
			shot 52_${HAND}_after_finger_tap
		fi
	fi
	adb shell input keyevent KEYCODE_HOME
done
set_string one_handed off
set_string clock_format none
adb shell "run-as $PKG cat $PREFS" > "$OUT/prefs.xml" || true

# ---------- Optimized (R8) release APK: smoke test ----------
if [ -n "$RELEASE_APK" ] ; then
	adb install -r "$RELEASE_APK" && echo "release installed"
	adb shell am force-stop "$PKG"
	adb shell input keyevent KEYCODE_HOME
	sleep 4
	adb shell input tap $((W - 5)) $((H / 2))
	shot 20_release_home
	tap_field
	shot 21_release_search
	adb shell input keyevent KEYCODE_BACK
	adb shell input keyevent KEYCODE_BACK
	adb shell dumpsys package "$PKG" | grep -m2 -E "versionName|flags=" > "$OUT/release_package.txt" || true
fi

# ---------- Another language (per-app locale, Android 13+) ----------
if adb shell cmd locale set-app-locales "$PKG" --locales de-DE ; then
	adb shell am force-stop "$PKG"
	adb shell input keyevent KEYCODE_HOME
	sleep 4
	adb shell input tap $((W - 5)) $((H / 2))
	shot 30_german_home
	P=$(center_of "Suchen…")
	echo "german field at: $P"
	if [ -n "$P" ] ; then adb shell input tap $P ; sleep 2 ; shot 31_german_search ; adb shell input keyevent KEYCODE_BACK ; adb shell input keyevent KEYCODE_BACK ; fi
	adb shell cmd locale set-app-locales "$PKG" --locales "" || true
fi

# ---------- Pull and release on the app drawer (normal interface) ----------
restart_home
pull_state() { if [ -n "$(center_of "Calendar")" ] ; then echo open ; else echo closed ; fi ; }
adb shell input swipe $((W / 2)) $((H * 2 / 3)) $((W / 2)) $((H / 4)) 150 ; sleep 2
echo "after opening: $(pull_state)" >> "$OUT/pull_to_close.txt"
# Small slide of the thumb while tapping (fast, 40 px towards the edge)
adb shell input swipe $((W / 2)) $((H / 3)) $((W / 2)) $((H / 3 + 40)) 60 ; sleep 2
echo "small slide: $(pull_state) (expected open)" >> "$OUT/pull_to_close.txt"
# Short pull below the threshold
adb shell input swipe $((W / 2)) $((H / 3)) $((W / 2)) $((H / 3 + 80)) 400 ; sleep 2
echo "short pull: $(pull_state) (expected open)" >> "$OUT/pull_to_close.txt"
shot 60_after_short_pull
# Long pull beyond the threshold
adb shell input swipe $((W / 2)) $((H / 4)) $((W / 2)) $((H * 3 / 4)) 700 ; sleep 2
echo "long pull: $(pull_state) (expected closed)" >> "$OUT/pull_to_close.txt"
shot 61_after_long_pull

# ---------- Favorites panel with many favorites: tap with drift and pull and release ----------
adb shell am force-stop "$PKG"
adb shell "run-as $PKG sh -c 'rm -f files/favorites.txt'"
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER > "$OUT/launcher_activities.txt" 2>&1
# Build the favorites file locally ({package/full.class.Name} per line), then copy it in the app data
python3 - "$OUT/launcher_activities.txt" "$PKG" > "$OUT/favorites_input.txt" <<'PYEOF'
import re, sys
seen = []
for line in open(sys.argv[1], encoding="utf-8", errors="ignore"):
    m = re.search(r"([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)", line)
    if not m or m.group(1) == sys.argv[2]: continue
    pkg, cls = m.group(1), m.group(2)
    if cls.startswith("."): cls = pkg + cls
    c = "{" + pkg + "/" + cls + "}"
    if c not in seen: seen.append(c)
print("\n".join(seen))
PYEOF
adb push "$OUT/favorites_input.txt" /data/local/tmp/favorites.txt > /dev/null
adb shell "run-as $PKG sh -c 'cat /data/local/tmp/favorites.txt > files/favorites.txt'" || adb shell "cat /data/local/tmp/favorites.txt | run-as $PKG sh -c 'cat > files/favorites.txt'"
echo "favorites: $(adb shell "run-as $PKG sh -c 'wc -l < files/favorites.txt'")" >> "$OUT/favorites_pull.txt"
restart_home
fav_state() { if [ -n "$(center_of "Menu")" ] ; then echo open ; else echo closed ; fi ; }
open_favorites() { adb shell input swipe $((W / 2)) $((H / 3)) $((W / 2)) $((H * 2 / 3)) 150 ; sleep 2 ; }
open_favorites
echo "after opening: $(fav_state)" >> "$OUT/favorites_pull.txt"
shot 70_favorites_many
# Tap with a small drift (under the touch slop) on a favorite: the app must open
T=$(center_of "Calendar")
if [ -n "$T" ] ; then
	read -r TX TY <<< "$T"
	adb shell input swipe "$TX" "$TY" "$TX" $((TY + 4)) 200 ; sleep 3
	echo "tap with drift: $(adb shell dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity' | grep -oE '[a-z.]+/[A-Za-z.]+' | head -1) (expected calendar)" >> "$OUT/favorites_pull.txt"
	adb shell input keyevent KEYCODE_HOME ; sleep 2
fi
open_favorites
adb shell input swipe $((W / 2)) $((H / 5)) $((W / 2)) $((H / 5 + 40)) 60 ; sleep 2
echo "small slide: $(fav_state) (expected open)" >> "$OUT/favorites_pull.txt"
adb shell input swipe $((W / 2)) $((H / 5)) $((W / 2)) $((H / 5 + 80)) 400 ; sleep 2
echo "short pull: $(fav_state) (expected open)" >> "$OUT/favorites_pull.txt"
adb shell input swipe $((W / 2)) $((H / 5)) $((W / 2)) $((H * 4 / 5)) 700 ; sleep 2
echo "long pull: $(fav_state) (expected closed)" >> "$OUT/favorites_pull.txt"
shot 71_favorites_after_long_pull
adb shell "run-as $PKG sh -c 'rm -f files/favorites.txt'"
adb shell am force-stop "$PKG"

# Crash check
adb logcat -d -b crash > "$OUT/crash.log" || true
rm -f "$OUT/ui.xml"
exit 0
