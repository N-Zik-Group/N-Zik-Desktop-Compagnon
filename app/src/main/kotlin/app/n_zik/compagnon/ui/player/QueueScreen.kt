package app.n_zik.compagnon.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.arrow_down
import app.n_zik.compagnon.generated.resources.arrow_up
import app.n_zik.compagnon.generated.resources.cancel
import app.n_zik.compagnon.generated.resources.ellipsis_vertical
import app.n_zik.compagnon.generated.resources.play
import app.n_zik.compagnon.generated.resources.player_unknown_artist
import app.n_zik.compagnon.generated.resources.player_untitled
import app.n_zik.compagnon.generated.resources.player_waiting_state
import app.n_zik.compagnon.generated.resources.queue_clear
import app.n_zik.compagnon.generated.resources.queue_clear_confirm_text
import app.n_zik.compagnon.generated.resources.queue_clear_confirm_title
import app.n_zik.compagnon.generated.resources.queue_count
import app.n_zik.compagnon.generated.resources.queue_empty
import app.n_zik.compagnon.generated.resources.queue_move_down
import app.n_zik.compagnon.generated.resources.queue_move_up
import app.n_zik.compagnon.generated.resources.queue_play
import app.n_zik.compagnon.generated.resources.queue_remove
import app.n_zik.compagnon.generated.resources.queue_title
import app.n_zik.compagnon.generated.resources.queue_track_menu
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.player.PlayerRepository
import app.n_zik.compagnon.player.PlayerState
import app.n_zik.compagnon.player.SessionContract
import app.n_zik.compagnon.player.Track
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.semiBold
import app.n_zik.compagnon.ui.theme.typography
import app.n_zik.compagnon.ui.theme.uiRoundnessShape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The phone's queue in its effective order, after the phone's `Queue`: current track highlighted,
 * click → `jump`, per-track menu (Play, Move up, Move down, Remove), "Clear queue" with confirmation.
 * Queue actions are hidden without the `queue` feature. Nothing changes locally: the list only
 * follows the WS state.
 */
@Composable
fun QueueScreen(
    repository: PlayerRepository,
    state: PlayerState?,
    onCommand: CommandLauncher,
    modifier: Modifier = Modifier,
    live: Boolean = true,
) {
    val palette = colorPalette()
    // Without a live session no delta can confirm a change: queue actions wait for the reconnection
    val actions = live && SessionContract.FEATURE_QUEUE in repository.features
    var confirmClear by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.queue_title), style = typography().xl.semiBold, color = palette.text)
                if (state != null) {
                    Text(stringResource(Res.string.queue_count, state.queue.size), style = typography().xxs, color = palette.textSecondary)
                }
            }
            if (actions && state != null && state.queue.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Icon(painterResource(Res.drawable.trash), null, tint = palette.textSecondary, modifier = Modifier.padding(end = 6.dp))
                    Text(stringResource(Res.string.queue_clear), style = typography().xs, color = palette.text)
                }
            }
        }
        when {
            state == null -> CenteredText(stringResource(Res.string.player_waiting_state))
            state.queue.isEmpty() -> CenteredText(stringResource(Res.string.queue_empty))
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(state.currentIndex) {
                    if (state.currentIndex in state.queue.indices) listState.animateScrollToItem((state.currentIndex - 2).coerceAtLeast(0))
                }
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(state.queue, key = { index, track -> "$index:${track.id}" }) { index, track ->
                        QueueItem(
                            repository = repository,
                            track = track,
                            index = index,
                            lastIndex = state.queue.lastIndex,
                            current = index == state.currentIndex,
                            actions = actions,
                            onCommand = onCommand,
                        )
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(Res.string.queue_clear_confirm_title), style = typography().l.semiBold) },
            text = { Text(stringResource(Res.string.queue_clear_confirm_text), style = typography().xs) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onCommand { clearQueue() }
                }) { Text(stringResource(Res.string.queue_clear), color = palette.red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(Res.string.cancel), color = palette.text) }
            },
            containerColor = palette.background1,
            titleContentColor = palette.text,
            textContentColor = palette.textSecondary,
        )
    }
}

@Composable
private fun QueueItem(
    repository: PlayerRepository,
    track: Track,
    index: Int,
    lastIndex: Int,
    current: Boolean,
    actions: Boolean,
    onCommand: CommandLauncher,
) {
    val palette = colorPalette()
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(uiRoundnessShape())
            .background(if (current) palette.accent.copy(alpha = 0.15f) else palette.background0)
            .clickable(enabled = actions && !current) { onCommand { jump(index, track.id) } }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.widthIn(min = 28.dp), contentAlignment = Alignment.Center) {
            if (current) {
                Icon(painterResource(Res.drawable.play), null, tint = palette.accent, modifier = Modifier.padding(2.dp))
            } else {
                Text("${index + 1}", style = typography().xxs, color = palette.textSecondary, textAlign = TextAlign.Center)
            }
        }
        Artwork(repository, track.id, track.hasArtwork, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title.ifBlank { stringResource(Res.string.player_untitled) },
                style = typography().xs.semiBold,
                color = if (current) palette.accent else palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artists?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.player_unknown_artist),
                style = typography().xxs,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        track.durationMs?.let { Text(formatDuration(it), style = typography().xxs, color = palette.textSecondary) }
        if (actions) {
            Box {
                PlayerIconButton(
                    Res.drawable.ellipsis_vertical,
                    stringResource(Res.string.queue_track_menu),
                    onClick = { menu = true },
                    size = 36.dp,
                    iconSize = 18.dp,
                    tint = palette.textSecondary,
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuEntry(Res.drawable.play, stringResource(Res.string.queue_play)) {
                        menu = false
                        onCommand { jump(index, track.id) }
                    }
                    MenuEntry(Res.drawable.arrow_up, stringResource(Res.string.queue_move_up), enabled = index > 0) {
                        menu = false
                        onCommand { move(index, index - 1, track.id) }
                    }
                    MenuEntry(Res.drawable.arrow_down, stringResource(Res.string.queue_move_down), enabled = index < lastIndex) {
                        menu = false
                        onCommand { move(index, index + 1, track.id) }
                    }
                    MenuEntry(Res.drawable.trash, stringResource(Res.string.queue_remove)) {
                        menu = false
                        onCommand { remove(index, track.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(icon: DrawableResource, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = colorPalette()
    DropdownMenuItem(
        text = { Text(text, style = typography().xs, color = if (enabled) palette.text else palette.textDisabled) },
        leadingIcon = {
            Icon(painterResource(icon), null, tint = if (enabled) palette.textSecondary else palette.textDisabled, modifier = Modifier.padding(2.dp))
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = typography().s, color = colorPalette().textSecondary, textAlign = TextAlign.Center)
    }
}
