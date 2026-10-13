package app.n_zik.compagnon.utils

/*
 * Port of the phone's `app/n_zik/android/utils/ProfileFace.kt` (initials part only): the PC has no profile
 * nor account photo, so the header shows the phone's fallback face, the initials of the default profile name.
 */

/** Number of deterministic backgrounds of the initials avatar. */
const val FACE_INITIALS_COLOR_COUNT = 8

/**
 * Deterministic initials for the placeholder avatar: the first letter of each of the first two words,
 * uppercased; a blank name renders "?" (phone's `faceInitials`).
 */
fun faceInitials(name: String): String {
    val letters = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
    return if (letters.isEmpty()) "?" else letters.joinToString("")
}

/** Deterministic background colour index of the initials avatar (phone's `faceInitialsColorIndex`). */
fun faceInitialsColorIndex(name: String): Int {
    val seed = name.trim()
    return if (seed.isEmpty()) 0 else (seed.hashCode() and Int.MAX_VALUE) % FACE_INITIALS_COLOR_COUNT
}
