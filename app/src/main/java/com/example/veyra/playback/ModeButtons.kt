package com.example.veyra.playback

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.veyra.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Shuffle and repeat buttons for the media notification (and other system controllers).
 *
 * The default notification only shows previous / play-pause / next. These two buttons are
 * custom session commands placed in the overflow slot, so they appear in the expanded
 * notification; their icons follow the current modes.
 */
@OptIn(UnstableApi::class)
internal class ModeButtons(private val context: Context) : MediaSession.Callback {

    private val toggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    private val cycleRepeat = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)

    fun buttonsFor(player: Player): List<CommandButton> {
        val shuffleOn = player.shuffleModeEnabled
        val repeat = repeatModeFromPlayer(player.repeatMode)
        return listOf(
            CommandButton.Builder(if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                .setDisplayName(context.getString(if (shuffleOn) R.string.player_shuffle_on else R.string.player_shuffle_off))
                .setSessionCommand(toggleShuffle)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(
                when (repeat) {
                    RepeatMode.Off -> CommandButton.ICON_REPEAT_OFF
                    RepeatMode.All -> CommandButton.ICON_REPEAT_ALL
                    RepeatMode.One -> CommandButton.ICON_REPEAT_ONE
                },
            )
                .setDisplayName(
                    context.getString(
                        when (repeat) {
                            RepeatMode.Off -> R.string.player_repeat_off
                            RepeatMode.All -> R.string.player_repeat_all
                            RepeatMode.One -> R.string.player_repeat_one
                        },
                    ),
                )
                .setSessionCommand(cycleRepeat)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )
    }

    /**
     * Starts from Media3's defaults for this controller (all commands if trusted, such as this
     * app and the media notification; read-only otherwise) and adds the two mode commands for
     * trusted controllers. Note: super.onConnect must not be used as the base: in Media3 1.11 it
     * returns a "not implemented" result with no commands at all.
     */
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setMediaButtonPreferences(buttonsFor(session.player))
        if (controller.isTrusted) {
            builder.setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(toggleShuffle)
                    .add(cycleRepeat)
                    .build(),
            )
        }
        return builder.build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val player = session.player
        when (customCommand.customAction) {
            ACTION_TOGGLE_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
            ACTION_CYCLE_REPEAT ->
                player.repeatMode = repeatModeFromPlayer(player.repeatMode).next().toPlayerRepeatMode()
            else -> return super.onCustomCommand(session, controller, customCommand, args)
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    private companion object {
        const val ACTION_TOGGLE_SHUFFLE = "com.example.veyra.TOGGLE_SHUFFLE"
        const val ACTION_CYCLE_REPEAT = "com.example.veyra.CYCLE_REPEAT"
    }
}
