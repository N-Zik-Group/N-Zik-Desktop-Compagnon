package app.n_zik.compagnon.components.player.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.album
import app.n_zik.compagnon.generated.resources.explicit
import app.n_zik.compagnon.generated.resources.people
import app.n_zik.compagnon.generated.resources.person
import app.n_zik.compagnon.generated.resources.unknown
import app.n_zik.compagnon.generated.resources.unknown_artist
import app.n_zik.compagnon.generated.resources.unknown_title
import app.n_zik.compagnon.generated.resources.value_copied
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.utils.cleanPrefix
import app.n_zik.compagnon.utils.onSecondaryClick
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `InfoAlbumAndArtistModern` (phone's `app/it/fast4x/rimusic/ui/screens/player/components/controls/
 * Modern.kt` 110-398), the default player info (`playerInfoShowIcon` on by default):
 * - the 26 dp album icon then an 8 dp spacer (`Modern.kt` 149-170), the 18 dp explicit badge (`isExplicit`,
 *   contract 1.3 `Track.isExplicit`, 197-205), the title in l.bold;
 * - 10 dp below, the 24 dp artist icon (2 dp start padding) then a 12 dp spacer (310-333), the artists in
 *   m.bold (`person` for one artist, `people` otherwise, `unknown` without a text).
 * A long press (a right click on the PC) copies the text, with the phone's "copied" toast.
 *
 * The icons are shown like the phone's navigable case (text colour) but have no action: the contract gives
 * no album or artist id for a track. Dropped: the like button (`Modern` controls only, not the default); the
 * text outline, transparent by default.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoAlbumAndArtistModern(
    title: String?,
    artist: String?,
    isExplicit: Boolean,
    disableScrollingText: Boolean = false,
) {
    val unknownTitle = stringResource(Res.string.unknown_title)
    val unknownArtist = stringResource(Res.string.unknown_artist)
    val clipboard = LocalClipboardManager.current
    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toaster.s(Res.string.value_copied)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth(),
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {

            IconButton(
                icon = if (title.isNullOrBlank() || title == unknownTitle || title == "Unknown Title") Res.drawable.unknown else Res.drawable.album,
                color = colorPalette().text,
                onClick = {},
                modifier = Modifier
                    .size(26.dp),
            )

            Spacer(
                modifier = Modifier
                    .width(8.dp),
            )

            var modifierTitle = Modifier
                .clip(uiRoundnessShape())
                .onSecondaryClick { copy(cleanPrefix(title ?: "")) }
                .combinedClickable(
                    indication = ripple(bounded = true),
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                    onLongClick = { copy(cleanPrefix(title ?: "")) },
                )

            if (!disableScrollingText) modifierTitle = modifierTitle.basicMarquee()
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f),
            ) {
                if (isExplicit) {
                    IconButton(
                        icon = Res.drawable.explicit,
                        color = colorPalette().text,
                        onClick = {},
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box {
                    BasicText(
                        text = cleanPrefix(title ?: ""),
                        style = TextStyle(
                            color = colorPalette().text,
                            fontStyle = typography().l.bold.fontStyle,
                            fontWeight = typography().l.bold.fontWeight,
                            fontSize = typography().l.bold.fontSize,
                            fontFamily = typography().l.bold.fontFamily,
                        ),
                        maxLines = 1,
                        modifier = modifierTitle,
                    )
                }
            }
        }
    }

    Spacer(
        modifier = Modifier
            .height(10.dp),
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth(),
    ) {

        IconButton(
            icon = when {
                artist.isNullOrBlank() || artist == unknownArtist || artist == "Unknown Artist" -> Res.drawable.unknown
                artist.contains(",") -> Res.drawable.people
                else -> Res.drawable.person
            },
            color = colorPalette().text,
            onClick = {},
            modifier = Modifier
                .size(24.dp)
                .padding(start = 2.dp),
        )

        Spacer(
            modifier = Modifier
                .width(12.dp),
        )

        var modifierArtist = Modifier
            .clip(uiRoundnessShape())
            .onSecondaryClick { copy(artist ?: "") }
            .combinedClickable(
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
                onLongClick = { copy(artist ?: "") },
            )

        if (!disableScrollingText) modifierArtist = modifierArtist.basicMarquee()
        Box {
            BasicText(
                text = artist ?: "",
                style = TextStyle(
                    color = colorPalette().text,
                    fontStyle = typography().m.bold.fontStyle,
                    fontSize = typography().m.bold.fontSize,
                    fontWeight = typography().m.bold.fontWeight,
                    fontFamily = typography().m.bold.fontFamily,
                ),
                maxLines = 1,
                modifier = modifierArtist,
            )
        }
    }
}
