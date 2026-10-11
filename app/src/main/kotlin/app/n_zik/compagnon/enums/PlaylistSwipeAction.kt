package app.n_zik.compagnon.enums

import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackDownloadState
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.bridge.state.displayedLike
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/PlaylistSwipeAction.kt` (the swipe actions of
 * the Songs rows, spec `spec-fill-pc-ui-gaps-home` #60), in the phone's declaration order.
 * [getStateIcon] is the phone's revealed-background icon, mapped on the PC's [Track] like and
 * download state, the [QueueSwipeAction] pattern (an active download shows the progress, a
 * downloaded track the `downloaded` icon, and the [Favourite] heart follows the tri-state).
 */
enum class PlaylistSwipeAction(
    val iconId: DrawableResource,
    val textId: StringResource,
) {
    NoAction(Res.drawable.close, Res.string.none),

    PlayNext(Res.drawable.play_skip_forward, Res.string.play_next),

    Download(Res.drawable.download, Res.string.download),

    Favourite(Res.drawable.heart_outline, Res.string.favorites),

    Enqueue(Res.drawable.enqueue, Res.string.enqueue);

    /** The phone's `getStateIcon`, mapped on the PC's track like and download state. */
    fun getStateIcon(track: Track): DrawableResource? = when (this) {
        NoAction -> null
        Download -> when {
            track.downloadState != TrackDownloadState.None -> Res.drawable.download_progress
            track.isDownloaded -> Res.drawable.downloaded
            else -> iconId
        }
        Favourite -> when (track.displayedLike) {
            TrackLike.Disliked -> Res.drawable.heart_dislike
            TrackLike.Neutral -> Res.drawable.heart_outline
            TrackLike.Liked -> Res.drawable.heart
        }
        else -> iconId
    }
}
