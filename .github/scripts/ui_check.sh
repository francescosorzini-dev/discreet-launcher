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

# Time for the results list to appear after tapping the field (second opening: keyboard warm)
list_delay() {
	local P T0 N
	P=$(center_of "Search…")
	[ -z "$P" ] && { echo "no field" ; return ; }
	T0=$(date +%s%N)
	adb shell input tap $P
	for N in $(seq 1 40) ; do
		if adb shell uiautomator dump /sdcard/d.xml > /dev/null 2>&1 && adb shell cat /sdcard/d.xml | grep -q 'application_item' ; then
			echo "$(( ($(date +%s%N) - T0) / 1000000 )) ms (includes ~1 s of uiautomator dump)"
			adb shell input keyevent KEYCODE_BACK ; adb shell input keyevent KEYCODE_BACK ; sleep 1
			return
		fi
	done
	echo "not shown"
	adb shell input keyevent KEYCODE_BACK ; adb shell input keyevent KEYCODE_BACK ; sleep 1
}
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
# (the app data folder cannot read /data/local/tmp: pass the content inline, base64 encoded)
FAV_B64=$(base64 -w0 "$OUT/favorites_input.txt")
adb shell "run-as $PKG sh -c 'mkdir -p files && echo $FAV_B64 | base64 -d > files/favorites.txt'"
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
# Gestures which could leave the panel stuck: quick flick starting on a favorite, pull right after
# scrolling the list (it is still settling), slow pull starting on a favorite
open_favorites
T=$(center_of "Calendar")
if [ -n "$T" ] ; then
	read -r TX TY <<< "$T"
	adb shell input swipe "$TX" "$TY" "$TX" $((TY + H / 3)) 90 ; sleep 2
	echo "flick from a favorite: $(fav_state) (expected closed)" >> "$OUT/favorites_pull.txt"
fi
open_favorites
adb shell input swipe $((W / 2)) $((H / 2)) $((W / 2)) $((H / 5)) 80
adb shell input swipe $((W / 2)) $((H / 5)) $((W / 2)) $((H * 4 / 5)) 300 ; sleep 1
adb shell input swipe $((W / 2)) $((H / 5)) $((W / 2)) $((H * 4 / 5)) 300 ; sleep 2
echo "pull right after scrolling: $(fav_state) (expected closed)" >> "$OUT/favorites_pull.txt"
open_favorites
T=$(center_of "Calendar")
if [ -n "$T" ] ; then
	read -r TX TY <<< "$T"
	adb shell input swipe "$TX" "$TY" "$TX" $((TY + H / 2)) 1500 ; sleep 2
	echo "slow pull from a favorite: $(fav_state) (expected closed)" >> "$OUT/favorites_pull.txt"
fi
shot 72_favorites_after_gestures
adb shell "run-as $PKG sh -c 'rm -f files/favorites.txt'"
adb shell am force-stop "$PKG"

# ---------- Drag to organize (favorites, app list, folders) ----------
DR="$OUT/drag.txt"
# Long press then move in one go: <x1> <y1> <x2> <y2> <seconds held at the end>
drag_to() {
	adb shell "input motionevent DOWN $1 $2 ; sleep 0.9 ; input motionevent MOVE $(($1 + 25)) $(($2 + 25)) ; input motionevent MOVE $3 $4 ; sleep $5 ; input motionevent MOVE $(($3 + 2)) $4 ; input motionevent UP $(($3 + 2)) $4"
	sleep 2
}
open_drawer() { adb shell input swipe $((W / 2)) $((H * 2 / 3)) $((W / 2)) $((H / 4)) 150 ; sleep 2 ; }
files_list() { adb shell "run-as $PKG sh -c 'ls files'" | tr '\n' ' ' ; }
file_lines() { adb shell "run-as $PKG sh -c 'cat files/$1 2>/dev/null'" | tr -d '\r' ; }
# Half of a cell of the app list (4 columns by default): used to aim at the border of an element
CELL_HALF=$((W / 8))
adb shell "run-as $PKG sh -c 'rm -f files/drawer_order.txt files/folder_*.txt'"
restart_home

adb logcat -c
# A) Long press and release without moving: the usual menu opens
open_drawer
T=$(center_of "Calendar")
if [ -n "$T" ] ; then
	read -r TX TY <<< "$T"
	adb shell "input motionevent DOWN $TX $TY ; sleep 1.2 ; input motionevent UP $TX $TY"
	shot 80_menu_on_release
	adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 ; adb pull /sdcard/ui.xml "$OUT/ui_menu.xml" > /dev/null 2>&1
	echo "A menu on release, texts: $(grep -oE ' text="[^"]+"' "$OUT/ui_menu.xml" | head -12 | tr '\n' ' ')" >> "$DR"
	echo "A order file after menu: $(files_list) (expected no drawer_order.txt)" >> "$DR"
	adb shell input keyevent KEYCODE_BACK ; sleep 1
fi

# B) Reorder: drag Calendar to the border of Settings and rest there
restart_home ; open_drawer
shot 81_drawer_before
C=$(center_of "Calendar") ; S=$(center_of "Settings")
echo "B Calendar at $C, Settings at $S" >> "$DR"
if [ -n "$C" ] && [ -n "$S" ] ; then
	read -r CX CY <<< "$C" ; read -r SX SY <<< "$S"
	drag_to "$CX" "$CY" $((SX - CELL_HALF + 8)) "$SY" 1.0
	shot 82_drawer_after_reorder
	echo "B order file: $(file_lines drawer_order.txt | tr '\n' ' ')" >> "$DR"
	echo "B Calendar now at $(center_of "Calendar") (was $C)" >> "$DR"
fi

# C) Hold an app over another one: new folder dialog, then create
restart_home ; open_drawer
# (the emulator images do not all have a Camera app)
N1=Camera ; A1=$(center_of "$N1")
[ -z "$A1" ] && { N1=Contacts ; A1=$(center_of "$N1") ; }
A2=$(center_of "Clock")
echo "C $N1 at $A1, Clock at $A2" >> "$DR"
if [ -n "$A1" ] && [ -n "$A2" ] ; then
	read -r X1 Y1 <<< "$A1" ; read -r X2 Y2 <<< "$A2"
	drag_to "$X1" "$Y1" "$X2" "$Y2" 1.5
	shot 83_new_folder_dialog
	B=$(center_of "Create")
	[ -z "$B" ] && B=$(center_of "CREATE")
	echo "C dialog create button: ${B:-missing}" >> "$DR"
	if [ -n "$B" ] ; then adb shell input tap $B ; sleep 3 ; fi
	shot 84_after_folder_created
	echo "C files: $(files_list)" >> "$DR"
	echo "C folder content: $(file_lines folder_Folder.txt | tr '\n' ' ')" >> "$DR"
	echo "C order file: $(file_lines drawer_order.txt | tr '\n' ' ')" >> "$DR"
fi

# D) Drag an app out of the folder popup, back to the app list
F=$(center_of "Folder")
echo "D folder at: ${F:-missing}" >> "$DR"
if [ -n "$F" ] ; then
	adb shell input tap $F ; sleep 2
	shot 85_folder_open
	P=$(center_of "$N1")
	echo "D $N1 in popup at: ${P:-missing}" >> "$DR"
	if [ -n "$P" ] ; then
		read -r PX PY <<< "$P"
		# Leave the popup (it closes), then rest on the border of an element at the bottom of the list
		adb shell "input motionevent DOWN $PX $PY ; sleep 0.9 ; input motionevent MOVE $((PX + 25)) $((PY + 25)) ; sleep 2.5 ; input motionevent MOVE $((W / 8 + 4)) $((H * 4 / 5)) ; sleep 3 ; input motionevent MOVE $((W / 8 + 6)) $((H * 4 / 5)) ; input motionevent UP $((W / 8 + 6)) $((H * 4 / 5))" &
		sleep 2.5
		sleep 2
		shot 85b_during_drag_out
		wait ; sleep 2
		shot 86_after_drag_out
		echo "D folder content: $(file_lines folder_Folder.txt | tr '\n' ' ') (expected Clock only)" >> "$DR"
		echo "D $N1 in list at: $(center_of "$N1")" >> "$DR"
		adb logcat -d -s DLDrag:I View:W ViewRootImpl:W >> "$DR" 2>&1
	fi
fi

# E) Favorites: drag the first favorite to the border of the third one
adb shell am force-stop "$PKG"
head -4 "$OUT/favorites_input.txt" > "$OUT/favorites_drag.txt"
FAV_B64=$(base64 -w0 "$OUT/favorites_drag.txt")
adb shell "run-as $PKG sh -c 'echo $FAV_B64 | base64 -d > files/favorites.txt'"
echo "E favorites before: $(file_lines favorites.txt | tr '\n' ' ')" >> "$DR"
restart_home ; open_favorites
shot 87_favorites_before_drag
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 ; adb pull /sdcard/ui.xml "$OUT/ui_fav.xml" > /dev/null 2>&1
# Names of the favorites in screen order (items of the favorites list have a text and no resource id)
python3 - "$OUT/ui_fav.xml" > "$OUT/fav_items.txt" <<'PYEOF'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
for n in re.findall(r'<node [^>]*>', xml):
    t = re.search(r' text="([^"]+)"', n) ; b = re.search(r'bounds="([^"]*)"', n)
    if t and b and 'class="android.widget.TextView"' in n and t.group(1) not in ("Menu",):
        v = list(map(int, re.findall(r'\d+', b.group(1)))) ; print((v[0]+v[2])//2, (v[1]+v[3])//2, v[2]-v[0], t.group(1))
PYEOF
cat "$OUT/fav_items.txt" >> "$DR"
if [ "$(wc -l < "$OUT/fav_items.txt")" -ge 3 ] ; then
	read -r FX FY _ _ <<< "$(sed -n 1p "$OUT/fav_items.txt")"
	read -r GX GY GW _ <<< "$(sed -n 3p "$OUT/fav_items.txt")"
	drag_to "$FX" "$FY" $((GX - GW / 2 + 6)) "$GY" 1.0
	shot 88_favorites_after_drag
	echo "E favorites after: $(file_lines favorites.txt | tr '\n' ' ')" >> "$DR"
fi
adb shell "run-as $PKG sh -c 'rm -f files/favorites.txt files/drawer_order.txt files/folder_*.txt'"
adb shell am force-stop "$PKG"
cat "$DR"

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

# ---------- Delay before the results list appears (logged by the app) ----------
adb shell am force-stop "$PKG" ; adb shell input keyevent KEYCODE_HOME ; sleep 4 ; adb shell input tap $((W - 5)) $((H / 2)) ; sleep 1
P=$(center_of "Search…")
adb logcat -c
for N in 1 2 3 4 ; do
	[ -n "$P" ] && adb shell input tap $P
	sleep 3
	adb shell input keyevent KEYCODE_BACK ; adb shell input keyevent KEYCODE_BACK ; sleep 2
done
adb logcat -d -s DLSearch:I > "$OUT/list_delay.txt"

# Crash check
adb logcat -d -b crash > "$OUT/crash.log" || true
rm -f "$OUT/ui.xml"
exit 0
