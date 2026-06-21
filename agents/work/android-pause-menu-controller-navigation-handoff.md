# Android Pause Menu Controller Navigation Handoff

## Goal

Add basic controller navigation for the Android in-game pause menu in Vita3K.

Requested minimum scope:

- directional keys
- select / confirm
- go back / close

The concrete user problem:

- pressing the controller guide / mode button already opens the Vita3K Android pause menu
- once the menu is open, the controller cannot navigate it
- touchscreen works, but controller-only use on Android handhelds is incomplete

## Repo / Codebase

Checked in local fork:

- `/Users/longn/github/forks/vita3k/Vita3K`

This is the correct Vita3K codebase and includes the Android app plus native SDL/JNI layer.

## Current Behavior

### Pause menu opening already exists

Native SDL path:

- [vita3k/android/jni/main_android.cpp](/Users/longn/github/forks/vita3k/Vita3K/vita3k/android/jni/main_android.cpp:484)

Relevant code:

- `SDL_EVENT_GAMEPAD_BUTTON_DOWN`
- if `event.gbutton.button == SDL_GAMEPAD_BUTTON_GUIDE`
- call `open_pause_menu()`

Android activity path:

- [android/app/src/main/java/org/vita3k/emulator/Emulator.java](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/Emulator.java:318)
- [android/app/src/main/java/org/vita3k/emulator/Emulator.java](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/Emulator.java:629)

Relevant code:

- `dispatchKeyEvent(...)` intercepts `KEYCODE_BUTTON_MODE`
- calls `openPauseMenuFromController()`

### Pause menu UI already exists

Compose pause menu:

- [android/app/src/main/java/org/vita3k/emulator/ui/screens/emulation/EmulationPauseMenu.kt](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/ui/screens/emulation/EmulationPauseMenu.kt:192)

This is a Compose drawer-style UI shown over emulation.

### Session state already exists

- [android/app/src/main/java/org/vita3k/emulator/ui/viewmodel/EmulationSessionViewModel.kt](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/ui/viewmodel/EmulationSessionViewModel.kt:14)

Relevant fields / methods:

- `showMenu`
- `isPaused`
- `openMenu(...)`
- `closeMenu(...)`
- `togglePause(...)`
- `handleBackPressed(...)`

Important behavior:

- when menu opens, `setNativeInputIntercepted(true)` is used
- controller overlay is hidden while the menu is open

So the emulator side already knows when the pause menu is active.

## Main Finding

The missing piece is not guide-button detection.

The missing piece is controller navigation inside the Android Compose pause menu.

I searched the pause menu UI for:

- `onPreviewKeyEvent`
- `onKeyEvent`
- `focusable`
- `FocusRequester`
- DPAD handling

Result:

- no controller / DPAD navigation wiring is present in `EmulationPauseMenu.kt`

I also checked for existing Compose focus patterns elsewhere:

- [android/app/src/main/java/org/vita3k/emulator/ui/screens/AppsListScreen.kt](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/ui/screens/AppsListScreen.kt:747)

That file uses `FocusRequester`, but only for text search focus, not gamepad menu navigation.

## Implementation Shape

### Straight conclusion

This feature looks implementable without deep emulator-core changes.

It is mostly Android UI / input routing work.

### Most likely files to touch

- [android/app/src/main/java/org/vita3k/emulator/Emulator.java](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/Emulator.java)
- [android/app/src/main/java/org/vita3k/emulator/ui/screens/emulation/EmulationPauseMenu.kt](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/ui/screens/emulation/EmulationPauseMenu.kt)
- maybe [android/app/src/main/java/org/vita3k/emulator/ui/viewmodel/EmulationSessionViewModel.kt](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/ui/viewmodel/EmulationSessionViewModel.kt)

### Recommended first-pass approach

Do not try to solve full generic gamepad navigation for every settings subtree first.

First-pass target:

- make the pause drawer navigable with controller
- support:
  - up / down / left / right
  - confirm
  - back / close

Best practical strategy:

1. Intercept controller key events in `Emulator.java` while `sessionViewModel.getUiState().getShowMenu()` is true.
2. Route those keys into the Compose pause menu host.
3. In `EmulationPauseMenu.kt`, maintain a small explicit selection model for:
   - top tab row
   - main actions in Session tab
   - close / back behavior
4. Only after this works, consider deeper settings-tab traversal.

Why explicit selection state is better for v1:

- the pause menu already has many clickable controls
- there is no current focus model
- generic Compose focus retrofitting across all nested controls will sprawl
- the user only asked for directional keys + select + goback

## Suggested Key Mapping

When pause menu is visible:

- `KEYCODE_DPAD_UP`
- `KEYCODE_DPAD_DOWN`
- `KEYCODE_DPAD_LEFT`
- `KEYCODE_DPAD_RIGHT`
- confirm:
  - `KEYCODE_BUTTON_A`
  - optionally `KEYCODE_ENTER` / `KEYCODE_NUMPAD_ENTER`
- back:
  - `KEYCODE_BACK`
  - optionally `KEYCODE_BUTTON_B`

Notes:

- `KEYCODE_BUTTON_MODE` already opens the menu
- avoid sending these inputs down to normal emulation while menu is open

## Evidence / Code Pointers

### Activity key interception

- [Emulator.java:299](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/Emulator.java:299)
- [Emulator.java:318](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/Emulator.java:318)

Current behavior:

- back is already special-cased
- guide / mode is already special-cased
- no DPAD / confirm routing for pause menu currently exists

### Native SDL event path

- [main_android.cpp:484](/Users/longn/github/forks/vita3k/Vita3K/vita3k/android/jni/main_android.cpp:484)

Current behavior:

- guide button opens pause menu
- no menu-navigation routing there

### Compose pause menu host

- [EmulationPauseMenu.kt:161](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/ui/screens/emulation/EmulationPauseMenu.kt:161)
- [EmulationPauseMenu.kt:218](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/ui/screens/emulation/EmulationPauseMenu.kt:218)
- [EmulationPauseMenu.kt:430](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/ui/screens/emulation/EmulationPauseMenu.kt:430)
- [EmulationPauseMenu.kt:568](/Users/longn/github/forks/vita3k/Vita3K/android/app/src/main/java/org/vita3k/emulator/ui/screens/emulation/EmulationPauseMenu.kt:568)

Current behavior:

- menu visibility state exists
- tab state exists
- buttons/chips are clickable
- no controller focus / DPAD behavior exists

## Related Existing Issues

I did not find an exact existing GitHub issue specifically for:

- "controller should navigate the Android pause menu with DPAD/select/back"

Closest relevant issue links found:

1. `Add "Input" settings tab and UI-based "Add Gamepad Mapping"`:
   - https://github.com/Vita3K/Vita3K-Android/issues/766
   - This is broader controller-management work, not pause-menu navigation specifically.

2. `Controller buttons incorrectly mapped or not registering input`:
   - https://github.com/Vita3K/Vita3K-Android/issues/764
   - Relevant as a current Android controller bug bucket.

3. `"Back" button reads as "Select"`:
   - https://github.com/Vita3K/Vita3K-Android/issues/601
   - Relevant because the reporter explicitly mentions wanting emulator-menu-style behavior from a physical back button.

4. `Remember choice on "Allow Vita3K to access Controller?" prompt`:
   - https://github.com/Vita3K/Vita3K-Android/issues/639
   - Relevant only as another Android controller UX issue.

If a future agent finds a newer or exact issue for pause-menu controller navigation, add it here and prefer that link.

## Recommended Next Step

If implementation starts:

1. Add pause-menu-specific key dispatch in `Emulator.java`.
2. Add a tiny controller navigation model in `EmulationPauseMenu.kt`.
3. Scope v1 to:
   - tab switching
   - Session tab actions
   - close/back
4. Test on-device with:
   - guide button opens menu
   - DPAD moves selection
   - A confirms
   - back / B closes

## Status

No code changes made yet in this fork for this feature.

This note is intended to let the next agent start implementation directly instead of rediscovering the Android/native/UI split.
