package io.github.admilsonlandim.veyra.data.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android compiles regular expressions with ICU, which rejects some patterns the JVM accepts:
 * the cleaning (tested in depth by the unit tests) must also work on a device.
 */
@RunWith(AndroidJUnit4::class)
class NameCleaningDeviceTest {

    private fun untagged(fileName: String) = displayNames(fileBaseName(fileName), null, fileName)

    @Test
    fun cleansOnTheDevice() {
        assertEquals(DisplayNames("Soja", "Black Sherif"), untagged("Black_Sherif_-_Soja_(Official_Video)_[a1b2c3].mp3"))
        assertEquals(
            DisplayNames("Lord I'm Amazed", "Black Sherif"),
            untagged("Black_Sherif_-_Lord_I_m_Amazed__Official_Video_(256k).mp3"),
        )
        assertEquals(
            DisplayNames("Forever Remix", "Gyakie & Omah Lay"),
            untagged("Gyakie___Omah_Lay_-_Forever__Remix___Official_Music_Video_(256k).mp3"),
        )
        assertEquals(DisplayNames("Song (feat. X)", "Artist"), untagged("01 - Artist - Song (feat. X) {HD} (Prod. by Y) - www.site.com.mp3"))
        assertEquals("Black Sherif", primaryArtist("Black Sherif, Seyi Vibez"))
        assertEquals("joao", foldForMatching("João"))
    }
}
