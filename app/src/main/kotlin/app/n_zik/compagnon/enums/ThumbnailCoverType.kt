package app.n_zik.compagnon.enums

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/ThumbnailCoverType.kt`: the style of the player's
 * central cover ([Vinyl] is its default). The phone's `textId` is its settings entry's label — the PC
 * has no settings entry for it yet (the PC settings spec arrives later), so the enum carries the values
 * only.
 */
enum class ThumbnailCoverType {
    Vinyl,
    CD,
    CDwithCover;
}
