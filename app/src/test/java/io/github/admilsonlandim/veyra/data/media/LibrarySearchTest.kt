package io.github.admilsonlandim.veyra.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {

    private fun track(id: Long, title: String?, artist: String? = null, album: String? = null, path: String? = null) = Track(
        id = id, contentUri = "", title = title, artist = artist, album = album, albumId = 0, durationMs = 1_000,
        displayName = null, relativePath = path, sizeBytes = 0, dateModifiedSeconds = 0,
    )

    @Test
    fun ignoresCaseAndAccents_bothWays() {
        assertTrue(SearchQuery("joao").matches("João"))
        assertTrue(SearchQuery("JOÃO").matches("joao"))
        assertTrue(SearchQuery("funana").matches("Febri Funaná"))
        assertFalse(SearchQuery("joana").matches("João"))
    }

    @Test
    fun everyWordMustMatch_inAnyField_inAnyOrder() {
        val tracks = listOf(
            track(1, "Soja", "Black Sherif"),
            track(2, "Soja Remix", "Other"),
            track(3, "Kwaku", "Black Sherif"),
        )
        assertEquals(listOf(1L), tracks.search(SearchQuery("sherif soja")).map { it.id })
        assertEquals(listOf(1L, 2L), tracks.search(SearchQuery("  soja  ")).map { it.id })
    }

    @Test
    fun searchesTitleArtistAlbumAndFolderName_butNotTheParentFolders() {
        val tracks = listOf(
            track(1, "A", album = "Utopia"),
            track(2, "B", path = "VidMate/download/"),
            track(3, "C", artist = "Ferro Gaita"),
        )
        assertEquals(listOf(1L), tracks.search(SearchQuery("utopia")).map { it.id })
        assertEquals(listOf(2L), tracks.search(SearchQuery("download")).map { it.id })
        assertEquals(emptyList<Long>(), tracks.search(SearchQuery("vidmate")).map { it.id })
        assertEquals(listOf(3L), tracks.search(SearchQuery("gaita")).map { it.id })
    }

    @Test
    fun emptyFields_areSkipped_andBlankQueriesMatchNothing() {
        val tracks = listOf(track(1, null), track(2, "Song"))
        assertEquals(listOf(2L), tracks.search(SearchQuery("song")).map { it.id })
        assertTrue(SearchQuery("   ").isBlank)
        assertEquals(emptyList<Track>(), tracks.search(SearchQuery("")))
    }

    @Test
    fun keepsTheGivenOrder() {
        val tracks = listOf(track(3, "ab"), track(1, "abc"), track(2, "xab"))
        assertEquals(listOf(3L, 1L, 2L), tracks.search(SearchQuery("ab")).map { it.id })
    }

    @Test
    fun foldersMatchNameOrPath_artistsMatchName() {
        val folders = listOf(FolderGroup("VidMate/download/", emptyList()), FolderGroup("Music/Kizomba/", emptyList()))
        assertEquals(listOf("download"), folders.searchFolders(SearchQuery("vidmate")).map { it.name })
        assertEquals(listOf("Kizomba"), folders.searchFolders(SearchQuery("kizomba")).map { it.name })

        val artists = listOf(ArtistGroup("helio batalha", "Hélio Batalha", emptyList()), ArtistGroup(null, null, emptyList()))
        assertEquals(listOf("Hélio Batalha"), artists.searchArtists(SearchQuery("helio")).map { it.name })
    }
}
