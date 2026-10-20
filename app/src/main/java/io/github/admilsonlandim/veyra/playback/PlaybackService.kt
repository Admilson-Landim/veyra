package io.github.admilsonlandim.veyra.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.admilsonlandim.veyra.MainActivity
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.audio.ReplayGain
import io.github.admilsonlandim.veyra.data.audio.replayGainVolume
import io.github.admilsonlandim.veyra.data.db.VeyraDatabase
import io.github.admilsonlandim.veyra.data.media.ArtworkLoader
import io.github.admilsonlandim.veyra.data.media.ArtworkSize
import io.github.admilsonlandim.veyra.data.settings.AppPreferences
import io.github.admilsonlandim.veyra.data.settings.PlaybackPreferences
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

/**
 * Owns the ExoPlayer and exposes it through a MediaSession. The UI never touches the player
 * directly: it connects with a MediaController (see PlayerConnection).
 *
 * Lifecycle: the system creates the service when the first controller binds. Media3 promotes it
 * to a foreground service (with the media notification) while playing, and drops it out of the
 * foreground 10 minutes after playback pauses or ends. onDestroy releases the player and session.
 *
 * Swiping the app away from Recents uses Media3's default onTaskRemoved: if playback is not
 * ongoing the service stops; if music is playing it keeps going, controllable from the
 * notification and the lock screen, which is what a music player is expected to do.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val errorSkipPolicy = ErrorSkipPolicy()

    // Media3 leaves a paused service running (just not in the foreground). Stop it ourselves
    // after a long idle period; if the UI is still bound, it is destroyed once the UI unbinds.
    private val idleHandler = Handler(Looper.getMainLooper())
    private val stopWhenIdle = Runnable { stopSelf() }

    @OptIn(UnstableApi::class) // setBitmapLoader, setMediaButtonPreferences
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // Pause for calls and other apps' playback, duck for short sounds, resume after.
                /* handleAudioFocus = */ true,
            )
            // Pause when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            // Local files only: keep the CPU (not Wi-Fi) awake while playing with the screen off.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(SkipBrokenItems(player))
        player.addListener(IdleStopTimer())
        val volume = PlayerVolume(player)
        playerVolume = volume
        val modeButtons = ModeButtons(this, SleepTimerSession.commands, ::onSleepCommand)
        val session = MediaSession.Builder(this, player)
            .setSessionActivity(openPlayerIntent())
            .setBitmapLoader(artworkLoader())
            .setCallback(modeButtons)
            .setMediaButtonPreferences(modeButtons.buttonsFor(player))
            .build()
        mediaSession = session
        // Keep the notification's shuffle/repeat icons in step with the player.
        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) =
                session.setMediaButtonPreferences(modeButtons.buttonsFor(player))

            override fun onRepeatModeChanged(repeatMode: Int) =
                session.setMediaButtonPreferences(modeButtons.buttonsFor(player))
        })
        attachSettings(player)
        attachSleepTimer(player, volume, session)
        queueSaver = QueueSaver(player, VeyraDatabase.get(this).savedQueueDao(), AppPreferences.get(this), serviceScope)
            .also { it.start() }
        // Also covers a service restarted by the system with an empty player.
        scheduleIdleStop()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioEffects by lazy { AudioEffectsController(this) }
    private val leveler = VolumeLeveler()
    private var playerVolume: PlayerVolume? = null
    private var sleepTimer: SleepTimer? = null
    private var queueSaver: QueueSaver? = null

    /**
     * Applies the stored sound settings to the player and keeps them applied: skip silence, and
     * the equalizer / bass boost on the player's audio session, recreated when the session id
     * changes (ExoPlayer generates it asynchronously after creation).
     */
    @OptIn(UnstableApi::class) // audioSessionId, onAudioSessionIdChanged
    private fun attachSettings(player: ExoPlayer) {
        val preferences = AppPreferences.get(this)
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            audioEffects.onSessionId(player.audioSessionId)
            leveler.onSessionId(player.audioSessionId)
        }
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioEffects.onSessionId(audioSessionId)
                leveler.onSessionId(audioSessionId)
            }
        })
        attachBalancedVolume(player, preferences)
        serviceScope.launch { preferences.equalizer.collect(audioEffects::update) }
        serviceScope.launch {
            preferences.playback.map { it.skipSilence }.distinctUntilChanged().collect {
                player.skipSilenceEnabled = it
            }
        }
    }

    /**
     * Balanced volume: each item's ReplayGain (read from its tags once the tracks are known)
     * lowers the player volume; items without it get the leveller instead. Both reset on every
     * item change, so a previous track's gain never applies to the next one.
     */
    @OptIn(UnstableApi::class) // Format.metadata
    private fun attachBalancedVolume(player: ExoPlayer, preferences: AppPreferences) {
        var settings = PlaybackPreferences()
        var gain: ReplayGain? = null
        fun apply() {
            val factor = if (settings.balancedVolume) replayGainVolume(gain, settings.replayGainMode) else null
            playerVolume?.setReplayGain(factor ?: 1f)
            leveler.setEnabled(settings.balancedVolume && factor == null)
        }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                gain = null
                apply()
            }

            override fun onTracksChanged(tracks: Tracks) {
                gain = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                    ?.getTrackFormat(0)?.metadata?.let(::replayGainOf)
                apply()
            }
        })
        serviceScope.launch {
            preferences.playback.distinctUntilChanged { a, b ->
                a.balancedVolume == b.balancedVolume && a.replayGainMode == b.replayGainMode
            }.collect {
                settings = it
                apply()
            }
        }
    }

    /** The sleep timer runs here; its state reaches the UI through the session extras. */
    private fun attachSleepTimer(player: ExoPlayer, volume: PlayerVolume, session: MediaSession) {
        val timer = SleepTimer(
            player = object : SleepTimerPlayer {
                override fun pause() = player.pause()

                @OptIn(UnstableApi::class) // pauseAtEndOfMediaItems
                override fun setPauseAtEndOfItem(enabled: Boolean) {
                    player.pauseAtEndOfMediaItems = enabled
                }

                override fun remainingInItemMs(): Long? =
                    player.duration.takeIf { it != C.TIME_UNSET && player.isPlaying }?.let { it - player.currentPosition }

                override fun setFade(fade: Float) = volume.setFade(fade)
            },
            scope = serviceScope,
            now = SystemClock::elapsedRealtime,
            onStateChanged = { session.setSessionExtras(SleepTimerSession.toExtras(it)) },
        )
        sleepTimer = timer
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    timer.onPausedAtEndOfItem()
                }
            }
        })
    }

    private fun onSleepCommand(action: String, args: Bundle): Boolean {
        val timer = sleepTimer ?: return false
        when (action) {
            SleepTimerSession.ACTION_START -> if (args.containsKey(SleepTimerSession.ARG_MINUTES)) {
                timer.startMinutes(args.getInt(SleepTimerSession.ARG_MINUTES))
            } else {
                timer.startEndOfTrack()
            }
            SleepTimerSession.ACTION_EXTEND -> timer.extend(args.getInt(SleepTimerSession.ARG_MINUTES))
            SleepTimerSession.ACTION_CANCEL -> timer.cancel()
            else -> return false
        }
        return true
    }

    /**
     * Notification texts (shuffle/repeat buttons, Media3's own labels) follow the app language.
     * Android 13+ applies it to every context; before that AppCompat only localises activities,
     * so the service applies the chosen locales to its own configuration here.
     */
    override fun attachBaseContext(base: Context) {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || locales.isEmpty) {
            super.attachBaseContext(base)
            return
        }
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Media3 returns START_STICKY, so after the process is killed the system recreates the
     * service with an empty player while the old "playing" notification lingers. The queue is
     * restored by the UI when the app is opened again (see QueueSaver), not by the system
     * restarting the service: don't ask to be restarted.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        idleHandler.removeCallbacks(stopWhenIdle)
        queueSaver?.stop()
        artworkExecutor.shutdownNow()
        serviceScope.cancel()
        audioEffects.release()
        leveler.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    // Rendered once, on first use, from the same vector the app shows.
    private val defaultArtwork: Bitmap by lazy {
        ContextCompat.getDrawable(this, R.drawable.veyra_default_artwork)!!
            .toBitmap(DEFAULT_ARTWORK_PX, DEFAULT_ARTWORK_PX)
    }

    /** Notification / lock screen artwork: the embedded cover, or the default Veyra artwork. */
    @OptIn(UnstableApi::class)
    private fun artworkLoader() = DefaultArtworkBitmapLoader(
        delegate = DataSourceBitmapLoader.Builder(this)
            .setMaximumOutputDimension(DEFAULT_ARTWORK_PX * 2)
            .build(),
        defaultArtwork = { defaultArtwork },
        // No embedded cover: try a separate image file, with the same loader the app uses.
        fallback = { metadata ->
            metadata.extras?.getString(EXTRA_CONTENT_URI)?.let { uri ->
                runBlocking { ArtworkLoader.get(this@PlaybackService).load(uri, ArtworkSize.Large) }
            }
        },
        fallbackExecutor = artworkExecutor,
    )

    private val artworkExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    /** Tapping the notification (or the lock screen media card) opens the app on the player. */
    private fun openPlayerIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun scheduleIdleStop() {
        idleHandler.removeCallbacks(stopWhenIdle)
        idleHandler.postDelayed(stopWhenIdle, IDLE_STOP_DELAY_MS)
    }

    private inner class IdleStopTimer : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) idleHandler.removeCallbacks(stopWhenIdle) else scheduleIdleStop()
        }
    }

    /** On an unplayable item (deleted or unreadable file), moves on to the next one. */
    private inner class SkipBrokenItems(private val player: Player) : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (errorSkipPolicy.shouldSkip(player.mediaItemCount, player.hasNextMediaItem())) {
                player.seekToNextMediaItem()
                // An error leaves the player idle; prepare again. playWhenReady is still true.
                player.prepare()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) errorSkipPolicy.reset()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) errorSkipPolicy.reset()
        }
    }

    private companion object {
        // Longer than Media3's 10-minute foreground timeout, so the notification has already
        // become dismissible when the service goes away.
        const val IDLE_STOP_DELAY_MS = 15 * 60 * 1000L

        // Media notifications show artwork at most a few hundred pixels wide.
        const val DEFAULT_ARTWORK_PX = 512
    }
}
