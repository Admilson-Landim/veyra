package io.github.admilsonlandim.veyra.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NameCleaningTest {

    /** A file without tags, as MediaStore reports it: the title is the file name. */
    private fun untagged(fileName: String): DisplayNames = displayNames(fileBaseName(fileName), null, fileName)

    private fun assertNames(fileName: String, artist: String?, title: String) =
        assertEquals(fileName, DisplayNames(title, artist), untagged(fileName))

    @Test
    fun specExample_underscoresOfficialVideoAndYoutubeId() {
        assertNames("Black_Sherif_-_Soja_(Official_Video)_[a1b2c3].mp3", "Black Sherif", "Soja")
    }

    // Real names from a VidMate library: brackets and apostrophes became underscores.
    @Test
    fun vidMateNames() {
        assertNames("Black_Sherif_-_Soja__Official_Visualizer_(256k).mp3", "Black Sherif", "Soja")
        assertNames("Black_Sherif_-_Dreamer__Visualizer_(256k).mp3", "Black Sherif", "Dreamer")
        assertNames("Khaid_-_Anabella__Official_Music_Video_(256k).mp3", "Khaid", "Anabella")
        assertNames("Fireboy_DML_-_Like_I_Do__Audio_(256k).mp3", "Fireboy DML", "Like I Do")
        assertNames("Ferro_Gaita_-_Fundo_Baxo_[Official_Video](256k).mp3", "Ferro Gaita", "Fundo Baxo")
        assertNames("Ferro_Gaita_-_Carne_Salgado__Oficial_vídeo_2024_(256k).mp3", "Ferro Gaita", "Carne Salgado")
        assertNames("Arila_Gang_-_Medo__Video_Official_(256k).mp3", "Arila Gang", "Medo")
        assertNames("Alice_Wonder_-__No_Te_Vayas___Oficial_Lyric_Video_(256k).mp3", "Alice Wonder", "No Te Vayas")
        assertNames("Jay_Moreira-_Pa_Mundu__Video_Oficial__(256k).mp3", "Jay Moreira", "Pa Mundu")
        assertNames("Arila_feat._Bruno_-_Foca__Vídeo_letra__2020(256k).mp3", "Arila feat. Bruno", "Foca")
    }

    @Test
    fun vidMateCopyCounterAfterBitrate() {
        assertNames("Alpha_Blondy___I_Wish_You_Were_Here(256k)-49.mp3", null, "Alpha Blondy I Wish You Were Here")
    }

    @Test
    fun apostrophesReplacedByUnderscores_areRestored() {
        assertNames("Black_Sherif_-_Lord_I_m_Amazed__Official_Video_(256k).mp3", "Black Sherif", "Lord I'm Amazed")
        assertNames("LOVIXX,_STOSLIV_-_Don_t_tell_your_dreams__Lyrics_(256k).mp3", "LOVIXX, STOSLIV", "Don't tell your dreams")
    }

    @Test
    fun tripleUnderscoreInArtist_isAnAmpersand() {
        assertNames("Gyakie___Omah_Lay_-_Forever__Remix___Official_Music_Video_(256k).mp3", "Gyakie & Omah Lay", "Forever Remix")
        assertNames("Alcides___Vanir_-_E_Só_Amor(256k).mp3", "Alcides & Vanir", "E Só Amor")
    }

    @Test
    fun featuringAndRemix_areKept() {
        assertNames("Burna_Boy_-_For_My_Hand_feat._Ed_Sheeran_[Official_Music_Video](256k).mp3", "Burna Boy", "For My Hand feat. Ed Sheeran")
        assertNames("Majeeed_-_Cry__shayo___Official_Video__[feat._Lojay](256k).mp3", "Majeeed", "Cry shayo [feat. Lojay]")
        assertNames("Post_Malone_-_Congratulations__Official_Music_Video__ft._Quavo(256k).mp3", "Post Malone", "Congratulations ft. Quavo")
        assertNames("Artist - Song (Remix) (Official Video).mp3", "Artist", "Song (Remix)")
        assertNames("Artist - Song (feat. Guest) [Lyric Video].mp3", "Artist", "Song (feat. Guest)")
        assertNames("Artist - Song (Live) (Audio).mp3", "Artist", "Song (Live)")
        assertNames("Artist - Song (Acoustic) [HD].flac", "Artist", "Song (Acoustic)")
        assertNames("Joeboy_ft._Qing_Madi_-_Adenuga_[Official_Music_Video](256k).mp3", "Joeboy ft. Qing Madi", "Adenuga")
    }

    @Test
    fun noiseInTheMiddle_withoutBrackets_isRemoved() {
        assertNames(
            "Justin_Bieber_-_Intentions__Official_Video__Short_Version___ft._Quavo(256k).mp3",
            "Justin Bieber",
            "Intentions Short Version ft. Quavo",
        )
        assertNames("Davis_Varela_-_Nunka_mas_ft._Brou_A.S___Video_official__4K(256k).mp3", "Davis Varela", "Nunka mas ft. Brou A.S")
    }

    @Test
    fun productionCredits_areRemoved() {
        assertNames("Apollo_G_-_Bua_ft._Puto_Fox__Official_Video__Prod_by._Mr_Marley(256k).mp3", "Apollo G", "Bua ft. Puto Fox")
        assertNames("Prodígio_-_Cigana__Prod__G5_LDN__2013(256k).mp3", "Prodígio", "Cigana")
        assertNames("Artist - Song (Prod. by Someone).mp3", "Artist", "Song")
        assertNames(
            "Loose_Jr_ft_Harley_Ksd_-_Abo_eh_so_benço__Official__Prod__Le_south___Sanysan(256k).mp3",
            "Loose Jr ft Harley Ksd",
            "Abo eh so benço",
        )
    }

    @Test
    fun legitimateHyphens_doNotSplit() {
        assertNames("T-Rex_-_TA_TUDO_BEM(256k).mp3", "T-Rex", "TA TUDO BEM")
        assertNames("SHADE-B__---_UM_PASSO(256k).mp3", "SHADE-B", "UM PASSO")
        assertNames("NEIDO_WEST-AGUENTA_BO_BRONKA(256k).mp3", null, "NEIDO WEST-AGUENTA BO BRONKA")
        assertNames(
            "Post_Malone,_Swae_Lee_-_Sunflower__Spider-Man__Into_the_Spider-Verse_(256k).mp3",
            "Post Malone, Swae Lee",
            "Sunflower Spider-Man Into the Spider-Verse",
        )
    }

    @Test
    fun severalSeparators_splitAtTheFirst() {
        assertNames("Artist - Song - Live at Wembley.mp3", "Artist", "Song - Live at Wembley")
        assertNames("Artist – Song.mp3", "Artist", "Song")
        assertNames("Artist — Song.mp3", "Artist", "Song")
    }

    @Test
    fun noSeparator_titleOnly() {
        assertNames("Bitori_Nha_Bibinha(256k).mp3", null, "Bitori Nha Bibinha")
        assertNames("Cabalo(256k).mp3", null, "Cabalo")
    }

    @Test
    fun trackNumbers_areRemovedWhenClearlyNumbers() {
        assertNames("01 - Song.mp3", null, "Song")
        assertNames("01. Song.mp3", null, "Song")
        assertNames("07 Song.mp3", null, "Song")
        assertNames("3. Song.mp3", null, "Song")
        assertNames("01 - Artist - Song.mp3", "Artist", "Song")
        assertNames("4_-_Apenas_Palavras_part._Edyoung_Lennon(256k).mp3", null, "Apenas Palavras part. Edyoung Lennon")
    }

    @Test
    fun numbersThatAreNames_areKept() {
        assertNames("112 - Cupid.mp3", "112", "Cupid")
        assertNames("50 Cent - In da Club.mp3", "50 Cent", "In da Club")
        assertNames("HARLEY_KSD_x_LANDIM_-_Number_1(256k).mp3", "HARLEY KSD x LANDIM", "Number 1")
        assertNames("1.5 Degrees.mp3", null, "1.5 Degrees")
    }

    @Test
    fun youtubeIdsAndSites_areRemoved() {
        assertNames("Artist - Song [dQw4w9WgXcQ].mp3", "Artist", "Song")
        assertNames("Artist - Song (www.mp3site.com).mp3", "Artist", "Song")
        assertNames("Artist - Song - mp3juices.cc.mp3", "Artist", "Song")
        // Bracketed words of the same length are not ids.
        assertNames("Artist - Song (Live_Studio).mp3", "Artist", "Song (Live Studio)")
    }

    @Test
    fun noiseWordsThatAreTheSong_areKept() {
        assertNames("Music.mp3", null, "Music")
        assertNames("Artist - Audio.mp3", "Artist", "Audio")
        assertNames("Artist - Home Video.mp3", "Artist", "Home Video")
        assertNames("Official Video.mp3", null, "Official Video")
    }

    @Test
    fun validTags_areNeverChanged() {
        // Title and artist tags: nothing to do, even if the file name says otherwise.
        assertEquals(
            DisplayNames("Soja", "Black Sherif"),
            displayNames("Soja", "Black Sherif", "Black_Sherif_-_Something_Else_(Official_Video).mp3"),
        )
        // A title that is close to the file name but not the same is a real tag.
        assertEquals(
            DisplayNames("Veyra Teste (com capa)", "Veyra Teste"),
            displayNames("Veyra Teste (com capa)", "Veyra Teste", "Veyra Teste - com capa.mp3"),
        )
        assertEquals(DisplayNames("Feeling Good", null), displayNames("Feeling Good", null, "track01.mp3"))
    }

    @Test
    fun titleTagWithoutArtist_takesTheArtistOnlyWhenTheFileNameMatches() {
        assertEquals(
            DisplayNames("Soja", "Black Sherif"),
            displayNames("Soja", null, "Black_Sherif_-_Soja__Official_Visualizer_(256k).mp3"),
        )
        assertEquals(
            DisplayNames("Soja (Official Video)", "Black Sherif"),
            displayNames("Soja (Official Video)", null, "Black Sherif - Soja (Official Video).mp3"),
        )
        assertEquals(DisplayNames("Another Song", null), displayNames("Another Song", null, "Black Sherif - Soja.mp3"))
    }

    @Test
    fun artistTagWithFileNameTitle_splitsOnlyWhenTheArtistsAgree() {
        assertEquals(
            DisplayNames("soja", "Black Sherif"),
            displayNames("black_sherif_-_soja_(official_video)", "Black Sherif", "black_sherif_-_soja_(official_video).mp3"),
        )
        // The part before the dash is not this artist: keep the whole cleaned name.
        assertEquals(
            DisplayNames("Soja - Remix", "Black Sherif"),
            displayNames("Soja - Remix (Official Audio)", "Black Sherif", "Soja - Remix (Official Audio).mp3"),
        )
    }

    @Test
    fun titleSameAsFileName_ignoringCaseUnderscoresAndSpacing() {
        assertEquals(
            DisplayNames("Soja", "Black Sherif"),
            displayNames("Black Sherif  -  Soja (OFFICIAL VIDEO)", null, "Black_Sherif_-_Soja_(Official_Video).mp3"),
        )
    }

    @Test
    fun missingTitle_usesTheFileName() {
        assertEquals(DisplayNames("Soja", "Black Sherif"), displayNames(null, null, "Black Sherif - Soja.mp3"))
    }

    @Test
    fun noFileName_keepsTheTags() {
        assertEquals(DisplayNames("A", null), displayNames("A", null, null))
        assertEquals(DisplayNames(null, null), displayNames(null, null, null))
    }

    @Test
    fun fileBaseName_dropsOnlyARealExtension() {
        assertEquals("song", fileBaseName("song.mp3"))
        assertEquals("my.song", fileBaseName("my.song.flac"))
        assertEquals("Brou A.S - Mundo", fileBaseName("Brou A.S - Mundo"))
        assertEquals(".mp3", fileBaseName(".mp3"))
    }

    @Test
    fun parseFileName_nothingUsable_isNull() {
        assertNull(parseFileName("(256k)"))
        assertNull(parseFileName("   "))
    }

    @Test
    fun withCleanNames_returnsTheSameTrackWhenNothingChanges() {
        val track = Track(
            id = 1, contentUri = "", title = "Soja", artist = "Black Sherif", album = null, albumId = 0,
            durationMs = 1, displayName = "soja.mp3", relativePath = null, sizeBytes = 0, dateModifiedSeconds = 0,
        )
        assertSame(track, track.withCleanNames())
        val untagged = track.copy(title = "Black_Sherif_-_Soja", artist = null, displayName = "Black_Sherif_-_Soja.mp3")
        assertEquals("Soja" to "Black Sherif", untagged.withCleanNames().let { it.title to it.artist })
    }

    @Test
    fun primaryArtist_splitsCollaborationsButNotDuos() {
        assertEquals("Joeboy", primaryArtist("Joeboy ft. Qing Madi"))
        assertEquals("Joeboy", primaryArtist("Joeboy feat Qing Madi"))
        assertEquals("Joeboy", primaryArtist("Joeboy featuring Qing Madi"))
        assertEquals("Black Sherif", primaryArtist("Black Sherif, Seyi Vibez"))
        assertEquals("Danni Gato", primaryArtist("Danni Gato x Loreta KBA"))
        assertEquals("Edyoung", primaryArtist("Edyoung part. V3ND3TT4"))
        assertEquals("Simon & Garfunkel", primaryArtist("Simon & Garfunkel"))
        assertEquals("AC/DC", primaryArtist("AC/DC"))
        assertEquals("Malcolm X", primaryArtist("Malcolm X"))
    }

    @Test
    fun foldForMatching_ignoresCaseAccentsAndSpaces() {
        assertEquals("helio batalha", foldForMatching("  Hélio   BATALHA "))
        assertEquals("joao", foldForMatching("João"))
    }
}
