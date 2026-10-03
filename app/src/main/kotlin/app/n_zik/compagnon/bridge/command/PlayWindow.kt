package app.n_zik.compagnon.bridge.command

import app.n_zik.compagnon.bridge.library.LibraryContract
import kotlin.random.Random

/** The ids sent by `/queue/play` and the index playback starts at. */
data class PlaySelection(val trackIds: List<String>, val startIndex: Int)

/**
 * Pure choice of what `/queue/play` sends (contract §9: 1 to 500 `trackIds`), as on the phone where a
 * click on a track makes the whole list the queue and starts at that track.
 */
object PlayWindow {

    /**
     * All of [trackIds] when they fit; otherwise a window of [max] ids that contains [index], centred on it
     * as far as the list allows. `startIndex` is the place of [index] inside the window.
     */
    fun around(trackIds: List<String>, index: Int, max: Int = LibraryContract.TRACK_IDS_MAX): PlaySelection {
        require(index in trackIds.indices) { "index $index outside 0..${trackIds.lastIndex}" }
        if (trackIds.size <= max) return PlaySelection(trackIds, index)
        val start = (index - max / 2).coerceIn(0, trackIds.size - max)
        return PlaySelection(trackIds.subList(start, start + max).toList(), index - start)
    }

    /** "Shuffle" on a collection: the same ids in a random order, playback from the first one. */
    fun shuffled(trackIds: List<String>, random: Random = Random.Default): PlaySelection =
        PlaySelection(trackIds.shuffled(random), 0)
}
