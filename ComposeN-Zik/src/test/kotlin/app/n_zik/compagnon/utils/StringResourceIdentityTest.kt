package app.n_zik.compagnon.utils

import app.n_zik.compagnon.generated.resources.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StringResourceIdentityTest {
    @Test
    fun `the seven dynamic phone names resolve to retained generated resources`() {
        val dynamicNames = mapOf(
            "autosync_likes" to "sync_likes",
            "create_new_playlist" to "playlist_create_dialog_title",
            "info_check_update_now" to "updates_action_check_now",
            "new_playlist" to "playlist_action_create",
            "randomizer" to "library_action_randomize",
            "songs_shuffled" to "player_shuffled_song_count_format",
            "update_download_completed" to "updates_download_completed",
        )
        dynamicNames.forEach { (historical, local) ->
            assertEquals(local, StringResourceIdentity.localName(historical))
            assertEquals(local, StringResourceIdentity.localName(local))
            assertTrue(Res.allStringResources.containsKey(local), "Missing dynamic resource: $local")
        }
    }

    @Test
    fun `shared mini player labels still resolve both published names`() {
        val names = mapOf(
            "audio_output" to "audio_output_title",
            "lyrics" to "lyrics_title",
            "repeat" to "player_action_repeat",
        )
        names.forEach { (label, canonical) ->
            assertEquals(canonical, StringResourceIdentity.localName("miniplayer_button_$label"))
            assertEquals(canonical, StringResourceIdentity.localName("player_mini_button_$label"))
            assertTrue(Res.allStringResources.containsKey(canonical))
        }
    }

    @Test
    fun `unknown and unchanged desktop names pass through`() {
        listOf("unknown_key_xyz", "pair_title", "command_play", "notice_no_delta", "").forEach { name ->
            assertEquals(name, StringResourceIdentity.localName(name))
        }
    }
}
