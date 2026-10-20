package io.github.admilsonlandim.veyra.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TagEncodingTest {

    /** What a tagger produces when UTF-8 bytes are read as Windows-1252. */
    private fun mojibake(text: String): String =
        String(text.toByteArray(Charsets.UTF_8), charset("windows-1252"))

    @Test
    fun repairsUtf8ReadAsWindows1252() {
        assertEquals("João", repairMojibake("JoÃ£o"))
        assertEquals("Don’t", repairMojibake("Donâ€™t"))
        assertEquals("Café", repairMojibake("CafÃ©"))
        assertEquals("Hélio Batalha", repairMojibake("HÃ©lio Batalha"))
        assertEquals("Ângela", repairMojibake("Ã‚ngela"))
        assertEquals("1º", repairMojibake("1Âº"))
    }

    @Test
    fun repairsWhatTheEncodingRoundTripProduces() {
        listOf("São Vicente", "Funaná", "Nhós Mantenha", "Ç’est la vie", "Mudjer Di Nhó", "Ferro Gaita — Rei Di Tabanka")
            .forEach { assertEquals(it, repairMojibake(mojibake(it))) }
    }

    @Test
    fun repairsTextMisreadTwice() {
        assertEquals("João", repairMojibake(mojibake(mojibake("João"))))
    }

    @Test
    fun correctNamesAreNeverChanged() {
        listOf(
            "Ângela", "São Vicente", "Café", "João", "Funaná", "Nhós Mantenha", "Prodígio", "Força Suprema",
            "Mudjer Di Nhó", "Tradição", "Bandera liberdade", "NEIDO WEST-NÓS BIFE E KU POBREZA ✊",
            "Don’t tell your dreams", "Beyoncé", "Sigur Rós", "Motörhead", "Ñandú", "Ça va", "À la", "Æon",
            "Björk", "Ølstykke", "naïve", "Zoë", "", "ASCII only", "🎵 Música", "Ãrvore", "Âmbar",
            "Ç’est la vie", "É“Ola”",
        ).forEach { assertEquals(it, repairMojibake(it)) }
    }

    @Test
    fun aRepairThatWouldNotBeValidUtf8_isRejected() {
        // Looks like mojibake (Ã + ©) but the bytes C3 A9 then 'C3' alone are not valid UTF-8.
        assertEquals("Ã©Ã", repairMojibake("Ã©Ã"))
        // Russian read as Windows-1252 is repaired to Cyrillic.
        assertEquals("Привет", repairMojibake(mojibake("Привет")))
        // A character outside Windows-1252 means it is not a byte-for-byte misreading.
        assertEquals("JoÃ£o ✊", repairMojibake("JoÃ£o ✊"))
    }

    @Test
    fun lostCharacters_cannotBeRepaired() {
        assertEquals("Jo�o", repairMojibake("Jo�o"))
        assertTrue(hasLostCharacters("Jo�o"))
        assertFalse(hasLostCharacters("João"))
        assertFalse(hasLostCharacters(null))
    }

    private fun track(title: String?, artist: String?, album: String? = null, file: String? = "Black_Sherif_-_Soja.mp3") = Track(
        id = 1, contentUri = "", title = title, artist = artist, album = album, albumId = 0, durationMs = 1,
        displayName = file, relativePath = null, sizeBytes = 0, dateModifiedSeconds = 0,
    )

    @Test
    fun displayNames_repairTitleArtistAndAlbum() {
        val shown = track("CoraÃ§Ã£o", "JoÃ£o", "MÃºsica").withDisplayNames(fixEncoding = true, cleanNames = true)
        assertEquals(Triple("Coração", "João", "Música"), Triple(shown.title, shown.artist, shown.album))
    }

    @Test
    fun displayNames_lostCharacters_useTheFileNameWhenCleaningIsOn() {
        val shown = track("So�a", "Bl�ck Sherif").withDisplayNames(fixEncoding = true, cleanNames = true)
        assertEquals("Soja" to "Black Sherif", shown.title to shown.artist)

        // Without cleaning, or without a usable file name, the damaged text stays.
        assertEquals("So�a", track("So�a", null).withDisplayNames(fixEncoding = true, cleanNames = false).title)
        assertEquals("So�a", track("So�a", null, file = null).withDisplayNames(fixEncoding = true, cleanNames = true).title)
    }

    @Test
    fun displayNames_fixOff_leavesTheTags() {
        val original = track("JoÃ£o", "X")
        assertEquals("JoÃ£o", original.withDisplayNames(fixEncoding = false, cleanNames = true).title)
        val correct = track("João", "X")
        assertSame(correct, correct.withDisplayNames(fixEncoding = true, cleanNames = false))
    }
}
