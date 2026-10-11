package app.n_zik.compagnon.enums

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/SwipeAnimations.kt` (`SwipeAnimationNoThumbnail`): the
 * cover transition when the track changes with the phone's thumbnail pager off, in the phone's
 * declaration order. [Sliding] is its default — the PC's current slide + fade + scale, unchanged.
 */
enum class SwipeAnimationNoThumbnail {
    Sliding,
    Fade,
    Scale,
    Carousel,
    Circle;
}
