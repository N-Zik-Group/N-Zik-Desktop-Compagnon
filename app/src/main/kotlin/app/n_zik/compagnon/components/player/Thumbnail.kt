package app.n_zik.compagnon.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.enums.ThumbnailCoverType
import app.n_zik.compagnon.enums.SwipeAnimationNoThumbnail
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.doubleShadowDrop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The player cover: `size` ≈ 2× its display size, like the phone's `thumbnail(1000)` (contract §10.1). */
const val PLAYER_ARTWORK_SIZE_PX = 1000

/** The phone's `Timeline.Window` of the current track: its place in the queue and its id. */
private data class Window(val index: Int, val trackId: String, val hasArtwork: Boolean)

/**
 * The direction of the player's cover horizontal swipe (the phone's `Player.kt` 1218-1241 gesture):
 * a drag to the right plays the previous track, a drag to the left the next one.
 */
enum class CoverSwipeDirection {
    /** A drag to the right beyond the threshold: the previous track. */
    Previous,

    /** A drag to the left beyond the threshold: the next track. */
    Next,
}

/**
 * The phone's swipe decision (its `Player.kt` 1229-1234): the accumulated drag distance [deltaX]
 * against [threshold] px — right = [CoverSwipeDirection.Previous], left = [CoverSwipeDirection.Next],
 * nothing in between.
 */
fun coverSwipeAction(deltaX: Float, threshold: Float): CoverSwipeDirection? = when {
    deltaX > threshold -> CoverSwipeDirection.Previous
    deltaX < -threshold -> CoverSwipeDirection.Next
    else -> null
}

/**
 * The phone's horizontal drag on the cover (its `Player.kt` 1218-1241, `detectHorizontalDragGestures`):
 * accumulates the drag distance over the gesture and, on release, fires [onCoverSwipe] with the
 * [coverSwipeAction] of the total. Inert with a `null` [threshold].
 */
fun Modifier.playerCoverSwipe(
    threshold: Float?,
    onCoverSwipe: (CoverSwipeDirection) -> Unit,
): Modifier = composed {
    val currentOnCoverSwipe by rememberUpdatedState(onCoverSwipe)
    if (threshold == null) {
        this
    } else {
        this.pointerInput(Unit) {
            var deltaX = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, dragAmount ->
                    deltaX += dragAmount
                },
                onDragStart = {
                    deltaX = 0f
                },
                onDragEnd = {
                    coverSwipeAction(deltaX, threshold)?.let { currentOnCoverSwipe(it) }
                },
            )
        }
    }
}

/**
 * Port of `Thumbnail` (phone's `app/it/fast4x/rimusic/ui/screens/player/Thumbnail.kt` 96-406), the cover
 * of the default `PlayerType.Essential` player: the current track's cover, square, with the double drop
 * shadow of `ThumbnailType.Modern` and the thumbnail shape, cropped (`cropVideoThumbnails`); a change of
 * track slides the new cover in from the side of its place in the queue. `ic_launcher_box` when there is
 * no cover.
 *
 * - The track-change transition follows the phone's `swipeAnimationsNoThumbnail` ([swipeAnimation]):
 *   [SwipeAnimationNoThumbnail.Sliding] (the default) keeps the PC's current slide + fade + scale from
 *   0.85; [SwipeAnimationNoThumbnail.Fade] slides + fades without scaling;
 *   [SwipeAnimationNoThumbnail.Scale] scales 0.85 only; [SwipeAnimationNoThumbnail.Carousel] and
 *   [SwipeAnimationNoThumbnail.Circle] add the phone's per-variant alpha and scale (its
 *   `Player.kt` 1743-1950) — Circle also clips the cover to a circle with the phone's 20 shadow. The
 *   phone's 20 blur on Carousel's dragged pages has no enter/exit counterpart in this Compose version.
 * - [coverAnimationEnabled] (the phone's `showCoverThumbnailAnimation`) renders the cover through
 *   [RotateThumbnailCover] in the [coverStyle] disc style and rotates it while it plays
 *   ([RotateThumbnailCoverAnimation]).
 * - [playerCoverSwipe] beside the double tap: right = previous, left = next (the phone's
 *   `playerSwipeSensitivity` threshold in px, [swipeThreshold]).
 *
 * Dropped: the lyrics, the visualizer, the stats for nerds and the playback errors shown over the cover
 * (not in the contract), the tap / long press (lyrics, stats: not ported). The double tap is ported:
 * [onDoubleTap], the like rotation (the phone's `onDoubleTap`).
 * Adaptation (pass 5): no artwork retry button on a failed load (the phone's 218-228 retries its
 * Coil request); the PC's artwork cache re-reads it on the next track change.
 */
@Composable
fun Thumbnail(
    state: PlayerState,
    modifier: Modifier = Modifier,
    /** The phone's double tap on the cover (`Thumbnail.kt` 275-305): the like rotation. */
    onDoubleTap: () -> Unit = {},
    /** The phone's `playerSwipeSensitivity` threshold in px for [onCoverSwipe]; `null` disables the swipe. */
    swipeThreshold: Float? = null,
    /** The horizontal swipe on the cover: right = previous, left = next (the phone's `Player.kt` 1218-1241). */
    onCoverSwipe: (CoverSwipeDirection) -> Unit = {},
    /** The phone's `showCoverThumbnailAnimation`: the central cover turns while it plays. */
    coverAnimationEnabled: Boolean = false,
    /** The phone's `coverThumbnailAnimation`: the disc style, [ThumbnailCoverType.Vinyl] by default. */
    coverStyle: ThumbnailCoverType = ThumbnailCoverType.Vinyl,
    /** The phone's `swipeAnimationsNoThumbnail`: the track-change transition of the cover. */
    swipeAnimation: SwipeAnimationNoThumbnail = SwipeAnimationNoThumbnail.Sliding,
) {
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentSwipeAnimation = rememberUpdatedState(swipeAnimation)
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

            // The phone's per-variant graphicsLayer parameters (its `Player.kt` 1743-1950), mapped to the
            // PC's single-cover transition
            when (currentSwipeAnimation.value) {
                // Sliding (the default): the PC's current slide + fade + scale from 0.85, unchanged
                SwipeAnimationNoThumbnail.Sliding -> ContentTransform(
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
                // Fade: slide + fade, no scale
                SwipeAnimationNoThumbnail.Fade -> ContentTransform(
                    targetContentEnter = slideIntoContainer(
                        towards = slideDirection,
                        animationSpec = tween(duration),
                    ) + fadeIn(
                        animationSpec = tween(duration),
                    ),
                    initialContentExit = slideOutOfContainer(
                        towards = slideDirection,
                        animationSpec = tween(duration),
                    ) + fadeOut(
                        animationSpec = tween(duration),
                    ),
                    sizeTransform = SizeTransform(clip = false),
                )
                // Scale: the phone scales the dragged page to 0.85 (no slide, no fade)
                SwipeAnimationNoThumbnail.Scale -> ContentTransform(
                    targetContentEnter = scaleIn(
                        initialScale = 0.85f,
                        animationSpec = tween(duration),
                    ),
                    initialContentExit = scaleOut(
                        targetScale = 0.85f,
                        animationSpec = tween(duration),
                    ),
                    sizeTransform = SizeTransform(clip = false),
                )
                // Carousel: slide + the phone's alpha (2 - startOffset) / 2 (0.5 on an adjacent page)
                // + its scale 1 - 0.1 * offset (0.9) — its 20 blur has no enter/exit counterpart here
                SwipeAnimationNoThumbnail.Carousel -> ContentTransform(
                    targetContentEnter = slideIntoContainer(
                        towards = slideDirection,
                        animationSpec = tween(duration),
                    ) + fadeIn(
                        animationSpec = tween(duration),
                        initialAlpha = 0.5f,
                    ) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(duration),
                    ),
                    initialContentExit = slideOutOfContainer(
                        towards = slideDirection,
                        animationSpec = tween(duration),
                    ) + fadeOut(
                        animationSpec = tween(duration),
                        targetAlpha = 0.5f,
                    ) + scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(duration),
                    ),
                    sizeTransform = SizeTransform(clip = false),
                )
                // Circle: slide + the phone's scale 1 + 0.4 * |offset| (1.4) + its alpha (2 - startOffset)
                // / 2 — the circular clip and the 20 shadow are applied to the content below
                SwipeAnimationNoThumbnail.Circle -> ContentTransform(
                    targetContentEnter = slideIntoContainer(
                        towards = slideDirection,
                        animationSpec = tween(duration),
                    ) + fadeIn(
                        animationSpec = tween(duration),
                        initialAlpha = 0.5f,
                    ) + scaleIn(
                        initialScale = 1.4f,
                        animationSpec = tween(duration),
                    ),
                    initialContentExit = slideOutOfContainer(
                        towards = slideDirection,
                        animationSpec = tween(duration),
                    ) + fadeOut(
                        animationSpec = tween(duration),
                        targetAlpha = 0.5f,
                    ) + scaleOut(
                        targetScale = 1.4f,
                        animationSpec = tween(duration),
                    ),
                    sizeTransform = SizeTransform(clip = false),
                )
            }
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

        // The phone's shape: a circle with its cover animation (its `Thumbnail.kt` 249-251) or its
        // Circle swipe variant (its `Player.kt` 1861-1888, settled page), the thumbnail shape otherwise
        val circleSwipe = currentSwipeAnimation.value == SwipeAnimationNoThumbnail.Circle
        val shape = if (coverAnimationEnabled || circleSwipe) CircleShape else thumbnailShape()

        // ThumbnailType.Modern (the default)
        val modifierUiType = modifier
            // The phone's horizontal swipe on the cover (its `Player.kt` 1218-1241), beside the double tap
            .playerCoverSwipe(swipeThreshold) { direction ->
                onCoverSwipe(direction)
            }
            .padding(vertical = 8.dp)
            .aspectRatio(1f)
            .fillMaxSize()
            .let {
                if (circleSwipe) {
                    // The phone's Circle variant: the circular clip with its shadowElevation 20
                    it.shadow(20.dp, CircleShape)
                } else {
                    it.doubleShadowDrop(shape, 4.dp, 8.dp)
                }
            }
            .clip(shape)

        Box(
            modifier = modifierUiType.pointerInput(Unit) {
                // The latest closure (state, session), not the first composition's
                detectTapGestures(onDoubleTap = { currentOnDoubleTap() })
            },
        ) {
            if (artImageAvailable) {
                if (coverAnimationEnabled) {
                    RotateThumbnailCoverAnimation(
                        painter = coverPainter,
                        isSongPlaying = state.isPlaying,
                        modifier = Modifier.fillMaxSize(),
                        type = coverStyle,
                    )
                } else {
                    Image(
                        painter = coverPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape),
                    )
                }
            } else {
                Image(
                    painter = painterResource(Res.drawable.ic_launcher_box),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentDescription = stringResource(Res.string.cd_background_image),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/**
 * Port of `RotateThumbnailCover` (phone's `app/it/fast4x/rimusic/ui/components/themed/
 * RotateThumbnailCover.kt` 47-142): the cover on its disc — the `vinyl_background` / `cd` background
 * drawable, then the cover on top for [ThumbnailCoverType.Vinyl] (at [imageCoverSize] % of 0.01) and
 * [ThumbnailCoverType.CDwithCover] (85 %); a plain [ThumbnailCoverType.CD] shows the disc only. The
 * CD-with-cover's hub: three centered boxes (the 0.3 ring border, the 0.2 grey disc, the 0.1 black dot).
 *
 * The phone's ring shape computes `p3 = p1 - p2` (the donut) but returns `Outline.Generic(p1)` — the
 * plain outer oval — verbatim; ported as-is with the unused ring kept.
 */
@Composable
private fun RotateThumbnailCover(
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
    painter: Painter,
    type: ThumbnailCoverType = ThumbnailCoverType.Vinyl,
    imageCoverSize: Float = 50f,
) {
    val roundedShape = remember {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline {
                val p1 = Path().apply {
                    addOval(Rect(4f, 3f, size.width - 1f, size.height - 1f))
                }
                val thickness = size.height / 2.10f
                val p2 = Path().apply {
                    addOval(
                        Rect(
                            thickness,
                            thickness,
                            size.width - thickness,
                            size.height - thickness,
                        ),
                    )
                }
                val p3 = Path()
                p3.op(p1, p2, PathOperation.Difference)

                return Outline.Generic(p1)
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1.0f)
            .clip(roundedShape),
    ) {
        Image(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotationDegrees)
                .aspectRatio(1.0f),
            painter = painterResource(
                when (type) {
                    ThumbnailCoverType.Vinyl -> Res.drawable.vinyl_background
                    ThumbnailCoverType.CD, ThumbnailCoverType.CDwithCover -> Res.drawable.cd
                },
            ),
            contentDescription = stringResource(Res.string.cd_disc_background),
        )

        if (type in listOf(ThumbnailCoverType.Vinyl, ThumbnailCoverType.CDwithCover)) {
            Image(
                modifier = Modifier
                    .fillMaxSize(
                        when (type) {
                            ThumbnailCoverType.Vinyl -> imageCoverSize * 0.01f
                            ThumbnailCoverType.CD -> 1f
                            ThumbnailCoverType.CDwithCover -> 0.85f
                        },
                    )
                    .rotate(rotationDegrees)
                    .aspectRatio(1.0f)
                    .align(Alignment.Center)
                    .clip(roundedShape),
                painter = painter,
                contentDescription = stringResource(Res.string.cd_song_album_cover),
            )
        }

        if (type == ThumbnailCoverType.CDwithCover) {
            Box(
                modifier = Modifier
                    .clip(roundedShape)
                    .fillMaxSize(0.3f)
                    .border(BorderStroke(2.dp, Color.LightGray), roundedShape)
                    .align(Alignment.Center),
            )
            Box(
                modifier = Modifier
                    .clip(roundedShape)
                    .fillMaxSize(0.2f)
                    .background(Color.LightGray)
                    .align(Alignment.Center),
            )
            Box(
                modifier = Modifier
                    .clip(roundedShape)
                    .fillMaxSize(0.1f)
                    .background(Color.Black)
                    .align(Alignment.Center),
            )
        }
    }
}

/**
 * Port of `RotateThumbnailCoverAnimation` (the phone's `RotateThumbnailCover.kt` 145-191): the cover
 * turns while it plays — 8000 ms per revolution, linear, forever; on pause it settles with an extra
 * 50° over 1250 ms (only once it already turned).
 */
@Composable
private fun RotateThumbnailCoverAnimation(
    modifier: Modifier = Modifier,
    isSongPlaying: Boolean = true,
    painter: Painter,
    type: ThumbnailCoverType = ThumbnailCoverType.Vinyl,
) {
    var currentRotation by remember {
        mutableFloatStateOf(0f)
    }

    val rotation = remember {
        Animatable(currentRotation)
    }

    LaunchedEffect(isSongPlaying) {
        if (isSongPlaying) {
            rotation.animateTo(
                targetValue = currentRotation + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(8000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            ) {
                currentRotation = value
            }
        } else {
            if (currentRotation > 0f) {
                rotation.animateTo(
                    targetValue = currentRotation + 50,
                    animationSpec = tween(
                        1250,
                        easing = LinearOutSlowInEasing,
                    ),
                ) {
                    currentRotation = value
                }
            }
        }
    }

    RotateThumbnailCover(
        painter = painter,
        rotationDegrees = rotation.value,
        modifier = modifier,
        type = type,
    )
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
