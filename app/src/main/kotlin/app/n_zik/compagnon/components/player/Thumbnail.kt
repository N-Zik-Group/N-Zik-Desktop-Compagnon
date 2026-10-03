package app.n_zik.compagnon.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.cd_background_image
import app.n_zik.compagnon.generated.resources.ic_launcher_box
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.doubleShadowDrop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The player cover: `size` ≈ 2× its display size, like the phone's `thumbnail(1000)` (contract §10.1). */
const val PLAYER_ARTWORK_SIZE_PX = 1000

/** The phone's `Timeline.Window` of the current track: its place in the queue and its id. */
private data class Window(val index: Int, val trackId: String, val hasArtwork: Boolean)

/**
 * Port of `Thumbnail` (phone's `app/it/fast4x/rimusic/ui/screens/player/Thumbnail.kt` 96-406), the cover of
 * the default `PlayerType.Essential` player: the current track's cover, square, with the double drop shadow
 * of `ThumbnailType.Modern` and the thumbnail shape, cropped (`cropVideoThumbnails`); a change of track
 * slides the new cover in from the side of its place in the queue (500 ms slide + fade + scale from 0.85).
 * `ic_launcher_box` when there is no cover.
 *
 * Dropped: the lyrics, the visualizer, the stats for nerds and the playback errors shown over the cover (not
 * in the contract), the tap / long press / double tap (lyrics, stats, like), the rotating vinyl option (off by
 * default).
 */
@Composable
fun Thumbnail(
    state: PlayerState,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val window = Window(state.currentIndex, track.id, track.hasArtwork)

    AnimatedContent(
        targetState = window,
        transitionSpec = {
            val duration = 500
            val slideDirection = if (targetState.index > initialState.index) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            }

            ContentTransform(
                targetContentEnter = slideIntoContainer(
                    towards = slideDirection,
                    animationSpec = tween(duration),
                ) + fadeIn(
                    animationSpec = tween(duration),
                ) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(duration),
                ),
                initialContentExit = slideOutOfContainer(
                    towards = slideDirection,
                    animationSpec = tween(duration),
                ) + fadeOut(
                    animationSpec = tween(duration),
                ) + scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(duration),
                ),
                sizeTransform = SizeTransform(clip = false),
            )
        },
        contentAlignment = Alignment.Center, label = "",
    ) { currentWindow ->

        var artImageAvailable by remember {
            mutableStateOf(true)
        }

        val coverPainter = ImageCacheFactory.Painter(
            key = if (currentWindow.hasArtwork) ArtworkKey.track(currentWindow.trackId, PLAYER_ARTWORK_SIZE_PX) else null,
            onError = { artImageAvailable = false },
            onSuccess = { artImageAvailable = true },
        )

        // ThumbnailType.Modern (the default)
        val modifierUiType = modifier
            .padding(vertical = 8.dp)
            .aspectRatio(1f)
            .fillMaxSize()
            .doubleShadowDrop(thumbnailShape(), 4.dp, 8.dp)
            .clip(thumbnailShape())

        Box(
            modifier = modifierUiType,
        ) {
            if (artImageAvailable) {
                Image(
                    painter = coverPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(thumbnailShape()),
                )
            } else {
                Image(
                    painter = painterResource(Res.drawable.ic_launcher_box),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(thumbnailShape()),
                    contentDescription = stringResource(Res.string.cd_background_image),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** Port of `Modifier.thumbnailpause` (`Thumbnail.kt` 409): the cover shrinks to 0.9 while paused (on by default). */
fun Modifier.thumbnailpause(
    shouldBePlaying: Boolean,
) = composed {
    val thumbnailpause = true
    val scale by animateFloatAsState(if ((thumbnailpause) && (!shouldBePlaying)) 0.9f else 1f)

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
