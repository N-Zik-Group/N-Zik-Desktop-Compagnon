package app.n_zik.compagnon.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.openInBrowser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource

/**
 * A contributor of the N-Zik project (spec `spec-updater` AD-10 — port of the phone's
 * `Developer` model, its Gson `@SerializedName` swapped for kotlinx.serialization's
 * `@SerialName`, the same JSON field names: the embedded `contributors.json`).
 */
@Serializable
data class Developer(
    val id: Int,
    @SerialName("login") val username: String,
    @SerialName("name") val displayName: String? = null,
    @SerialName("html_url") val url: String,
    @SerialName("avatar_url") val avatar: String,
    val contributions: Int? = null,
) {

    /** The profile's handle (the phone's `handle`: the `html_url`'s last segment). */
    val handle: String
        get() = url.split("/").last()
}

/**
 * The developer card (1:1 port of the phone's `Developer.Draw`): the 40 dp bordered avatar (the
 * GitHub `avatar_url`, loaded by [ImageCacheFactory.ExternalAvatarPainter]), the bold name (the
 * GitHub `name`, falling back to the `login`), the italic clickable @handle (opens the profile in
 * the browser), the contributions count + the git icon. The phone's special case kept: developer
 * id `1484476` (fast4x) gets the `background1` card background.
 */
@Composable
fun DeveloperCard(developer: Developer) {
    val avatarPainter = ImageCacheFactory.ExternalAvatarPainter(developer.avatar)
    val backgroundColor = if (developer.id == 1484476) colorPalette().background1 else Color.Transparent

    Card(
        modifier = Modifier
            .padding(start = 12.dp, end = 20.dp, bottom = 10.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp, horizontal = 15.dp)
                .background(backgroundColor, uiRoundnessShape()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = avatarPainter,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(uiRoundnessShape())
                    .border(1.dp, Color.White, uiRoundnessShape()),
                contentScale = ContentScale.Fit,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(Modifier.fillMaxWidth().padding(end = 10.dp)) {
                BasicText(
                    text = developer.displayName ?: developer.username,
                    style = typography().xs.copy(
                        color = colorPalette().text,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                    ),
                )

                Row(Modifier.fillMaxWidth()) {
                    BasicText(
                        text = "@${developer.handle}",
                        style = typography().xs.copy(
                            color = colorPalette().textSecondary,
                            fontStyle = FontStyle.Italic,
                        ),
                        modifier = Modifier
                            .wrapContentSize()
                            .clip(uiRoundnessShape())
                            .clickable { openInBrowser(developer.url) },
                    )

                    if (developer.contributions != null) {
                        // The phone's `favoritesIcon` (a secondary decorative colour) — the desktop
                        // palette's closest member is the accent
                        val color = colorPalette().accent.copy(alpha = 0.8f)

                        BasicText(
                            text = developer.contributions.toString(),
                            style = typography().xs.copy(color = color, textAlign = TextAlign.End),
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(Modifier.width(5.dp))

                        Icon(
                            painter = painterResource(Res.drawable.git_pull_request_outline),
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
