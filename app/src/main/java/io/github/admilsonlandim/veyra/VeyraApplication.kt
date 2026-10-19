package io.github.admilsonlandim.veyra

import android.app.Application
import android.os.StrictMode

class VeyraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
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
