package app.n_zik.compagnon.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import java.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Port of `formatAsTime` (phone's `app/it/fast4x/rimusic/utils/Utils.kt` 439): `"45m 12s"`, `"1h 5m 3s"`. */
fun formatAsTime(millis: Long): String {
    val timePart1 = Duration.ofMillis(millis).toMinutes().minutes
    val timePart2 = Duration.ofMillis(millis).seconds % 60

    return "$timePart1 ${timePart2}s"
}

/**
 * Desktop stand-in for the phone's long press: a right click runs [onClick] (no phone original; the
 * mouse has no long press worth using).
 */
fun Modifier.onSecondaryClick(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) {
        this
    } else {
        pointerInput(onClick) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        event.changes.forEach { it.consume() }
                        onClick()
                    }
                }
            }
        }
    }

/**
 * Port of `formatAsDuration` (phone's `app/it/fast4x/rimusic/utils/Utils.kt` 415):
 * `DateUtils.formatElapsedTime(millis / 1000).removePrefix("0")`, i.e. `m:ss`, or `h:mm:ss` from one hour.
 */
fun formatAsDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1_000)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** Port of the title prefixes (phone's `app/it/fast4x/rimusic/Utils.kt` 3-10). */
const val PINNED_PREFIX = "pinned:"
const val MODIFIED_PREFIX = "modified:"
const val MONTHLY_PREFIX = "monthly:"
const val EXPLICIT_PREFIX = "e:"
const val LOCAL_KEY_PREFIX = "local:"
const val YTP_PREFIX = "account:"

/**
 * Port of `cleanPrefix` (phone's `app/it/fast4x/rimusic/Utils.kt` 17): assumption: all prefixes end with
 * ":" and have at least 1 (other) character. Removes a "prefix of prefixes" including multiple times the
 * same prefix (at different locations).
 */
fun cleanPrefix(text: String): String {
    val splitText = text.split(":")
    var i = 0
    while (i < splitText.size - 1) {
        if ("${splitText[i]}:" !in listOf(PINNED_PREFIX, MODIFIED_PREFIX, MONTHLY_PREFIX,
                EXPLICIT_PREFIX, LOCAL_KEY_PREFIX, YTP_PREFIX)) {
            break
        }
        i++
    }
    if (i >= splitText.size) return ""
    return splitText.subList(i, splitText.size).joinToString(":")
        .removePrefix("\uD83C\uDD74 ")
}

/** Port of `String.hasExplicitPrefix` (phone's `app/it/fast4x/rimusic/Utils.kt` 32). */
fun String.hasExplicitPrefix(): Boolean {
    val splitText = this.split(":")
    var i = 0
    while (i < splitText.size - 1) {
        val prefix = "${splitText[i]}:"
        if (prefix == EXPLICIT_PREFIX) return true
        if (prefix !in listOf(PINNED_PREFIX, MODIFIED_PREFIX, MONTHLY_PREFIX,
                EXPLICIT_PREFIX, LOCAL_KEY_PREFIX, YTP_PREFIX)) {
            break
        }
        i++
    }
    return false
}
