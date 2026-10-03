package app.n_zik.compagnon.components.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.uiRoundnessShape

/**
 * Progress at or below which the deploy card background is skipped: the mini-player still renders its
 * own surface there, so drawing the card would only paint a sliver behind it.
 * Port of the phone's `CARD_BG_COLLAPSED_EPSILON` (`CustomBottomSheet.kt` 152).
 */
internal const val CARD_BG_COLLAPSED_EPSILON = 0.01f

/**
 * Fade-in progress of the full player content (0 = hidden, 1 = visible). Starts at 0.45, exactly when
 * the mini-player is fully faded ([miniPlayerFade]), so the hand-over has no pop, no overlap and no
 * empty gap. Port of the phone's `playerContentFade` (`CustomBottomSheet.kt` 135).
 */
internal const val PLAYER_SHEET_HANDOVER_PROGRESS = 0.45f

internal fun playerContentFade(progress: Float): Float =
    ((progress - PLAYER_SHEET_HANDOVER_PROGRESS) / (1f - PLAYER_SHEET_HANDOVER_PROGRESS)).coerceIn(0f, 1f)

/**
 * Fade-out progress of the mini-player (1 = visible, 0 = hidden). Starts fading as soon as the deploy
 * begins and is fully transparent at 0.45 — right when the player fade-in starts. Port of the phone's
 * `miniPlayerFade` (`CustomBottomSheet.kt` 144).
 */
internal fun miniPlayerFade(progress: Float): Float =
    1f - (progress / PLAYER_SHEET_HANDOVER_PROGRESS).coerceIn(0f, 1f)

/**
 * Geometry of the deploy card at progress [p] (port of the phone's `computeCardGeometry`,
 * `CustomBottomSheet.kt` 50, without the collapsed insets: no navigation rail beside the PC mini-player).
 */
internal data class CardGeometry(
    val left: Float,
    val width: Float,
    val height: Float,
    val cornerPx: Float,
)

internal fun cardGeometry(
    p: Float,
    parentWidthPx: Float,
    parentHeightPx: Float,
    collapsedHeightPx: Float,
    horizontalPaddingPx: Float,
    baseCornerPx: Float,
    corner28Px: Float,
): CardGeometry {
    val cardHeight = collapsedHeightPx + (parentHeightPx - collapsedHeightPx) * p
    val startWidth = parentWidthPx - 2 * horizontalPaddingPx
    val cardWidth = startWidth + (parentWidthPx - startWidth) * p
    val cardLeft = horizontalPaddingPx * (1f - p)
    val cornerPx = if (p < 0.5f) {
        baseCornerPx + (corner28Px - baseCornerPx) * (p / 0.5f)
    } else {
        corner28Px * (1f - (p - 0.5f) / 0.5f)
    }
    return CardGeometry(cardLeft, cardWidth, cardHeight, cornerPx)
}

/**
 * Shape that follows the exact animated card rect (port of the phone's `CardClipShape`), so the clip and
 * the background drawn behind stay in sync.
 */
private class CardClipShape(private val geometry: CardGeometry) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rounded(
        RoundRect(
            left = geometry.left,
            top = 0f,
            right = geometry.left + geometry.width,
            bottom = geometry.height,
            radiusX = geometry.cornerPx,
            radiusY = geometry.cornerPx,
        )
    )
}

/**
 * Port of the phone's player sheet (`app/n_zik/android/components/CustomBottomSheet.kt` 209-409): a single
 * sliding unit holding the [MiniPlayer] at the top edge of a full-screen box, translated down until the
 * mini-player sits [bottomPadding] above the parent's bottom edge. The box's card background (the
 * mini-player's `background2`) deploys from the collapsed card to the full screen while the [Player]
 * content fades in from [PLAYER_SHEET_HANDOVER_PROGRESS] (0.45), right when the mini-player is fully
 * faded out — the hand-over has no pop, no overlap and no empty gap. The card background is kept all the
 * way to fully expanded (the phone's issue #855): the [Player] background's transparent regions would
 * otherwise expose the screen behind the sheet.
 *
 * Dropped: the sheet's drag / fling (no touch on the PC: the player opens and closes with the mini-player's
 * click, in a 400 ms tween instead of the phone's spring), the top-anchored variant, the collapsed insets
 * (no navigation rail beside the PC mini-player) and the disabled-dismiss reporting.
 */
@Composable
fun PlayerSheet(
    showPlayer: Boolean,
    onShowPlayer: (Boolean) -> Unit,
    onShowQueue: () -> Unit,
    phoneName: String,
    bottomPadding: Dp,
) {
    val progress by animateFloatAsState(
        targetValue = if (showPlayer) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "playerSheetProgress",
    )
    val density = LocalDensity.current
    val baseShape = uiRoundnessShape()
    val miniPlayerColor = colorPalette().background2
    val collapsedHeightPx = with(density) { Dimensions.collapsedPlayer.toPx() }
    val horizontalPaddingPx = with(density) { 16.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // The collapsed box is pushed down until the mini-player sits [bottomPadding] above the
                // parent's bottom edge; at progress 1 the sheet is full screen (no translation)
                translationY = (size.height - collapsedHeightPx - bottomPadding.toPx()) *
                    (1f - progress.coerceIn(0f, 1f))
            }
            .graphicsLayer {
                // Clip to the exact animated card rect (same geometry as the background below): the hit
                // area is the mini-player band when collapsed, full screen when expanded
                val p = progress.coerceIn(0f, 1f)
                val baseCornerPx = (baseShape as? RoundedCornerShape)
                    ?.topStart?.toPx(size, this)
                    ?: 16.dp.toPx()
                shape = CardClipShape(
                    cardGeometry(
                        p = p,
                        parentWidthPx = size.width,
                        parentHeightPx = size.height,
                        collapsedHeightPx = collapsedHeightPx,
                        horizontalPaddingPx = horizontalPaddingPx,
                        baseCornerPx = baseCornerPx,
                        corner28Px = 28.dp.toPx(),
                    )
                )
                clip = true
            }
            .drawBehind {
                val p = progress.coerceIn(0f, 1f)
                if (p > CARD_BG_COLLAPSED_EPSILON) {
                    val baseCornerPx = (baseShape as? RoundedCornerShape)
                        ?.topStart?.toPx(size, this)
                        ?: 16.dp.toPx()
                    val geometry = cardGeometry(
                        p = p,
                        parentWidthPx = size.width,
                        parentHeightPx = size.height,
                        collapsedHeightPx = collapsedHeightPx,
                        horizontalPaddingPx = horizontalPaddingPx,
                        baseCornerPx = baseCornerPx,
                        corner28Px = 28.dp.toPx(),
                    )
                    // The card keeps the mini-player background colour throughout the deploy animation
                    drawRoundRect(
                        color = miniPlayerColor,
                        topLeft = Offset(geometry.left, 0f),
                        size = Size(geometry.width, geometry.height),
                        cornerRadius = CornerRadius(geometry.cornerPx, geometry.cornerPx),
                    )
                }
            },
    ) {
        // Player content — always in composition; the draw is skipped below the alpha threshold instead
        // of just setting alpha = 0 (progress is read in draw phase only, never in composition: a
        // composition read would recompose the whole sheet on every frame, the phone's note)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val p = progress
                    if (p >= 0.3f && playerContentFade(p) > 0.01f) drawContent()
                }
                .graphicsLayer {
                    alpha = playerContentFade(progress)
                },
        ) {
            Player(onDismiss = { onShowPlayer(false) })
        }

        // Mini-player — at the top edge of the box; fades out to 0.45, out of composition once fully
        // expanded (the boolean flips only at the crossing, so there is no per-frame recomposition)
        if (progress < 0.99f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.collapsedPlayer)
                    .graphicsLayer {
                        alpha = miniPlayerFade(progress)
                        // Clipped to the collapsed card shape while deploying so the mini-player's shadow
                        // (drawn inside it) cannot leak outside its card as the card grows around it
                        if (progress > CARD_BG_COLLAPSED_EPSILON) {
                            val baseCornerPx = (baseShape as? RoundedCornerShape)
                                ?.topStart?.toPx(size, this)
                                ?: 16.dp.toPx()
                            shape = CardClipShape(
                                cardGeometry(
                                    p = 0f,
                                    parentWidthPx = size.width,
                                    parentHeightPx = size.height,
                                    collapsedHeightPx = collapsedHeightPx,
                                    horizontalPaddingPx = horizontalPaddingPx,
                                    baseCornerPx = baseCornerPx,
                                    corner28Px = 28.dp.toPx(),
                                )
                            )
                            clip = true
                        }
                    },
            ) {
                MiniPlayer(
                    showPlayer = { onShowPlayer(true) },
                    hidePlayer = { onShowPlayer(false) },
                    onShowQueue = onShowQueue,
                    phoneName = phoneName,
                )
            }
        }
    }
}
