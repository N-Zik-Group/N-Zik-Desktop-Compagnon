package app.n_zik.compagnon.components.ui.screens.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.artistThumbnailShape
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.utils.FACE_INITIALS_COLOR_COUNT
import app.n_zik.compagnon.utils.faceInitials
import app.n_zik.compagnon.utils.faceInitialsColorIndex
import app.n_zik.compagnon.utils.semiBold

/** Deterministic backgrounds of the initials avatar (phone's `ProfileCard.kt` 345-355). */
private val INITIALS_BACKGROUNDS = listOf(
    Color(0xFF7B4FD8),
    Color(0xFF1E88E5),
    Color(0xFF2E9E5B),
    Color(0xFFE8842C),
    Color(0xFFD64550),
    Color(0xFF00ACC1),
    Color(0xFFC0921C),
    Color(0xFF8D6E63),
)

/**
 * Port of `ProfileFaceAvatar` (phone's `app/n_zik/android/components/ui/screens/profiles/ProfileCard.kt`
 * 369-424) in its initials case, the only one on the PC (no profile photo, no account): the initials of
 * [faceName] in s.semiBold white, on their deterministic background, clipped to the artist roundness.
 */
@Composable
fun ProfileFaceAvatar(
    faceName: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size)) {
        ProfileInitialsFace(faceName, size)
    }
}

@Composable
private fun ProfileInitialsFace(name: String, size: Dp) {
    require(FACE_INITIALS_COLOR_COUNT == INITIALS_BACKGROUNDS.size) {
        "FACE_INITIALS_COLOR_COUNT must match the initials palette size"
    }
    val background = INITIALS_BACKGROUNDS[faceInitialsColorIndex(name)]
    Box(
        modifier = Modifier
            .size(size)
            .clip(artistThumbnailShape())
            .background(background, artistThumbnailShape()),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = faceInitials(name),
            style = typography().s.semiBold.copy(color = Color.White),
        )
    }
}
