package io.github.admilsonlandim.veyra.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SidecarArtworkIndexTest {

    @Test
    fun sameBaseName_inSameFolder_winsFirst() {
        val index = SidecarArtworkIndex(
            listOf(
                ImageEntry(1, "Music/Album/", "cover.jpg"),
                ImageEntry(2, "Music/Album/", "Song.PNG"),
            ),
        )
        assertEquals(2L, index.find(audioId = 99, folder = "Music/Album/", displayName = "song.mp3"))
    }

    @Test
    fun folderCover_whenNoSameName_inPreferenceOrder() {
        val index = SidecarArtworkIndex(
            listOf(
                ImageEntry(1, "Music/Album/", "albumart.jpg"),
                ImageEntry(2, "Music/Album/", "Folder.jpg"),
            ),
        )
        assertEquals(2L, index.find(99, "Music/Album/", "other.mp3"))
    }

    @Test
    fun vidmateThumbnail_matchesByAudioId_inSubfolder() {
        val index = SidecarArtworkIndex(listOf(ImageEntry(7, "VidMate/download/lm_thumbnail/", "177055.png")))
        assertEquals(7L, index.find(177055, "VidMate/download/", "Some_song(256k).mp3"))
        // Same number but another audio folder: not VidMate's thumbnail for this file.
        assertNull(index.find(177055, "Music/", "Some_song(256k).mp3"))
        assertNull(index.find(177056, "VidMate/download/", "x.mp3"))
    }

    @Test
    fun imagesInOtherFolders_orNonImages_doNotMatch() {
        val index = SidecarArtworkIndex(
            listOf(
                ImageEntry(1, "DCIM/Camera/", "song.jpg"),
                ImageEntry(2, "Music/", "song.txt"),
            ),
        )
        assertNull(index.find(5, "Music/", "song.mp3"))
    }

    @Test
    fun unknownFolder_findsNothing() {
        val index = SidecarArtworkIndex(listOf(ImageEntry(1, "Music/", "cover.jpg")))
        assertNull(index.find(5, folder = null, displayName = "song.mp3"))
    }

    @Test
    fun foldersCompareWithoutCaseOrSlashes() {
        val index = SidecarArtworkIndex(listOf(ImageEntry(1, "/music/Album", "cover.jpg")))
        assertEquals(1L, index.find(5, "Music/album/", "x.mp3"))
    }
}
