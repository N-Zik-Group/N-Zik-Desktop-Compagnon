package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import app.n_zik.compagnon.bridge.library.PlaylistOrigin
import app.n_zik.compagnon.components.items.PlaylistItem
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey

/**
 * Port of `Playlist` (phone's `app/it/fast4x/rimusic/ui/components/themed/Playlist.kt` 40), the playlist card
 * of the playlist screen: one image when [thumbnails] holds a single one, else the 2×2 mosaic. Since 1.7,
 * the origin icon, the pin and the bookmark badge come from the contract's [origin] / [isPinned] /
 * [isBookmarked] and — since 1.7.1 — the phone's [isEditable]: the "locked" badge of a non-editable
 * YouTube playlist (the contract now carries the flag). Since 1.7.2: the phone's custom cover (its
 * `thumbnail/playlist_<id>`, [customCover] — read with `GET /library/playlists/{id}/artwork`, a missing
 * one keeps the mosaic, as on the phone).
 */
@Composable
fun Playlist(
    name: String,
    songCount: Int,
    thumbnails: List<ArtworkKey>,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    /** Since 1.7: the origin of the playlist, for its origin icon. */
    origin: PlaylistOrigin = PlaylistOrigin.Local,
    /** Since 1.7: a pinned playlist (the phone's `pinned:` name prefix); the pin takes priority over the origin. */
    isPinned: Boolean = false,
    /** Since 1.7: a bookmarked YouTube playlist, for its bookmark badge. */
    isBookmarked: Boolean = false,
    /** Since 1.7.1: the phone's `isEditable` — the lock badge on a non-editable YouTube playlist. */
    isEditable: Boolean = true,
    /** Since 1.7.2: the phone's custom cover (`ArtworkKey.playlist`); a missing one (a `404`) keeps
     *  [thumbnails], as on the phone. */
    customCover: ArtworkKey? = null,
) {
    // Since 1.7.2: the phone's custom cover (its `checkFileExists(context, "thumbnail/playlist_<id>")`,
    // its `LocalPlaylistSongs.kt` 1078-1082), read with `GET /library/playlists/{id}/artwork`
    var hasCover by remember { mutableStateOf(false) }
    if (customCover != null) {
        ImageCacheFactory.Painter(
            key = customCover,
            onSuccess = { hasCover = true },
            onError = { hasCover = false },
        )
    }

    PlaylistItem(
        thumbnailContent = {
            if (customCover != null && hasCover) {
                ImageCacheFactory.Thumbnail(
                    key = customCover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            } else if (thumbnails.toSet().size == 1) {
                ImageCacheFactory.Thumbnail(
                    key = thumbnails.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            } else if (thumbnails.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    listOf(
                        Alignment.TopStart,
                        Alignment.TopEnd,
                        Alignment.BottomStart,
                        Alignment.BottomEnd,
                    ).forEachIndexed { index, alignment ->
                        ImageCacheFactory.Thumbnail(
                            key = thumbnails.getOrNull(index),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(alignment)
                                .size(thumbnailSizeDp / 2),
                        )
                    }
                }
            }
        },
        songCount = songCount,
        name = name,
        origin = origin,
        isPinned = isPinned,
        isBookmarked = isBookmarked,
        // The phone's themed card: the lock badge of a non-editable YouTube playlist (wire
        // `isBookmarked` == the phone's `isYoutubePlaylist`)
        lockBadge = isBookmarked && !isEditable,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
    )
}
