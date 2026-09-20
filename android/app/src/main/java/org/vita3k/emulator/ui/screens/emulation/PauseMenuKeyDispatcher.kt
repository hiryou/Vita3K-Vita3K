package org.vita3k.emulator.ui.screens.emulation

import android.view.KeyEvent

/**
 * Activity-level bridge so controller keys can drive the Compose pause menu.
 * SDL's surface keeps window focus; [org.vita3k.emulator.Emulator] intercepts
 * pad input while the menu is open and forwards it here.
 */
object PauseMenuKeyDispatcher {
    @Volatile
    var listener: ((Int) -> Boolean)? = null

    @JvmStatic
    fun dispatch(keyCode: Int): Boolean = listener?.invoke(keyCode) ?: false

    @JvmStatic
    fun isNavigationKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BACK -> true
            else -> false
        }
    }
}
