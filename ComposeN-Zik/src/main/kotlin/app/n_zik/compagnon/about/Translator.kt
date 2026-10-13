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
 * A translator of the N-Zik project (spec `spec-updater` AD-10 — port of the phone's
 * `Translator` model, Gson swapped for kotlinx.serialization, the same JSON field names: the
 * embedded `translators.json`).
 */
@Serializable
data class Translator(
    @SerialName("username") val username: String,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("languages") val languages: String,
    @SerialName("profileUrl") val profileUrl: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
) {

    /** The profile's handle (the phone's `usernameByProfile`: the `profileUrl`'s last segment). */
    val usernameByProfile: String
        get() = profileUrl?.split("/")?.last().toString()
}

/**
 * The translator card (1:1 port of the phone's `Translator.Draw`): the 40 dp bordered avatar
 * (the `avatarUrl`, a transparent placeholder when it fails), the bold name, the italic clickable
 * @handle (opens the profile in the browser when one exists), the languages + the translate icon.
 */
@Composable
fun TranslatorCard(translator: Translator) {
    val avatarPainter = ImageCacheFactory.ExternalAvatarPainter(translator.avatarUrl)

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
                .background(Color.Transparent, uiRoundnessShape()),
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
                    text = translator.displayName ?: translator.username,
                    style = typography().xs.copy(
                        color = colorPalette().text,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                    ),
                )

                Row(Modifier.fillMaxWidth()) {
                    BasicText(
                        text = if (translator.profileUrl != null) "@${translator.usernameByProfile}" else "@${translator.username}",
                        style = typography().xs.copy(
                            color = colorPalette().textSecondary,
                            fontStyle = FontStyle.Italic,
                        ),
                        modifier = Modifier
                            .wrapContentSize()
                            .clip(uiRoundnessShape())
                            .clickable {
                                translator.profileUrl?.let { openInBrowser(it) }
                            },
                    )

                    // The phone's `favoritesIcon` (a secondary decorative colour) — the desktop
                    // palette's closest member is the accent
                    val color = colorPalette().accent.copy(alpha = 0.8f)

                    BasicText(
                        text = translator.languages,
                        style = typography().xs.copy(color = color, textAlign = TextAlign.End),
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(Modifier.width(5.dp))

                    Icon(
                        painter = painterResource(Res.drawable.translate),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
