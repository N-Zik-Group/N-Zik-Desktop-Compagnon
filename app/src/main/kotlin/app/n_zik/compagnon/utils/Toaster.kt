package app.n_zik.compagnon.utils

import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.alert
import app.n_zik.compagnon.generated.resources.checkmark
import app.n_zik.compagnon.generated.resources.close
import app.n_zik.compagnon.generated.resources.information
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource

/**
 * Port of the phone's `Toaster` (`app/kreate/android/me/knighthat/utils/Toaster.kt`): the short messages of
 * the app, typed ([Type]: colours and icon) and shown one after the other at the bottom centre, 109 dp up
 * (the phone's offset with its default floating navigation bar).
 *
 * The phone draws them with the `Toasty` library (`toast_layout.xml`, `toast_frame.9.png`): a pill tinted
 * with the type's colour at 90 % (the frame's alpha), 25 × 11 dp of padding, a 24 dp icon tinted with the
 * text colour and 8 dp from the text (no icon for [Type.NORMAL]), the text at 16 sp. A desktop window has
 * no system toast: [Host] draws them inside the window. Toasty's `sans-serif-condensed` face is Roboto
 * Condensed: it is used when Windows has it installed (no bundled font, no dependency), the default font
 * otherwise.
 *
 * Types, as the phone's equivalent calls (story 11c): "copied" `s` (`ClipBoard.kt` 28); the locator's
 * messages and "No song to shuffle" `i` (`Locator.kt` 79/88, `SongShuffler.kt` 52); a toolbar button's
 * description on long press `i` (`Descriptive.kt` 16); "No song found" and the failures `e`; the position
 * lock refusal `e` (`PositionLock.kt` 49). PC only: the command notices `e` (a truncated queue `w`) and the
 * local player's failures `e`.
 */
object Toaster {

    // floatingNavBarHeight (84.dp) + navBarBottomPadding (25.dp minimum)
    private const val FLOATING_BAR_HEIGHT_DP = 109

    /** `Toast.LENGTH_SHORT` / `Toast.LENGTH_LONG`, in ms. */
    const val LENGTH_SHORT = 2_000L
    const val LENGTH_LONG = 3_500L

    enum class Type(val background: Color, val foreground: Color, val iconId: DrawableResource?) {
        NORMAL(Color(108, 117, 125), Color.White, null),
        SUCCESS(Color(25, 135, 84), Color.White, Res.drawable.checkmark),
        INFO(Color(13, 110, 253), Color.White, Res.drawable.information),
        WARNING(Color(255, 193, 7), Color(33, 37, 41), Res.drawable.alert),
        ERROR(Color(220, 53, 69), Color.White, Res.drawable.close),
    }

    private class Toast(val message: String, val type: Type, val duration: Long)

    private val toasts = Channel<Toast>(Channel.UNLIMITED)
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)

    fun toast(message: String, type: Type = Type.NORMAL, duration: Long = LENGTH_SHORT) {
        toasts.trySend(Toast(message, type, duration))
    }

    private fun toast(messageId: StringResource, type: Type, duration: Long, vararg formatArgs: Any) {
        scope.launch { toast(formatMessage(messageId, *formatArgs), type, duration) }
    }

    fun n(message: String, duration: Long = LENGTH_SHORT) = toast(message, Type.NORMAL, duration)
    fun n(messageId: StringResource, vararg formatArgs: Any) = toast(messageId, Type.NORMAL, LENGTH_SHORT, *formatArgs)

    fun s(message: String, duration: Long = LENGTH_SHORT) = toast(message, Type.SUCCESS, duration)
    fun s(messageId: StringResource, vararg formatArgs: Any) = toast(messageId, Type.SUCCESS, LENGTH_SHORT, *formatArgs)

    fun i(message: String, duration: Long = LENGTH_SHORT) = toast(message, Type.INFO, duration)
    fun i(messageId: StringResource, vararg formatArgs: Any) = toast(messageId, Type.INFO, LENGTH_SHORT, *formatArgs)

    fun w(message: String, duration: Long = LENGTH_SHORT) = toast(message, Type.WARNING, duration)
    fun w(messageId: StringResource, vararg formatArgs: Any) = toast(messageId, Type.WARNING, LENGTH_SHORT, *formatArgs)

    fun e(message: String, duration: Long = LENGTH_SHORT) = toast(message, Type.ERROR, duration)
    fun e(messageId: StringResource, vararg formatArgs: Any) = toast(messageId, Type.ERROR, LENGTH_SHORT, *formatArgs)

    /** Draws the toasts at the bottom of the window (the desktop stand-in for the system toast). */
    @Composable
    fun BoxScope.Host() {
        var current by remember { mutableStateOf<Toast?>(null) }
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            for (toast in toasts) {
                current = toast
                visible = true
                delay(toast.duration)
                visible = false
                delay(TOAST_FADE_MS)
            }
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = FLOATING_BAR_HEIGHT_DP.dp),
        ) {
            current?.let { ToastContent(it) }
        }
    }

    @Composable
    private fun ToastContent(toast: Toast) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 600.dp)
                .heightIn(min = 44.dp)
                .background(toast.type.background.copy(alpha = 230 / 255f), RoundedCornerShape(22.dp))
                .padding(horizontal = 25.dp, vertical = 11.dp),
        ) {
            toast.type.iconId?.let { iconId ->
                Icon(
                    painter = painterResource(iconId),
                    contentDescription = null,
                    tint = toast.type.foreground,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp),
                )
            }
            Box {
                BasicText(
                    text = toast.message,
                    style = TextStyle(color = toast.type.foreground, fontSize = 16.sp, fontFamily = TOAST_FONT_FAMILY),
                )
            }
        }
    }

    private const val TOAST_FADE_MS = 300L

    /** Toasty's `sans-serif-condensed`, Roboto Condensed, read from the system when installed. */
    @OptIn(ExperimentalTextApi::class)
    private val TOAST_FONT_FAMILY: FontFamily by lazy {
        val installed = runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
                .any { it.equals(CONDENSED_FAMILY, ignoreCase = true) }
        }.getOrDefault(false)
        if (installed) FontFamily(SystemFont(CONDENSED_FAMILY)) else FontFamily.Default
    }

    private const val CONDENSED_FAMILY = "Roboto Condensed"
}

/**
 * Applies the format arguments of a desktop message, as the phone's own `Resources.getString(id, args)`
 * does. This compose version applies them in neither its non-composable `getString` nor its composable
 * `stringResource` (both return the raw message, its `%s` / `%d` staying visible), so they are applied
 * at the call site. An argument that does not match (wrong count / type) leaves the raw message, which
 * is what the phone's own `String.format` would not do either — a visible, unformatted line is safer
 * than a crash.
 */
internal fun formatText(message: String, vararg formatArgs: Any): String =
    if (formatArgs.isEmpty()) message else runCatching { String.format(message, *formatArgs) }.getOrDefault(message)

/** The [formatText] over the non-composable [getString] (the call sites are all suspend). */
internal suspend fun formatMessage(messageId: StringResource, vararg formatArgs: Any): String =
    formatText(getString(messageId), *formatArgs)
