package app.n_zik.compagnon.components.menu

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A menu entry of the phone that has no contract route (story 11c): shown in its section with the phone's
 * icon and label (plus [suffix], e.g. the artist of "More of"), clickable, without effect. Like the phone's
 * entries it is `Descriptive`: a long press / right click shows its description ([descriptionId], the title
 * by default — the phone's `messageId`; `null` where the phone's entry has an empty long press).
 */
class InertMenuItem(
    override val iconId: DrawableResource,
    private val title: StringResource,
    private val suffix: String = "",
    /** `null` where the phone's entry overrides its long press with nothing (no help toast). */
    private val descriptionId: StringResource? = title,
) : MenuIcon, app.n_zik.compagnon.components.tab.toolbar.Descriptive {
    override val messageId: StringResource get() = descriptionId ?: title

    override fun onLongClick() {
        descriptionId?.let { app.n_zik.compagnon.utils.Toaster.i(it) }
    }

    override val menuIconTitle: String
        @Composable
        get() = stringResource(title) + suffix

    override fun onShortClick() {}
}

/**
 * Port of `String?.splitArtistNames` (phone's `app/it/fast4x/rimusic/utils/Utils.kt` 195-211): split on
 * `&`, `,` and the localized [conjunctions] — the phone's `ArtistConjunctions`, its `R.string.and`
 * (`MainApplication.kt` 214), read by the caller from `Res.string.and` — matched as whole words,
 * case-insensitively ("and" inside "Grand" never splits).
 */
internal fun splitArtistNames(artists: String?, conjunctions: List<String> = emptyList()): List<String> {
    if (artists.isNullOrBlank()) return emptyList()
    val words = conjunctions.filter { it.isNotBlank() }
    val pattern = if (words.isNotEmpty()) {
        Regex("\\s*(\\b(?:" + words.joinToString("|") { Regex.escape(it) } + ")\\b|&|,)\\s*", RegexOption.IGNORE_CASE)
    } else {
        Regex("\\s*(&|,)\\s*", RegexOption.IGNORE_CASE)
    }
    return artists.split(pattern).map { it.trim() }.filter { it.isNotBlank() }
}
