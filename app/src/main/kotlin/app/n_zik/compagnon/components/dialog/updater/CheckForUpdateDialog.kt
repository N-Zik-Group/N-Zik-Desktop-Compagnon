package app.n_zik.compagnon.components.dialog.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.theme.ModernBlackColorPalette
import app.n_zik.compagnon.components.theme.PureBlackColorPalette
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.updater.models.CheckUpdateState
import app.n_zik.compagnon.updater.services.Updater
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The "ask" startup dialog (port of the phone's `CheckForUpdateDialog` (phone's
 * `app/n_zik/android/components/dialog/updater/CheckForUpdateDialog.kt`), spec `spec-updater`
 * AD-9 — loop 2 closure): shown at startup when the user's check choice is [CheckUpdateState.Ask]
 * (the phone's `Skeleton.kt` call site, here the [app.n_zik.compagnon.App] startup effect),
 * replacing the v1 themed confirmation — the phone's full info card + its three option cards,
 * 1:1. Deliberate deviations (the desktop's conventions, as in [NewUpdateAvailableDialog]):
 * - the phone's `ColorPaletteMode.PitchBlack` check of the card-color branch is gone (the
 *   desktop's palettes have no pitch-black MODE — the Pure Black / Modern Black palettes keep
 *   the phone's `0xFF1A1A1A` card color);
 * - the phone's `checkBetaUpdates` parameter is gone (the channel is a build-time decision —
 *   the check is [Updater.checkForUpdate]'s plain, non-forced one, dialog on result);
 * - the "Turn off" option writes [CheckUpdateState.Off] through [Updater.setCheckUpdateState]
 *   (the desktop's `settings.json` binding — the phone's `rememberPreference` write).
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

        Dialog(onDismissRequest = { onDismiss() }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    // The phone's cap on wide screens (landscape phones, portrait tablets): the
                    // dialog does not span the full window
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                // Header with title
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                        animationSpec = tween(300),
                        initialScale = 0.9f,
                    ),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            },
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(Res.drawable.update),
                                    contentDescription = null,
                                    tint = colorPalette().accent,
                                    modifier = Modifier.size(32.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                BasicText(
                                    text = stringResource(Res.string.check_at_github_for_updates),
                                    style = typography().l.bold.copy(color = colorPalette().text),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                BasicText(
                                    text = stringResource(Res.string.when_an_update_is_available_you_will_be_asked_if_you_want_to_install_info),
                                    style = typography().xs.copy(color = colorPalette().textSecondary),
                                )
                                BasicText(
                                    text = stringResource(Res.string.but_these_updates_would_not_go_through),
                                    style = typography().xs.copy(color = colorPalette().textSecondary),
                                )
                                BasicText(
                                    text = stringResource(Res.string.you_can_still_turn_it_on_or_off_from_the_settings),
                                    style = typography().xs.copy(color = colorPalette().textSecondary),
                                )
                            }
                        }
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clickable {
                                onDismiss()
                                Updater.checkForUpdate()
                            },
                        colors = CardDefaults.cardColors(containerColor = colorPalette().accent),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            BasicText(
                                text = stringResource(Res.string.check_update),
                                style = typography().xs.semiBold.copy(color = Color.White),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.8f),
                            )
                            Icon(
                                painter = painterResource(Res.drawable.update),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clickable { onDismiss() },
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            },
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            BasicText(
                                text = stringResource(Res.string.cancel),
                                style = typography().xs.semiBold.copy(color = colorPalette().text),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.8f),
                            )
                            Icon(
                                painter = painterResource(Res.drawable.arrow_left),
                                contentDescription = null,
                                tint = colorPalette().accent,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clickable {
                                Updater.setCheckUpdateState(CheckUpdateState.Off)
                                onDismiss()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            },
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            BasicText(
                                text = stringResource(Res.string.turn_off),
                                style = typography().xs.semiBold.copy(color = colorPalette().text),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.8f),
                            )
                            Icon(
                                painter = painterResource(Res.drawable.close),
                                contentDescription = null,
                                tint = colorPalette().accent,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
