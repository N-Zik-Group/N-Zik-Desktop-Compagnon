package app.n_zik.compagnon.components.player

import kotlin.math.abs

/*
 * Port of the phone's `app/n_zik/android/components/player/PendingSeekScrub.kt` (issue #881, Phase 3 Fix E
 * and 3.1): the seek target held on the bar and the label until the player confirms it. On the PC the
 * "player" is the phone: its position reaches the Compagnon by the WS state (contract §7), so the target is
 * held until that position converges on it, instead of snapping back to the stale position until the delta.
 */

/** Tolerance (ms) around the target within which the confirmed position counts as "caught up". */
internal const val PENDING_SEEK_SETTLE_TOLERANCE_MS = 500L

/** Safety timeout (ms) after which the held target is released even if the seek is never confirmed. */
internal const val PENDING_SEEK_RELEASE_TIMEOUT_MS = 10_000L

/** Polling interval (ms) of the settle check. */
internal const val PENDING_SEEK_POLL_INTERVAL_MS = 50L

/**
 * Whether the bar / label may stop holding [pendingTargetMs] and fall back to [playerPositionMs]: when the
 * position converges within [PENDING_SEEK_SETTLE_TOLERANCE_MS], or after [PENDING_SEEK_RELEASE_TIMEOUT_MS].
 */
internal fun shouldReleasePendingSeekPosition(
    pendingTargetMs: Long,
    playerPositionMs: Long,
    heldForMs: Long,
): Boolean =
    heldForMs >= PENDING_SEEK_RELEASE_TIMEOUT_MS ||
        abs(playerPositionMs - pendingTargetMs) <= PENDING_SEEK_SETTLE_TOLERANCE_MS

/**
 * The position a skip button (±5/10/30 s) adjusts from, evaluated at tap time: the in-flight drag, else a
 * still-pending seek target (consecutive taps chain from the target), else the live position.
 */
internal fun skipBasePosition(
    scrubbingMs: Long?,
    pendingTargetMs: Long?,
    pendingHeldForMs: Long,
    livePlayerPositionMs: Long,
): Long {
    scrubbingMs?.let { return it }
    if (pendingTargetMs != null &&
        !shouldReleasePendingSeekPosition(pendingTargetMs, livePlayerPositionMs, pendingHeldForMs)
    ) {
        return pendingTargetMs
    }
    return livePlayerPositionMs
}
