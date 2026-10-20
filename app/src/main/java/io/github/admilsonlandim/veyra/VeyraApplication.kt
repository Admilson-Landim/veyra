package io.github.admilsonlandim.veyra

import android.app.Application
import android.os.StrictMode
import io.github.admilsonlandim.veyra.playback.MediaKeyCrashGuard

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
    }
}
