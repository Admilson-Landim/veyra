package io.github.admilsonlandim.veyra.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UntrustedControllerCommandsTest {

    private val commands = ModeButtons.UNTRUSTED_PLAYER_COMMANDS.toSet()

    @Test
    fun otherApps_cannotReadOrChangeTheQueue() {
        listOf(
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SET_PLAYLIST_METADATA,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS,
            Player.COMMAND_RELEASE,
        ).forEach { assertFalse("command $it", it in commands) }
    }

    @Test
    fun otherApps_canControlPlayback_andSeeTheCurrentTrack() {
        listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
        ).forEach { assertTrue("command $it", it in commands) }
    }
}
