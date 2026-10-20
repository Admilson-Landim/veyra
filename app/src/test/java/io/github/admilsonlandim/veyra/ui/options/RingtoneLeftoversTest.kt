package io.github.admilsonlandim.veyra.ui.options

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingtoneLeftoversTest {

    private val own = "io.github.admilsonlandim.veyra"
    private val startedAt = 1_000L

    @Test
    fun tempFiles_onlyTheExportersOwnNames() {
        assertTrue(RingtoneExporter.isLeftoverTempFile("ringtone-export123456.m4a"))
        assertTrue(RingtoneExporter.isLeftoverTempFile("ringtone-export.m4a")) // name used before Phase 17
        assertFalse(RingtoneExporter.isLeftoverTempFile("ringtone-export123.tmp"))
        assertFalse(RingtoneExporter.isLeftoverTempFile("image_cache.m4a"))
        assertFalse(RingtoneExporter.isLeftoverTempFile("artwork"))
    }

    @Test
    fun pendingExcerpts_ownOldEntriesInTheSoundFolders_areRemoved() {
        val rows = listOf(
            PendingAudio(1, "Ringtones/", own, 900),
            PendingAudio(2, "Notifications/", own, 999),
            PendingAudio(3, "Alarms/", own, 10),
        )
        assertEquals(listOf(1L, 2L, 3L), rows.filter { RingtoneExporter.isLeftoverExcerpt(it, own, startedAt) }.map { it.id })
    }

    @Test
    fun pendingEntries_ofOtherApps_otherFolders_orStartedSince_areNeverTouched() {
        val rows = listOf(
            PendingAudio(1, "Ringtones/", "com.other.app", 900),
            PendingAudio(2, "Music/", own, 900),
            PendingAudio(3, "Ringtones/Custom/", own, 900),
            PendingAudio(4, "Ringtones/", own, startedAt),
            PendingAudio(5, "Ringtones/", null, 900),
            PendingAudio(6, null, own, 900),
        )
        assertTrue(rows.none { RingtoneExporter.isLeftoverExcerpt(it, own, startedAt) })
    }
}
