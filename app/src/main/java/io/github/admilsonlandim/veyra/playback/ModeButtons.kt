package io.github.admilsonlandim.veyra.playback

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import io.github.admilsonlandim.veyra.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The session callback: who may do what, and the shuffle and repeat buttons.
 *
 * The default notification only shows previous / play-pause / next. The two mode buttons are
 * custom session commands placed in the overflow slot, so they appear in the expanded
 * notification; their icons follow the current modes.
 *
 * Controllers: this app, the media notification and the system (system UI, lock screen,
 * Bluetooth through the system, apps the user made notification listeners) are trusted and get
 * everything. Any other installed app can bind to the exported service; it only gets playback
 * controls and the current track ([UNTRUSTED_PLAYER_COMMANDS]): no queue, no custom commands.
 *
 * The platform media session (what system UI, Bluetooth and android.media.session controllers
 * see) mirrors the media notification controller's commands. Its token reaches every connected
 * app, untrusted ones included, and the platform serves its queue without asking this callback,
 * so the notification controller gets no queue ([PLATFORM_HIDDEN_COMMANDS]) and the platform
 * session publishes none. The notification, lock screen and media buttons only use the current
 * track; what is lost is the queue list some cars show over Bluetooth.
 */
@OptIn(UnstableApi::class)
internal class ModeButtons(
    private val context: Context,
    /** Further commands for trusted controllers (this app), handled by [onOtherCommand]. */
    private val otherCommands: List<SessionCommand> = emptyList(),
    /** Returns false for an action it does not know. */
    private val onOtherCommand: (action: String, args: Bundle) -> Boolean = { _, _ -> false },
    /** Applied to the items controllers add, before the player gets them (see TrackKeyRegistry). */
    private val admitItems: (List<MediaItem>) -> List<MediaItem> = { it },
) : MediaSession.Callback {

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
     * Starts from Media3's defaults for this controller (all commands if trusted; read-only
     * session commands otherwise). Trusted controllers also get the mode and other custom
     * commands; untrusted ones get [UNTRUSTED_PLAYER_COMMANDS] instead of Media3's read-only
     * player commands, which include the whole queue. Note: super.onConnect must not be used as
     * the base: in Media3 1.11 it returns a "not implemented" result with no commands at all.
     */
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setMediaButtonPreferences(buttonsFor(session.player))
        if (session.isMediaNotificationController(controller)) {
            builder.setAvailablePlayerCommands(
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon().removeAll(*PLATFORM_HIDDEN_COMMANDS).build(),
            )
        }
        if (controller.isTrusted) {
            builder.setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(toggleShuffle)
                    .add(cycleRepeat)
                    .apply { otherCommands.forEach(::add) }
                    .build(),
            )
        } else {
            builder.setAvailablePlayerCommands(Player.Commands.Builder().addAll(*UNTRUSTED_PLAYER_COMMANDS).build())
        }
        return builder.build()
    }

    /**
     * Items from controllers (only trusted ones may add any) are playable as sent, like Media3's
     * default, which still refuses items without a URI; they go through [admitItems] first.
     */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        if (mediaItems.any { it.localConfiguration == null }) {
            return super.onAddMediaItems(mediaSession, controller, mediaItems)
        }
        return Futures.immediateFuture(admitItems(mediaItems))
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
            else -> if (!onOtherCommand(customCommand.customAction, args)) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    companion object {
        /**
         * What an untrusted app connected to the session may do: play/pause, skip, seek, and
         * read the current track (title, artist, artwork). Not the queue (COMMAND_GET_TIMELINE),
         * not changing it, not volume or track selection.
         */
        val UNTRUSTED_PLAYER_COMMANDS = intArrayOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
        )

        /** Taken from the media notification controller, so the platform session never publishes the queue. */
        val PLATFORM_HIDDEN_COMMANDS = intArrayOf(Player.COMMAND_GET_TIMELINE)

        private const val ACTION_TOGGLE_SHUFFLE = "io.github.admilsonlandim.veyra.TOGGLE_SHUFFLE"
        private const val ACTION_CYCLE_REPEAT = "io.github.admilsonlandim.veyra.CYCLE_REPEAT"
    }
}
