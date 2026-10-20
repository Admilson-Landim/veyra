package io.github.admilsonlandim.veyra

import android.app.Application
import android.os.StrictMode
import android.util.Log
import io.github.admilsonlandim.veyra.playback.MediaKeyCrashGuard
import io.github.admilsonlandim.veyra.ui.options.RingtoneExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VeyraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        MediaKeyCrashGuard.install()
        // Debug builds only: log any disk or network access made on the main thread.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build(),
            )
        }
        // A ringtone excerpt interrupted by the process being killed leaves a temporary file and
        // a pending MediaStore entry; nothing created from now on is touched.
        val startedAtSeconds = System.currentTimeMillis() / 1000
        processScope.launch {
            runCatching { RingtoneExporter.cleanUpLeftovers(this@VeyraApplication, startedAtSeconds) }
                .onFailure { Log.w(TAG, "Couldn't clean up interrupted ringtone excerpts", it) }
        }
    }

    companion object {
        private const val TAG = "VeyraApplication"

        /**
         * For short IO that must finish even when the screen or service that started it goes
         * away (e.g. the last queue save). Lives as long as the process.
         */
        val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
