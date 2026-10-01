package app.n_zik.compagnon.pairing

import kotlinx.coroutines.delay
import java.util.logging.Logger

/**
 * The terminal "device revoked" rule of contract §2 / §4.7, shared by every caller:
 * - REST: a `401 DEVICE_REVOKED` is confirmed by **one** retry [confirmDelayMs] later; a second
 *   consecutive `401 DEVICE_REVOKED` means revoked ([confirmRest]);
 * - WS close `4003`, or an audio `401 DEVICE_REVOKED` outside the kick window (story 11): revoked at
 *   once ([revokeNow]).
 *
 * Revoked → [onRevoked] erases the credential and shows the re-pairing screen. No other `401` ever
 * erases the pairing.
 */
class RevocationPolicy(
    private val onRevoked: suspend () -> Unit,
    private val confirmDelayMs: Long = BridgeContract.REVOCATION_CONFIRM_DELAY_MS,
) {
    private val log = Logger.getLogger("RevocationPolicy")

    /** Result of [confirmRest]: the last answer, and whether the pairing was revoked (and erased). */
    data class Outcome<T>(val result: T, val revoked: Boolean)

    /**
     * Runs [call]; if [isRevoked] says it answered `401 DEVICE_REVOKED`, waits [confirmDelayMs] and
     * runs it once more. Only a second `DEVICE_REVOKED` triggers [onRevoked].
     */
    suspend fun <T> confirmRest(call: suspend () -> T, isRevoked: (T) -> Boolean): Outcome<T> {
        val first = call()
        if (!isRevoked(first)) return Outcome(first, revoked = false)
        log.info("401 DEVICE_REVOKED, confirming in $confirmDelayMs ms")
        delay(confirmDelayMs)
        val second = call()
        if (!isRevoked(second)) return Outcome(second, revoked = false)
        revokeNow()
        return Outcome(second, revoked = true)
    }

    /** Terminal state reached by a signal that needs no confirmation (WS `4003`, audio `401`, story 11). */
    suspend fun revokeNow() {
        log.info("Pairing revoked by the phone: credential erased")
        onRevoked()
    }
}
