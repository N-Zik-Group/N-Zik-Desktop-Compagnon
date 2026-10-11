package app.n_zik.compagnon.enums

import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackDownloadState
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.bridge.state.displayedLike
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/QueueSwipeAction.kt`: the swipe action of the queue
 * rows, in the phone's declaration order. [getStateIcon] is the phone's icon of the revealed background —
 * its `SwipeableQueueItem` passes the track's tri-state like and download state (its
 * `DownloadedStateMedia`): an active download shows the progress, a downloaded track the `downloaded`
 * icon, and the [Favourite] heart follows the tri-state (its `showDisliked` flag is always on here —
 * with the phone's "disliked" mode off the wire never carries a disliked track).
 */
enum class QueueSwipeAction(
    val iconId: DrawableResource,
) {
    NoAction(Res.drawable.close),
    PlayNext(Res.drawable.play_skip_forward),
    Download(Res.drawable.download),
    Favourite(Res.drawable.heart_outline),
    RemoveFromQueue(Res.drawable.trash),
    Enqueue(Res.drawable.enqueue);

    /** The phone's `getStateIcon`, mapped on the track's like and download state. */
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
