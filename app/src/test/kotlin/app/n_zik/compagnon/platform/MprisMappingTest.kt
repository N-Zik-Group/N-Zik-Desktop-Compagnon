package app.n_zik.compagnon.platform

import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import org.freedesktop.dbus.types.Variant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The phone state → MPRIS surface mapping (frozen spec `spec-tray-taskbar-media-controls`):
 * `PlaybackStatus` / `Can*` / `Metadata` from [PlayerState] + [ConnectionState] (matrix rows
 * `MPRIS_HAPPY` / `FEATURE_ABSENT`, Design Notes "Statut MPRIS" + "MPRIS Metadata"), and the
 * `PropertiesChanged` diff (all seven `.Player` properties at the first push, only the changed
 * ones afterwards).
 */
class MprisMappingTest {

    private val playbackFeatures = setOf(SessionContract.FEATURE_PLAYBACK)

    private fun track(
        id: String = "t1",
        title: String = "Title",
        artists: String? = "N-Zik",
        durationMs: Long? = 20_000L,
        hasArtwork: Boolean = true,
    ) = Track(
        id = id,
        title = title,
        artists = artists,
        durationMs = durationMs,
        hasArtwork = hasArtwork,
    )

    private fun stateOf(
        track: Track?,
        isPlaying: Boolean,
        playerDurationMs: Long = PlayerState.DURATION_UNREPORTED,
    ) = if (track == null) {
        PlayerState(isPlaying = isPlaying, durationMs = playerDurationMs)
    } else {
        PlayerState(
            queue = listOf(track),
            currentIndex = 0,
            currentTrackId = track.id,
            isPlaying = isPlaying,
            durationMs = playerDurationMs,
        )
    }

    // ---- PlaybackStatus / Can* -------------------------------------------------------------------------

    @Test
    fun `without a live session the surface is stopped with every gate closed`() {
        val snapshot = mprisSnapshot(stateOf(track(), isPlaying = true), ConnectionState.Reconnecting(1000), playbackFeatures, null)
        assertEquals(stoppedSnapshot(), snapshot)
    }

    @Test
    fun `a live playing session is playing with the playback gates open`() {
        val snapshot = mprisSnapshot(stateOf(track(), isPlaying = true), ConnectionState.Live, playbackFeatures, null)
        assertEquals(PLAYING, snapshot.playbackStatus)
        assertTrue(snapshot.canGoNext)
        assertTrue(snapshot.canGoPrevious)
        assertFalse(snapshot.canPlay)
        assertTrue(snapshot.canPause)
        assertTrue(snapshot.canControl)
    }

    @Test
    fun `a live paused session is paused with the pause gate closed`() {
        val snapshot = mprisSnapshot(stateOf(track(), isPlaying = false), ConnectionState.Live, playbackFeatures, null)
        assertEquals(PAUSED, snapshot.playbackStatus)
        assertTrue(snapshot.canPlay)
        assertFalse(snapshot.canPause)
        assertTrue(snapshot.canControl)
    }

    @Test
    fun `a live session without the playback feature keeps the status but closes every gate`() {
        // The status reflects the phone (AD-1: the PC invents nothing); the controls are gated off
        val snapshot = mprisSnapshot(stateOf(track(), isPlaying = true), ConnectionState.Live, emptySet(), null)
        assertEquals(PLAYING, snapshot.playbackStatus)
        assertFalse(snapshot.canGoNext)
        assertFalse(snapshot.canGoPrevious)
        assertFalse(snapshot.canPlay)
        assertFalse(snapshot.canPause)
        assertFalse(snapshot.canControl)
    }

    // ---- Metadata ---------------------------------------------------------------------------------------

    @Test
    fun `the metadata maps the wire fields verbatim without album`() {
        val snapshot = mprisSnapshot(stateOf(track(id = "t42", title = "Song", artists = "N-Zik"), isPlaying = false), ConnectionState.Live, playbackFeatures, null)
        val metadata = snapshot.metadata
        assertEquals("t42", metadata?.trackId)
        assertEquals("Song", metadata?.title)
        // The phone's `artists` string verbatim, single-element (no parsing — AD-1)
        assertEquals(listOf("N-Zik"), metadata?.artist)
        // 20 000 ms → 20 000 000 µs
        assertEquals(20_000_000L, metadata?.lengthUs)
        assertNull(metadata?.artUrl)
    }

    @Test
    fun `the metadata is null without a current track`() {
        val snapshot = mprisSnapshot(stateOf(null, isPlaying = true), ConnectionState.Live, playbackFeatures, null)
        assertNull(snapshot.metadata)
    }

    @Test
    fun `the metadata artist is empty when the phone sends no artists`() {
        val snapshot = mprisSnapshot(stateOf(track(artists = null), isPlaying = false), ConnectionState.Live, playbackFeatures, null)
        assertEquals(emptyList<String>(), snapshot.metadata?.artist)
    }

    @Test
    fun `the metadata duration falls back to the player duration on the phone sentinel`() {
        val snapshot = mprisSnapshot(stateOf(track(durationMs = null), isPlaying = false, playerDurationMs = 50_000L), ConnectionState.Live, playbackFeatures, null)
        assertEquals(50_000_000L, snapshot.metadata?.lengthUs)
    }

    @Test
    fun `the metadata duration is omitted on the unset sentinel`() {
        val snapshot = mprisSnapshot(stateOf(track(durationMs = null), isPlaying = false, playerDurationMs = PlayerState.DURATION_TIME_UNSET), ConnectionState.Live, playbackFeatures, null)
        assertNull(snapshot.metadata?.lengthUs)
    }

    @Test
    fun `the track duration wins over the player duration`() {
        val snapshot = mprisSnapshot(stateOf(track(durationMs = 20_000L), isPlaying = false, playerDurationMs = 50_000L), ConnectionState.Live, playbackFeatures, null)
        assertEquals(20_000_000L, snapshot.metadata?.lengthUs)
    }

    @Test
    fun `the artwork url rides the metadata only when cached`() {
        val withArt = mprisSnapshot(stateOf(track(), isPlaying = false), ConnectionState.Live, playbackFeatures, "file:///C:/art.png")
        assertEquals("file:///C:/art.png", withArt.metadata?.artUrl)
        val withoutArt = mprisSnapshot(stateOf(track(), isPlaying = false), ConnectionState.Live, playbackFeatures, null)
        assertNull(withoutArt.metadata?.artUrl)
    }

    // ---- PropertiesChanged diff --------------------------------------------------------------------------

    @Test
    fun `the first push announces every player property`() {
        val changed = propertiesChanged(null, mprisSnapshot(stateOf(track(), isPlaying = true), ConnectionState.Live, playbackFeatures, null))
        assertEquals(ALL_PLAYER_PROPERTIES, changed)
    }

    @Test
    fun `an unchanged state announces nothing`() {
        val snapshot = mprisSnapshot(stateOf(track(), isPlaying = true), ConnectionState.Live, playbackFeatures, "file:///C:/art.png")
        assertEquals(emptyList<String>(), propertiesChanged(snapshot, snapshot))
    }

    @Test
    fun `a play pause flip announces the status only`() {
        val playing = mprisSnapshot(stateOf(track(), isPlaying = true), ConnectionState.Live, playbackFeatures, null)
        val paused = mprisSnapshot(stateOf(track(), isPlaying = false), ConnectionState.Live, playbackFeatures, null)
        assertEquals(listOf("PlaybackStatus", "CanPlay", "CanPause"), propertiesChanged(playing, paused))
    }

    @Test
    fun `a track change announces the metadata only`() {
        val old = mprisSnapshot(stateOf(track(id = "t1"), isPlaying = true), ConnectionState.Live, playbackFeatures, null)
        val new = mprisSnapshot(stateOf(track(id = "t2"), isPlaying = true), ConnectionState.Live, playbackFeatures, null)
        assertEquals(listOf("Metadata"), propertiesChanged(old, new))
    }

    @Test
    fun `going to no session announces the status and the metadata`() {
        val old = mprisSnapshot(stateOf(track(), isPlaying = true), ConnectionState.Live, playbackFeatures, null)
        val changed = propertiesChanged(old, stoppedSnapshot())
        assertTrue("PlaybackStatus" in changed && "Metadata" in changed && "CanGoNext" in changed && "CanControl" in changed)
    }

    // ---- Metadata wire map (the D-Bus `a{sv}`) --------------------------------------------------------------

    @Test
    fun `the wire map carries the frozen entries, the xesam artist as an as array`() {
        val map = MprisMetadata(trackId = "t42", title = "Song", artist = listOf("N-Zik"), lengthUs = 20_000_000L, artUrl = "file:///art.png").toDBusMap()
        assertEquals(5, map.size)
        assertEquals("t42", map[MprisMetadata.TRACK_ID]?.value)
        assertEquals("Song", map[MprisMetadata.TITLE]?.value)
        val artist = map[MprisMetadata.ARTIST]
        assertEquals("as", artist?.sig)
        assertEquals(listOf("N-Zik"), artist?.value)
        assertEquals(20_000_000L, map[MprisMetadata.LENGTH]?.value)
        assertEquals("file:///art.png", map[MprisMetadata.ART_URL]?.value)
    }

    @Test
    fun `the wire map artist is an empty as array when the phone sends no artists`() {
        val map = MprisMetadata(trackId = "t42", title = "Song", artist = emptyList(), lengthUs = null, artUrl = null).toDBusMap()
        assertEquals(3, map.size)
        val artist = map[MprisMetadata.ARTIST]
        assertEquals("as", artist?.sig)
        assertEquals(emptyList<String>(), artist?.value)
        assertNull(map[MprisMetadata.LENGTH])
        assertNull(map[MprisMetadata.ART_URL])
    }
}
