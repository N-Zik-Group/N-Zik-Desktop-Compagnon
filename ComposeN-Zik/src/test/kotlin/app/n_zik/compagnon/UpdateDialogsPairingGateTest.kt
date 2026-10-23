package app.n_zik.compagnon

import app.n_zik.compagnon.bridge.pairing.PairedStatus
import app.n_zik.compagnon.bridge.pairing.PairingRecord
import app.n_zik.compagnon.bridge.pairing.PairingState
import app.n_zik.compagnon.enums.PairingMode
import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The update-dialogs pairing gate (user request 2026-10-16): [updateDialogsVisible] shows the two
 * root update dialogs only AFTER the pairing screen, and the shared-source pin keeps the gate
 * around BOTH dialogs in `Main.kt` — dropping it for one of them, or reordering a render before
 * the gate, would let an update pop on top of the pairing UI again with every behavior test green.
 * The source-text idiom is `ChannelLabelSharedSourceTest`'s (the tests' working dir is the
 * `ComposeN-Zik/` module dir, so the file is read relative to it).
 */
class UpdateDialogsPairingGateTest {

    private val record = PairingRecord(
        serverIps = listOf("192.168.1.10"),
        serverPort = 3000,
        serverName = "Phone",
        deviceId = "device-1",
        deviceName = "PC",
    )

    @Test
    fun `paired states show the dialogs`() {
        // Any [PairedStatus]: the paired screen (Ok, Checking, the unreachable / other-PC /
        // incompatible / error variants) is no longer the pairing screen
        for (status in listOf(
            PairedStatus.Checking,
            PairedStatus.Ok,
            PairedStatus.Unreachable,
            PairedStatus.OtherActive("Other PC"),
            PairedStatus.Incompatible("1.0"),
            PairedStatus.Error(500, null),
        )) {
            assertTrue(updateDialogsVisible(PairingState.Paired(record, status)), "Paired($status) must show the dialogs")
        }
    }

    @Test
    fun `pairing screen states hide the dialogs`() {
        assertFalse(updateDialogsVisible(PairingState.Starting), "the startup Starting phase must hide the dialogs")
        assertFalse(
            updateDialogsVisible(PairingState.Unpaired(mode = PairingMode.Qr, deviceName = "PC", candidates = emptyList())),
            "Unpaired (the pairing screen) must hide the dialogs",
        )
        assertFalse(updateDialogsVisible(PairingState.Validating(manual = true)), "an in-flight Validating must hide the dialogs")
        assertFalse(updateDialogsVisible(PairingState.Validating(manual = false)), "an in-flight Validating must hide the dialogs")
        assertFalse(updateDialogsVisible(PairingState.Revoked), "Revoked (the re-pairing screen) must hide the dialogs")
    }

    @Test
    fun `main gates both dialogs behind the pairing state`() {
        // Shared-source pin: the gate is referenced in Main.kt and BOTH dialogs are composed
        // after it (inside it) — a dropped gate or a render moved before the gate fails here

        // while the behavior tests alone could not catch it
        val source = File("src/main/kotlin/app/n_zik/compagnon/Main.kt").readText()
        val gate = source.indexOf("updateDialogsVisible(")
        assertTrue(gate >= 0, "Main.kt must gate the update dialogs with updateDialogsVisible")
        val afterGate = source.substring(gate)
        assertTrue(
            afterGate.contains("NewUpdateAvailableDialog.Render()"),
            "the new-update dialog must be composed after (inside) the gate",
        )
        assertTrue(
            afterGate.contains("CheckForUpdateDialog.Render()"),
            "the ask startup dialog must be composed after (inside) the gate",
        )
    }
}
