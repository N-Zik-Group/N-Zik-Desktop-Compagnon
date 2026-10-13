package app.n_zik.compagnon.components.player

import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.bridge.state.displayedLike
import app.n_zik.compagnon.bridge.state.nextRotation
import app.n_zik.compagnon.bridge.state.nextToggle
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.cleanPrefix
import org.jetbrains.compose.resources.StringResource

/**
 * The phone's `YouTubeSync.rotateSongLikeState` (`YouTubeSync.kt` 44-116, 174-183), shared by the player's
 * heart and the cover's double tap (the phone's `Player.kt` 1205-1215): the next state — the rotation
 * neutral → liked → disliked → neutral, or with the phone's "disliked" mode off the binary toggle — is
 * written (`library.write`) and, once the phone confirmed it (the phone toasts after its DB write), the
 * phone's toast of the resulting state — `rotateSongLikeState`'s three messages in both modes (its toggle
 * branch toasts `removed_from_dislikes` too) — the title and artists cleaned of the phone's prefixes as
 * its `cleanPrefix` does.
 * Not ported (the phone's side effects of the same call): `insertIgnore` of a track absent from its
 * database (the PC's write answers `404` "not found" instead), `downloadOnLike`, the YouTube Music push
 * and its `songs_liked_yt*` toasts — phone-only sync settings the contract does not serve.
 */
fun rotateTrackLike(track: Track, writes: LibraryActions?, rotationEnabled: Boolean) {
    // Without `library.write` nothing is written: no success toast either (the heart is an indicator)
    if (writes == null) return
    val like = track.displayedLike
    val next = if (rotationEnabled) like.nextRotation() else like.nextToggle()
    val label = likeToastLabel(track.title, track.artists)
    writes.likeSong(track.id, next) { confirmed ->
        val messageId = likeToastMessage(confirmed, rotationEnabled = true)
        if (label != null) Toaster.s(messageId, label) else Toaster.s(messageId)
    }
}

/**
 * The phone's toast of the resulting like state: `rotateSongLikeState`'s three messages (the player, in
 * both modes) or `toggleSongLikeState`'s two (the song / player menus with the "disliked" mode off).
 */
fun likeToastMessage(next: TrackLike, rotationEnabled: Boolean): StringResource = when {
    rotationEnabled -> when (next) {
        TrackLike.Liked -> Res.string.added_to_favorites
        TrackLike.Disliked -> Res.string.added_to_dislikes
        TrackLike.Neutral -> Res.string.removed_from_dislikes
    }
    next == TrackLike.Liked -> Res.string.added_to_favorites
    else -> Res.string.removed_from_favorites
}

/** The phone's quoted label `"title - artists"` (prefixes cleaned), `null` without a title. */
fun likeToastLabel(title: String, artists: String?): String? {
    val cleanTitle = cleanPrefix(title)
    if (cleanTitle.isBlank()) return null
    val cleanArtists = artists?.let(::cleanPrefix)?.takeIf { it.isNotBlank() }
    return if (cleanArtists != null) "\"$cleanTitle - $cleanArtists\"" else "\"$cleanTitle\""
}

/** The phone's `getLikedIcon()` (`GetLikeIconType.kt` 9-21): its `iconLikeType` (since 1.10.0, `ui.settings`). */
@androidx.compose.runtime.Composable
fun getLikedIcon(): org.jetbrains.compose.resources.DrawableResource =
    when (app.n_zik.compagnon.bridge.state.LocalUiSettings.current.likeIcon) {
        app.n_zik.compagnon.bridge.state.IconLikeType.Essential -> Res.drawable.heart
        app.n_zik.compagnon.bridge.state.IconLikeType.Gift -> Res.drawable.heart_gift
        app.n_zik.compagnon.bridge.state.IconLikeType.Apple -> Res.drawable.heart_apple
        app.n_zik.compagnon.bridge.state.IconLikeType.Brilliant -> Res.drawable.heart_brilliant
        app.n_zik.compagnon.bridge.state.IconLikeType.Shape -> Res.drawable.heart_shape
        app.n_zik.compagnon.bridge.state.IconLikeType.Breaked -> Res.drawable.heart_breaked_no
        app.n_zik.compagnon.bridge.state.IconLikeType.Striped -> Res.drawable.heart_striped
    }

/** The phone's `getUnlikedIcon()` (`GetLikeIconType.kt` 24-36). */
@androidx.compose.runtime.Composable
fun getUnlikedIcon(): org.jetbrains.compose.resources.DrawableResource =
    when (app.n_zik.compagnon.bridge.state.LocalUiSettings.current.likeIcon) {
        app.n_zik.compagnon.bridge.state.IconLikeType.Essential -> Res.drawable.heart_outline
        app.n_zik.compagnon.bridge.state.IconLikeType.Gift -> Res.drawable.heart_gift_outline
        app.n_zik.compagnon.bridge.state.IconLikeType.Apple -> Res.drawable.heart_apple_outline
        app.n_zik.compagnon.bridge.state.IconLikeType.Brilliant -> Res.drawable.heart_brilliant_outline
        app.n_zik.compagnon.bridge.state.IconLikeType.Shape -> Res.drawable.heart_shape_outline
        app.n_zik.compagnon.bridge.state.IconLikeType.Breaked -> Res.drawable.heart_breaked_yes
        app.n_zik.compagnon.bridge.state.IconLikeType.Striped -> Res.drawable.heart_striped_outline
    }

/**
 * The phone's share URLs (`ExternalUris.kt`, `SongItemMenu.kt` 757): on the desktop the "share" copies the
 * link to the clipboard (no Android share sheet), with the PC's "copied" toast.
 */
object ShareLinks {
    fun song(trackId: String) = "https://music.youtube.com/watch?v=$trackId"
    fun artist(artistId: String) = "https://music.youtube.com/channel/${artistId.removePrefix("modified:")}"

    /** The phone's album share fallback (`AlbumItemMenu.kt` 411; the album's `shareUrl` is not in the contract). */
    fun album(albumId: String) = "https://music.youtube.com/browse/$albumId"

    fun copy(url: String) = app.n_zik.compagnon.updater.ui.copyToClipboard(url, Res.string.value_copied)
}
