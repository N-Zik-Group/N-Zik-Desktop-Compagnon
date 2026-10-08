package app.n_zik.compagnon.updater.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.updater.models.UpdaterConstants
import androidx.compose.material3.Icon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Parses the changelog text into sections (port of the phone's `parseChangelogText`,
 * `UpdateScreen.kt` L965-1001 — the de-Androided pure version: the phone's `R.string.other`
 * fallback title is a parameter, so the function is unit-testable without a composition).
 *
 *  * a line ending in `:` is a section title (the phone's category keys: added / changed /
 *    improved / fixed / …);
 *  * a line starting with `-` is a change of the current section;
 *  * changes before the first title land under [otherTitle] (the phone's `R.string.other`);
 *  * anything else is ignored.
 */
fun parseChangelogText(text: String, otherTitle: String): List<Pair<String, List<String>>> {
    val sections = mutableListOf<Pair<String, List<String>>>()
    var currentTitle: String? = null
    val currentChanges = mutableListOf<String>()

    fun packSection() {
        val title = currentTitle
        if (title != null) {
            sections.add(title to currentChanges.toList())
            currentChanges.clear()
        }
    }

    text.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.endsWith(":") || trimmed.endsWith(" :") -> {
                packSection()
                currentTitle = trimmed.substringBeforeLast(":").trim()
            }
            trimmed.startsWith("-") -> {
                val change = trimmed.removePrefix("-").trim()
                if (change.isNotBlank()) {
                    currentChanges.add(change)
                }
            }
        }
    }
    packSection()

    if (sections.isEmpty() && currentChanges.isNotEmpty()) {
        sections.add(otherTitle to currentChanges.toList())
    }

    return sections
}

/**
 * The "What's new" card (1:1 port of the phone's `ChangelogCard`, `UpdateScreen.kt` L1003-1116):
 * the changelog [rawText] (English) shown as collapsible sections, each with its category icon
 * + the phone's category colors; [translatedText] (the user's language, same section structure)
 * replaces the display titles + changes when its parse matches the raw one section-for-section,
 * else the card falls back to the original text (translation is a convenience, never a blocker).
 */
@Composable
fun ChangelogCard(rawText: String, translatedText: String?) {
    val otherTitle = stringResource(Res.string.other)
    val rawSections = remember(rawText) { parseChangelogText(rawText, otherTitle) }

    val displaySections = remember(rawText, translatedText) {
        val parsedTranslated = translatedText?.let { parseChangelogText(it, otherTitle) }

        if (parsedTranslated != null && parsedTranslated.size == rawSections.size) {
            // Zip them: Triple(Raw Title, Display Title, Display Changes)
            rawSections.mapIndexed { index, rawSection ->
                Triple(rawSection.first, parsedTranslated[index].first, parsedTranslated[index].second)
            }
        } else {
            // Fallback: no translation or the structure broke
            val fallbackSections = parsedTranslated ?: rawSections
            fallbackSections.map { Triple(it.first, it.first, it.second) }
        }
    }

    if (displaySections.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        displaySections.forEach { section ->
            var expanded by remember { mutableStateOf(true) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(uiRoundnessShape())
                    .clickable { expanded = !expanded }
                    .background(Color.Transparent),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(
                                when (section.first.lowercase()) {
                                    UpdaterConstants.CHANGELOG_ADDED -> Res.drawable.add
                                    UpdaterConstants.CHANGELOG_CHANGED -> Res.drawable.pencil
                                    UpdaterConstants.CHANGELOG_IMPROVED -> Res.drawable.refresh_circle
                                    UpdaterConstants.CHANGELOG_FIXED -> Res.drawable.alert
                                    UpdaterConstants.CHANGELOG_DEV -> Res.drawable.sparkles
                                    else -> Res.drawable.information
                                }
                            ),
                            contentDescription = null,
                            tint = colorPalette().accent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = section.second, // Use the display title
                            style = typography().s.semiBold.copy(color = colorPalette().text),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            painter = painterResource(if (expanded) Res.drawable.chevron_up else Res.drawable.chevron_down),
                            contentDescription = null,
                            tint = colorPalette().textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        ) {
                            section.third.forEach { change ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                when (section.first.lowercase()) {
                                                    UpdaterConstants.CHANGELOG_ADDED -> Color(0xFF4CAF50)
                                                    UpdaterConstants.CHANGELOG_CHANGED -> Color(0xFFFF9800)
                                                    UpdaterConstants.CHANGELOG_IMPROVED -> Color(0xFF2196F3)
                                                    UpdaterConstants.CHANGELOG_FIXED -> Color(0xFFF44336)
                                                    UpdaterConstants.CHANGELOG_DEV -> Color(0xFFFFEB3B)
                                                    else -> colorPalette().accent
                                                }
                                            ),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    BasicText(
                                        text = change,
                                        style = typography().xs.copy(color = colorPalette().text),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
