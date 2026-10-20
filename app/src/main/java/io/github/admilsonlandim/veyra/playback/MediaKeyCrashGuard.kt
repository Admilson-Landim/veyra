package io.github.admilsonlandim.veyra.playback

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Keeps one known Media3 bug from crashing the app.
 *
 * When a media key reaches the session through MediaSessionManager (as `adb shell media dispatch`
 * does, and Bluetooth controls may), EMUI on Android 10 reports the sender with an empty package
 * name. Media3 1.11.1 (`MediaSessionCompat.MediaSessionImplApi28.getCurrentControllerInfo`, also
 * on its release branch) wraps it in a RemoteUserInfo, whose constructor throws
 * IllegalArgumentException("packageName should be nonempty") on the main thread, before any app
 * callback runs. Nothing public can intercept it.
 *
 * The guard runs the main loop inside a try/catch: that exception, recognised by its exact
 * message (class and method names are renamed in release builds), is logged and the loop goes
 * on; that one key press is lost. Every other exception is rethrown and crashes as before.
 * Remove it once Media3 handles the empty package name.
 */
object MediaKeyCrashGuard {

    private const val TAG = "MediaKeyCrashGuard"
    private const val MEDIA3_EMPTY_PACKAGE = "packageName should be nonempty"

    /** Call once, on the main thread, from Application.onCreate. */
    fun install() {
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                } catch (e: IllegalArgumentException) {
                    if (!isEmptyPackageBug(e)) throw e
                    Log.w(TAG, "Ignored a media key from a controller without a package name", e)
                }
            }
        }
    }

    fun isEmptyPackageBug(e: Throwable): Boolean = e is IllegalArgumentException && e.message == MEDIA3_EMPTY_PACKAGE
}
