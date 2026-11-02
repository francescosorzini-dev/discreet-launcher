# Fork changelog

Changes made in this personal fork of [falzonv/discreet-launcher](https://github.com/falzonv/discreet-launcher) (last original version: v7.8.4).
Newest first. The same notes are shown inside the app (*About > Changelog*) and in each [release](https://github.com/francescosorzini-dev/discreet-launcher/releases).

## v7.15.1 (95) - 02/11/2026

**CORRECTIONS**

- Favorites panel and app list: pulling to close could get stuck (the list or a touched favorite kept the gesture); the pull is now watched before any element of the screen

**IMPROVEMENTS**

- A quick flick downwards also closes the panel, without pulling it far

## v7.15.0 (94) - 01/11/2026

**NEW THINGS**

- Drag to organize: long press an app or folder, it gets picked up; release without moving to open the usual menu, or drag it
- Favorites: drag to change their position
- App list: drag apps and folders to reorder them freely
- App list: drop an app on a folder to put it inside
- App list: hold an app over another one for half a second to create a new folder with both
- Folders: drag an app out of an open folder to put it back in the list or move it to another folder
- Once the list has been reordered by dragging, newly installed apps appear at its top
- Settings > Operation: "Sort apps alphabetically" to forget the order chosen by dragging

**IMPROVEMENTS**

- The order of the list is included in the export / import of settings

## v7.14.3 (93) - 01/11/2026

**CORRECTIONS**

- Search: the results list could wait up to 2.5 s with the keyboard already open; the keyboard is now detected right away, and the list appears after 0.4 s at most in any case

## v7.14.2 (92) - 31/10/2026

**CORRECTIONS**

- Favorites panel: pulling it is now handled by its scrolling container, so it is no longer interrupted with many favorites
- Pulling a panel starts only beyond the touch threshold: a tap with a slightly moving finger opens the app again
- Search: the results list could still wait 1.5 s when the keyboard opened instantly

## v7.14.1 (91) - 31/10/2026

**CORRECTIONS**

- Favorites panel: it closes by pulling it downwards from the beginning of the list, available as soon as it opens (with many favorites, it was necessary to scroll to the end first)

## v7.14.0 (90) - 31/10/2026

**IMPROVEMENTS**

- The app list and the favorites panel close by pulling them beyond the edge of their list and releasing: the panel follows the finger, closes beyond a threshold and goes back in place otherwise. Small slides of the finger while tapping an app no longer close them

## v7.13.1 (89) - 31/10/2026

**IMPROVEMENTS**

- The slow appearance is 3 times faster, and the same effect is used when closing the search, the folders, the favorites panel and the app list

**CORRECTIONS**

- First opening of the search: the results list appeared only after 1.5 s

## v7.13.0 (88) - 31/10/2026

**IMPROVEMENTS**

- Search: the keyboard opens first, the results list is added only once the keyboard is open, then appears slowly
- Same slow appearance for the favorites panel, the app list and the folders

## v7.12.7 (87) - 31/10/2026

**IMPROVEMENTS**

- Opening the search: every later move caused by the keyboard (late opening, suggestions bar) is now also animated, and the list never shows a wrong position for a moment

## v7.12.6 (86) - 31/10/2026

**IMPROVEMENTS**

- Opening the search: slower slide of the results (1.4 s instead of 0.35 s)

## v7.12.5 (85) - 31/10/2026

**IMPROVEMENTS**

- Opening the search: the results slide up from the keyboard to their place with a slowed-down movement

## v7.12.4 (84) - 31/10/2026

**IMPROVEMENTS**

- Opening the search: the results appear directly in their final place, with the most used apps already shown, without visible scrolling

## v7.12.3 (83) - 31/10/2026

**IMPROVEMENTS**

- Smoother opening of the search: the results appear with a short fade once the keyboard is open, instead of jumping up

## v7.12.2 (82) - 31/10/2026

**CORRECTIONS**

- One-handed use: touching an app in the search results (empty search) moved the list and did not open the app; the visual feedback of a touch no longer changes the layout

## v7.12.1 (81) - 31/10/2026

**CORRECTIONS**

- One-handed use: the search results no longer jump up when touching an app (the search now closes before the app starts)

## v7.12.0 (80) - 31/10/2026

**NEW THINGS**

- One-handed use (Operation settings): search results start from the bottom, on the side of the selected hand; with the right hand, the rows of the app list are filled from the right

## v7.11.1 (79) - 31/10/2026

**IMPROVEMENTS**

- Option to reset the app usage statistics (Operation settings)
- New texts of this fork translated in all the languages of the app

## v7.11.0 (78) - 31/10/2026

**NEW THINGS**

- Without search text, the search shows the 8 apps you open most often first

**IMPROVEMENTS**

- Optimized APK: smaller and faster than the previous debug builds
- Automated tests of the search run on every build

## v7.10.2 (77) - 31/10/2026

**CORRECTIONS**

- Android 15: the first item of the settings screens was hidden under the title bar

## v7.10.1 (76) - 31/10/2026

**IMPROVEMENTS**

- The search field is now placed just under the clock and follows its position, alignment and colors (at the bottom of the home screen without clock)
- Magnifying glass icon in the search field

**CORRECTIONS**

- Search field overlapping the clock in reversed interface

## v7.10.0 (75) - 31/10/2026

**NEW THINGS**

- Search field fixed at the bottom of the home screen (tap to search, can be disabled in settings)
- Search by the initials of the words of a name ("gm" for Google Maps)
- Folder names act as labels in the search ("bank" finds the apps of the "Bank" folder)

**IMPROVEMENTS**

- Apps you open often come first in the search results
- Updated for Android 15

## v7.9.1 (74) - 30/10/2026

**IMPROVEMENTS**

- About dialog mentions this fork and links to its repository
- APKs are now always signed with the same key, so new builds update the installed app

## v7.9.0 (73) - 30/10/2026

**IMPROVEMENTS**

- Search applications by any part of their name, not only by the beginning (results starting with the search come first)
