package app.n_zik.compagnon.platform

import java.util.logging.Logger
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface

/**
 * The app's single-instance guard (the Linux D-Bus session bus). A second launch — the desktop
 * icon, a `flatpak run`, or the media notification's click (GNOME's shell activates the MPRIS
 * `DesktopEntry`, and the Flatpak desktop file execs `flatpak run`) — would otherwise start a
 * PARALLEL PLAYER: its tray / media surfaces stay off (the first instance owns the static bus
 * names — the SNI's "bus name taken" fallback, the MPRIS's "could not own" no-op), but it still
 * pairs with the phone and plays the current track a second time; the audio doubles, which reads
 * as "the volume jumped back to 100 % after I clicked a tray option" (live report 2026-10-12).
 * Instead, the duplicate re-shows the first instance's window (its MPRIS `Raise`) and exits.
 *
 * The marker is the MPRIS static bus name ([MPRIS_BUS_NAME]): the media surface owns it from the
 * first instance's start-up (before any session — see [MprisMediaControls]) and the session bus
 * drops it when that instance exits, so a live owner IS a live first instance. Without a session
 * bus the check is skipped (a headless session cannot be double-launched from the desktop); the
 * Windows / non-D-Bus paths never run it. The one unguarded edge: a first instance that started
 * while the bus was DOWN (its MPRIS is the no-op self-healing mode and never owned the name) is
 * not marked — a duplicate then plays along until the bus appears.
 */
object SingleInstance {
    private val log = Logger.getLogger("SingleInstance")

    /**
     * Whether this launch is a DUPLICATE (another live instance owns [MPRIS_BUS_NAME]). When it is,
     * the first instance's window is re-shown (best-effort `Raise`) and the caller must exit. One
     * short D-Bus exchange on its own connection; any bus failure reads as "the only instance"
     * (the check must never kill a legitimate launch).
     */
    fun isDuplicate(): Boolean {
        if (!System.getProperty("os.name", "").lowercase().startsWith("linux")) return false
        val conn = runCatching { DBusConnectionBuilder.forSessionBus().build() }.getOrNull() ?: return false
        // The WHOLE exchange is guarded: a bus error mid-call (a dying session bus, a proxy
        // rejection) must read as "the only instance" — never kill a legitimate launch
        return runCatching {
            val owns = conn.getRemoteObject(DBUS_BUS_NAME, DBUS_BUS_OBJECT_PATH, DBus::class.java)
                .NameHasOwner(MPRIS_BUS_NAME)
            duplicateLaunch(owns) {
                conn.getRemoteObject(MPRIS_BUS_NAME, MPRIS_OBJECT_PATH, RaiseIface::class.java).raise()
            }
        }.onFailure { log.warning("Single instance: the bus check failed (${it::class.simpleName}) — read as the only instance") }
            .getOrDefault(false)
            .also { runCatching { conn.close() } }
    }

    /**
     * The duplicate-launch outcome from its two inputs (pure — the tests pin it): [ownsName]
     * whether a first instance owns the marker name, [raiseFirst] the `Raise` on its window.
     * Returns `true` — exit — only when the name is owned; the raise is best-effort (a failed raise
     * still exits: the first instance is alive either way).
     */
    internal fun duplicateLaunch(ownsName: Boolean, raiseFirst: () -> Unit): Boolean {
        if (!ownsName) return false
        runCatching { raiseFirst() }
            .onFailure { log.warning("Single instance: could not raise the first window (${it::class.simpleName})") }
        log.info("Single instance: another instance owns $MPRIS_BUS_NAME — this launch exits")
        return true
    }
}

/** The first instance's `org.mpris.MediaPlayer2` root, reduced to the one member the duplicate calls. */
@DBusInterfaceName(MPRIS_ROOT_IFACE_NAME)
internal interface RaiseIface : DBusInterface {
    @DBusMemberName("Raise")
    fun raise()
}

private const val MPRIS_ROOT_IFACE_NAME = "org.mpris.MediaPlayer2"
private const val DBUS_BUS_NAME = "org.freedesktop.DBus"
private const val DBUS_BUS_OBJECT_PATH = "/org/freedesktop/DBus"
