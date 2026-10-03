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
import app.n_zik.compagnon.generated.resources.explicit
import app.n_zik.compagnon.generated.resources.value_copied
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.utils.cleanPrefix
import app.n_zik.compagnon.utils.hasExplicitPrefix
import app.n_zik.compagnon.utils.onSecondaryClick

/**
 * Port of `InfoAlbumAndArtistModern` (phone's `app/it/fast4x/rimusic/ui/screens/player/components/controls/
 * Modern.kt` 110-398), the default player info: the title (l.bold, with the 18 dp explicit badge before it
 * when the title carries the `e:` prefix) then, 10 dp below, the artists (m.bold), aligned left, scrolling
 * when too long. A long press (a right click on the PC) copies the text, with the phone's "copied" toast.
 *
 * Dropped: the album / artist icons and their navigation, and the title / artist click (the contract gives no
 * album or artist id for a track); the like button (`Modern` controls only, not the default); the text
 * outline, transparent by default. Without an album id the phone
 * dims the texts as "not navigable": navigation does not exist on the PC, so they keep the text colour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoAlbumAndArtistModern(
    title: String?,
    artist: String?,
    disableScrollingText: Boolean = false,
) {
    val isExplicit = (title ?: "").hasExplicitPrefix()
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
                    // The explicit badge (phone's 18 dp icon, text colour), before the title
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
