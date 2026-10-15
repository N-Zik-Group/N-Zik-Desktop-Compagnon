package app.n_zik.compagnon.components.dialog.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.dialog.common.ButtonRow
import app.n_zik.compagnon.components.dialog.common.DialogButton
import app.n_zik.compagnon.components.dialog.common.DialogFrame
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.updater.models.CheckUpdateState
import app.n_zik.compagnon.updater.services.Updater
import app.n_zik.compagnon.utils.bold
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The "ask" startup dialog (port of the phone's `CheckForUpdateDialog` (phone's
 * `app/n_zik/android/components/dialog/updater/CheckForUpdateDialog.kt`), spec `spec-updater`
 * AD-9 — loop 2 closure): shown at startup when the user's check choice is [CheckUpdateState.Ask]
 * (the phone's `Skeleton.kt` call site, here the [app.n_zik.compagnon.App] startup effect).
 * Deliberate deviations (the desktop's conventions, as in [NewUpdateAvailableDialog]):
 * - the phone's `checkBetaUpdates` parameter is gone (the channel is a build-time decision —
 *   the check is [Updater.checkForUpdate]'s plain, non-forced one, dialog on result);
 * - the "Turn off" option writes [CheckUpdateState.Off] through [Updater.setCheckUpdateState]
 *   (the desktop's `settings.json` binding — the phone's `rememberPreference` write).
 *
 * The chrome renders through the shared [DialogFrame] (420 dp cap — the updater width), and the
 * three option rows are shared [ButtonRow] pills in the phone's roles (spec
 * `spec-dialog-framework-port`): "Check for updates" = the accent `primary`, "Cancel" = the red
 * `danger`, "Turn off" = the gray secondary.
 *
 * Frame-migration deltas (spec `spec-dialog-framework-port`): the standalone card chrome is
 * removed (the content renders directly in the shared frame) and the metrics moved to the shared
 * pill language (pill spacing 12→8 dp, pill padding 16/10 dp, the updater icons 24→16 dp, the
 * 420 dp cap now includes the frame's 16 dp card padding); the header + the three options scroll
 * in a column bounded to the frame's remaining space on a short window (the frame body itself
 * stays non-scrollable).
 */
object CheckForUpdateDialog {

    private var isCanceled: Boolean by mutableStateOf(false)
    var isActive: Boolean by mutableStateOf(false)

    fun onDismiss() {
        isCanceled = true
        isActive = false
    }

    /** The dialog root (composed at the app root, the phone's `Skeleton.kt` call site). */
    @Composable
    fun Render() {
        if (isCanceled || !isActive) return

        DialogFrame(onDismiss = { onDismiss() }, maxWidth = 420.dp) {
            val colorPalette = colorPalette()

            // The header + the three option rows are static content: on a window whose 80 %
            // height cap falls below the ~460 dp of content, the bottom row would clip — the
            // frame body is not scrollable, so the content scrolls in this column bounded to
            // the frame's remaining space (the DefaultDialog idiom)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header with title
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                        animationSpec = tween(300),
                        initialScale = 0.9f,
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(Res.drawable.update),
                            contentDescription = null,
                            tint = colorPalette.accent,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BasicText(
                            text = stringResource(Res.string.check_at_github_for_updates),
                            style = typography().l.bold.copy(color = colorPalette.text),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        BasicText(
                            text = stringResource(Res.string.when_an_update_is_available_you_will_be_asked_if_you_want_to_install_info),
                            style = typography().xs.copy(color = colorPalette.textSecondary),
                        )
                        BasicText(
                            text = stringResource(Res.string.but_these_updates_would_not_go_through),
                            style = typography().xs.copy(color = colorPalette.textSecondary),
                        )
                        BasicText(
                            text = stringResource(Res.string.you_can_still_turn_it_on_or_off_from_the_settings),
                            style = typography().xs.copy(color = colorPalette.textSecondary),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Check for updates (the non-forced check — the phone's
                // `checkForUpdate(checkBetaUpdates = …)`, its channel replaced by the build-time one)
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(400)) + scaleIn(
                        animationSpec = tween(400),
                        initialScale = 0.9f,
                    ),
                ) {
                    ButtonRow(
                        buttons = listOf(
                            DialogButton(
                                text = stringResource(Res.string.check_update),
                                icon = Res.drawable.update,
                                onClick = {
                                    onDismiss()
                                    Updater.checkForUpdate()
                                },
                                primary = true,
                            ),
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Option 2: Cancel (skip this time)
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(500)) + scaleIn(
                        animationSpec = tween(500),
                        initialScale = 0.9f,
                    ),
                ) {
                    ButtonRow(
                        buttons = listOf(
                            DialogButton(
                                text = stringResource(Res.string.cancel),
                                icon = Res.drawable.arrow_left,
                                onClick = { onDismiss() },
                                danger = true,
                            ),
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Option 3: Turn off the startup check (the phone's `CheckUpdateState.Disabled`
                // write, here the desktop's [CheckUpdateState.Off] through the settings binding)
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                        animationSpec = tween(600),
                        initialScale = 0.9f,
                    ),
                ) {
                    ButtonRow(
                        buttons = listOf(
                            DialogButton(
                                text = stringResource(Res.string.turn_off),
                                icon = Res.drawable.close,
                                onClick = {
                                    Updater.setCheckUpdateState(CheckUpdateState.Off)
                                    onDismiss()
                                },
                            ),
                        ),
                    )
                }
            }
        }
    }
}
