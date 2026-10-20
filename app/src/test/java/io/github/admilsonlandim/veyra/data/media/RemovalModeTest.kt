package io.github.admilsonlandim.veyra.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class RemovalModeTest {

    @Test
    fun trashFromAndroid11_deleteBefore() {
        assertEquals(RemovalMode.Delete, removalModeFor(24))
        assertEquals(RemovalMode.Delete, removalModeFor(28))
        assertEquals(RemovalMode.Delete, removalModeFor(29))
        assertEquals(RemovalMode.Trash, removalModeFor(30))
        assertEquals(RemovalMode.Trash, removalModeFor(37))
    }
}
