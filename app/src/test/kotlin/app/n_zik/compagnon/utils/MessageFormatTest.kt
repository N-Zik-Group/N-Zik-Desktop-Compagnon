package app.n_zik.compagnon.utils

import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.added_to_dislikes
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Regression for the desktop `getString` / `stringResource` not applying format arguments (their `%s`
 * stayed visible in toasts and error texts): the phone's `Resources.getString(id, args)` semantics must
 * be restored by [formatText] / [formatMessage].
 */
class MessageFormatTest {
    @Test
    fun `formatText applies the placeholders`() {
        assertEquals("Disliked «X»", formatText("Disliked %s", "«X»"))
        assertEquals("Paired with 2 devices", formatText("Paired with %d devices", 2))
        assertEquals("A and B", formatText("%s and %s", "A", "B"))
    }

    @Test
    fun `formatText without args returns the raw message`() {
        assertEquals("Done", formatText("Done"))
    }

    @Test
    fun `formatText with a mismatch keeps the raw message instead of throwing`() {
        assertEquals("Disliked %s", formatText("Disliked %s"))
        assertEquals("%s %s", formatText("%s %s", "only-one"))
    }

    @Test
    fun `formatMessage applies the args to a real resource`() = runBlocking {
        // The phone's `added_to_dislikes` is "Disliked %s"; the label is the "«Title - Artist»" one.
        assertEquals("Disliked «X»", formatMessage(Res.string.added_to_dislikes, "«X»"))
    }
}
