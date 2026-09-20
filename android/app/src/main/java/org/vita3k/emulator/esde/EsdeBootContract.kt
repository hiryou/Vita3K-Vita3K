package org.vita3k.emulator.esde

/**
 * External boot contract used by frontends such as ES-DE.
 *
 * The caller provides a source Vita `.zip` ROM path in [PathExtra]. Vita3K resolves that zip to an
 * already-installed title ID, then boots it with [org.vita3k.emulator.Emulator.createLaunchIntent].
 */
object EsdeBootContract {
    const val BootRomAction = "org.vita3k.emulator.action.BOOT_ROM"
    const val PathExtra = "path"
    const val ErrorMessage = "Game must be preinstalled in Vita3K from a .zip ROM"
}
