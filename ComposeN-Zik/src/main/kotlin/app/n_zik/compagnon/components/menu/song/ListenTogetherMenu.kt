package app.n_zik.compagnon.components.menu.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The phone's default `serverLabel` (its `ListenTogetherMenu.kt` 252, the `selectedServer` of its
 * `ListenTogetherServers.kt` 20-38 — the default server "The Meowery — Poland"): a data value of the
 * phone's code, not a string resource — shown as-is on the Server URL entry.
 */
private const val DEFAULT_SERVER_LABEL = "The Meowery — Poland"

/**
 * Port of `ListenTogetherMenu` (phone's
 * `app/n_zik/android/components/ui/screens/listentogether/ListenTogetherMenu.kt`, list rendering
 * 473-690, the disconnected state: § Connection 478-483, § Room 609-624, § Settings 635-689) — the
 * phone's room page, opened by the `listen_together` entries of the song menu (spec
 * `spec-fill-pc-ui-gaps-menus` #48).
 *
 * The phone's Listen Together is a phone-side network feature: the contract has no route, the PC is
 * never connected. Only the phone's disconnected state is rendered (disconnected, no room code, no
 * waiting) and everything is inert — every entry keeps the phone's icon, title and subtitle (the
 * phone's default-state static values) with a no-op click, like the phone's own rows.
 *
 * Dropped against the phone: the trailing [ToggleSwitch]es of its four toggle entries (its 653, 661,
 * 668, 675) — an inert switch would show a state nothing reads; and its connected / waiting states
 * (room code, connected users, join requests, leave room, loading rows) — unreachable on the PC.
 *
 * Note: the spec counts "7 entrées" in § Settings; the phone's code (its 635-689) renders 8 — server
 * URL, username, the two auto-approvals, the volume sync, the history, the logs and the blocked
 * users. The phone's code is ported as-is, all eight.
 */
@Composable
fun ListenTogetherMenu() {
    val palette = colorPalette()

    @Composable
    fun SettingIcon(icon: DrawableResource, color: Color = palette.accent) {
        // The phone's own `SettingIcon` (its `ListenTogetherMenu.kt` grid counterpart 980-988): the
        // 32 dp box, the 18 dp icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = color.copy(alpha = 0.1f),
                    shape = uiRoundnessShape(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                tint = color,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    ListMenu.Menu(title = stringResource(Res.string.listen_together)) {
        // ── Connection (the phone's 478-487) ───────────────────────────────
        ListMenu.SectionTitle(stringResource(Res.string.listen_together_connection))
        // The phone's `ConnectionStatusRow` (its 1014-1054), disconnected: the 10 dp dot and the
        // status text in `textDisabled`
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(palette.textDisabled),
            )
            Text(
                text = stringResource(Res.string.listen_together_disconnected),
                style = typography().s.semiBold,
                color = palette.textDisabled,
            )
        }
        // The phone's Connect entry (its 483-487): inert — no contract route, the PC never connects
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_connect),
            icon = { SettingIcon(Res.drawable.link) },
            onClick = {},
        )

        // ── Room: create / join (the phone's 609-629) ──────────────────────
        ListMenu.SectionTitle(stringResource(Res.string.listen_together_room))
        // The phone's Create entry (its 618-623): inert
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_create_room),
            subtitle = stringResource(Res.string.listen_together_create_room_desc),
            icon = { SettingIcon(Res.drawable.people) },
            onClick = {},
        )
        // The phone's Join entry (its 624-629): inert
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_join_room),
            subtitle = stringResource(Res.string.listen_together_enter_room_code),
            icon = { SettingIcon(Res.drawable.people) },
            onClick = {},
        )

        // ── Settings (the phone's 635-689): the phone's eight entries, its
        // default-state static subtitles ───────────────────────────────────
        ListMenu.SectionTitle(stringResource(Res.string.settings))
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_server_url),
            // The phone's `serverLabel` (its 252): the default server, its default state
            subtitle = DEFAULT_SERVER_LABEL,
            icon = { SettingIcon(Res.drawable.server) },
            onClick = {},
        )
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_username),
            // The phone's `usernameLabel` (its 253): an unset username
            subtitle = stringResource(Res.string.listen_together_not_set),
            icon = { SettingIcon(Res.drawable.person) },
            onClick = {},
        )
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_auto_approval_joins),
            subtitle = stringResource(Res.string.listen_together_auto_approval_joins_desc),
            icon = { SettingIcon(Res.drawable.checkmark) },
            onClick = {},
        )
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_auto_approval_suggestions),
            subtitle = stringResource(Res.string.listen_together_auto_approval_suggestions_desc),
            icon = { SettingIcon(Res.drawable.musical_notes) },
            onClick = {},
        )
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_sync_volume),
            subtitle = stringResource(Res.string.listen_together_sync_volume_desc),
            icon = { SettingIcon(Res.drawable.volume_up) },
            onClick = {},
        )
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_history),
            subtitle = stringResource(Res.string.listen_together_history_desc),
            icon = { SettingIcon(Res.drawable.history) },
            onClick = {},
        )
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_view_logs),
            subtitle = stringResource(Res.string.listen_together_view_logs_desc),
            icon = { SettingIcon(Res.drawable.bugs) },
            onClick = {},
        )
        ListMenu.Entry(
            text = stringResource(Res.string.listen_together_blocked_users),
            // The phone's `blockedLabel` (its 258-263): no blocked user, its default state
            subtitle = stringResource(Res.string.listen_together_no_blocked_users),
            icon = { SettingIcon(Res.drawable.close, palette.red) },
            onClick = {},
        )
    }
}
