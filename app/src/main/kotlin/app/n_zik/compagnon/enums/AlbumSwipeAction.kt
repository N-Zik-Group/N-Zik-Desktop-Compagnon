package app.n_zik.compagnon.enums

import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/AlbumSwipeAction.kt` (the swipe actions of the
 * Albums cards, spec `spec-fill-pc-ui-gaps-home` #61), in the phone's declaration order.
 * [getStateIcon] is the phone's revealed-background icon — the [Bookmark] heart fills with the
 * bookmarked state, [NoAction] reveals nothing. (The phone passes a nullable bookmark timestamp;
 * the PC's contract carries the boolean [bookmarkedState].)
 */
enum class AlbumSwipeAction(
    val iconId: DrawableResource,
    val textId: StringResource,
) {
    NoAction(Res.drawable.close, Res.string.none),

    PlayNext(Res.drawable.play_skip_forward, Res.string.play_next),

    Bookmark(Res.drawable.bookmark_outline, Res.string.bookmark),

    Enqueue(Res.drawable.enqueue, Res.string.enqueue);

    /** The phone's `getStateIcon(bookmarkedState)`, adapted to the PC's boolean state. */
    fun getStateIcon(bookmarkedState: Boolean): DrawableResource? = when (this) {
        Bookmark -> if (bookmarkedState) Res.drawable.bookmark else Res.drawable.bookmark_outline
        NoAction -> null
        else -> iconId
    }
}
