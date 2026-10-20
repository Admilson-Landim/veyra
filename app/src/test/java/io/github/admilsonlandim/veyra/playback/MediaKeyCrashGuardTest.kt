package io.github.admilsonlandim.veyra.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaKeyCrashGuardTest {

    @Test
    fun recognisesOnlyTheMedia3EmptyPackageError() {
        assertTrue(MediaKeyCrashGuard.isEmptyPackageBug(IllegalArgumentException("packageName should be nonempty")))
        assertFalse(MediaKeyCrashGuard.isEmptyPackageBug(IllegalArgumentException("something else")))
        assertFalse(MediaKeyCrashGuard.isEmptyPackageBug(IllegalArgumentException()))
        assertFalse(MediaKeyCrashGuard.isEmptyPackageBug(IllegalStateException("packageName should be nonempty")))
    }
}
