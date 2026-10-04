package app.n_zik.compagnon.components.navigation

/**
 * The scroll-hide arithmetic of the phone's `MainActivity.kt` 1713-1782: the header moves with the scroll
 * up to [topMaxPx] out (negative offset), the floating bar and mini-player up to [bottomMaxPx] down; on
 * release they snap to shown or hidden around half the header.
 *
 * PC (story 11c): a mouse wheel delivers no fling, and over a list already at its top the wheel reaches
 * no nested scroll at all, so the bars stayed hidden. The window also feeds the raw wheel ([wheel]) and
 * snaps after a short pause ([snapShown]) instead of relying on `onPostFling`.
 */
class BarsScrollHide(private val topMaxPx: Float, private val bottomMaxPx: Float) {
    var top: Float = 0f
        private set
    var bottom: Float = 0f
        private set

    /** A scroll of [delta] px (positive = content moves down, i.e. scrolling up); returns the y consumed. */
    fun scroll(delta: Float): Float {
        if (delta == 0f) return 0f
        val previous = top
        top = (top + delta).coerceIn(-topMaxPx, 0f)
        bottom = (bottom - delta).coerceIn(0f, bottomMaxPx)
        return top - previous
    }

    /** A wheel step upward ([delta] > 0) brings the bars back even when no list scrolls. */
    fun wheel(delta: Float) {
        if (delta > 0f) scroll(delta)
    }

    /** Where to snap once the scroll stops: `true` = shown. */
    fun snapShown(): Boolean = top >= -topMaxPx / 2f

    fun set(top: Float, bottom: Float) {
        this.top = top.coerceIn(-topMaxPx, 0f)
        this.bottom = bottom.coerceIn(0f, bottomMaxPx)
    }
}
