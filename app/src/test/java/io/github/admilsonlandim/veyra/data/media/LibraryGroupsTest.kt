package io.github.admilsonlandim.veyra.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class LibraryGroupsTest {

    private fun track(id: Long, path: String?, artist: String? = null, title: String = "t$id") = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = title, artist = artist,
        album = null, albumId = 0, durationMs = 1_000, displayName = null, relativePath = path,
        sizeBytes = 0, dateModifiedSeconds = 0,
    )

    @Test
    fun folders_groupByPathIgnoringCase_sortedByName_keepingTrackOrder() {
        val tracks = listOf(
            track(1, "VidMate/download/"),
            track(2, "Music/Zouk/"),
            track(3, "vidmate/Download/"),
            track(4, "Music/"),
            track(5, "Música/Ábaco/"),
        )

        val groups = tracks.groupByFolder(Locale.ROOT)

        assertEquals(listOf("Ábaco", "download", "Music", "Zouk"), groups.map { it.name })
        assertEquals(listOf(1L, 3L), groups[1].tracks.map { it.id })
        assertEquals("VidMate/download/", groups[1].path)
    }

    @Test
    fun folders_rootAndUnknownGoLast() {
        val groups = listOf(track(1, null), track(2, ""), track(3, "Music/")).groupByFolder(Locale.ROOT)

        assertEquals(listOf("Music/", "", null), groups.map { it.path })
        assertNull(groups[1].name)
        assertEquals(FolderGroup.UNKNOWN_FOLDER_ID, groups[2].id)
        assertEquals(3, groups.map { it.id }.toSet().size)
    }

    @Test
    fun folders_shortPath_keepsTheLastSegments() {
        assertEquals("Music/Album", FolderGroup("Music/Album/", emptyList()).shortPath())
        assertEquals("…/b/c/d", FolderGroup("a/b/c/d/", emptyList()).shortPath())
        assertNull(FolderGroup("", emptyList()).shortPath())
        assertNull(FolderGroup(null, emptyList()).shortPath())
    }

    @Test
    fun artists_normaliseCaseAccentsAndSpaces_andShowTheMostFrequentSpelling() {
        val tracks = listOf(
            track(1, null, "Helio Batalha"),
            track(2, null, "Hélio Batalha"),
            track(3, null, "helio  batalha"),
            track(4, null, "Hélio Batalha"),
        )

        val groups = tracks.groupByArtist(Locale.ROOT)

        assertEquals(1, groups.size)
        assertEquals("Hélio Batalha", groups.single().name)
        assertEquals(listOf(1L, 2L, 3L, 4L), groups.single().tracks.map { it.id })
    }

    @Test
    fun artists_collaborationsCountForTheMainArtistOnly() {
        val tracks = listOf(
            track(1, null, "Black Sherif"),
            track(2, null, "Black Sherif, Seyi Vibez"),
            track(3, null, "Joeboy ft. Qing Madi"),
            track(4, null, "Seyi Vibez"),
            track(5, null, "Simon & Garfunkel"),
        )

        val groups = tracks.groupByArtist(Locale.ROOT)

        assertEquals(listOf("Black Sherif", "Joeboy", "Seyi Vibez", "Simon & Garfunkel"), groups.map { it.name })
        assertEquals(listOf(1L, 2L), groups[0].tracks.map { it.id })
        assertEquals(listOf(4L), groups[2].tracks.map { it.id })
    }

    @Test
    fun artists_unknownGroupsTracksWithoutArtist_andGoesLast() {
        val groups = listOf(track(1, null), track(2, null, "Zé"), track(3, null)).groupByArtist(Locale.ROOT)

        assertEquals(listOf("Zé", null), groups.map { it.name })
        assertEquals(listOf(1L, 3L), groups[1].tracks.map { it.id })
        assertEquals("", groups[1].id)
    }

    @Test
    fun artists_sortedByNameIgnoringCaseAndAccents() {
        val groups = listOf(track(1, null, "zouk"), track(2, null, "Ánia"), track(3, null, "bia")).groupByArtist(Locale.ROOT)
        assertEquals(listOf("Ánia", "bia", "zouk"), groups.map { it.name })
    }
}
