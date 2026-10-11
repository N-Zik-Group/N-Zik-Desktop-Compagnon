package app.n_zik.compagnon.enums

import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/NavigationBarType.kt` (the style of the home's
 * navigation bar), in the phone's declaration order: [IconAndText] shows a label with each icon,
 * [IconOnly] the icons alone.
 */
enum class NavigationBarType(
    val textId: StringResource,
) {
    IconAndText(Res.string.icon_and_text),
    IconOnly(Res.string.only_icon),
}
