package com.example.veyra.data.playlists

import com.example.veyra.data.playlists.PlaylistNameValidation.Duplicate
import com.example.veyra.data.playlists.PlaylistNameValidation.Empty
import com.example.veyra.data.playlists.PlaylistNameValidation.TooLong
import com.example.veyra.data.playlists.PlaylistNameValidation.Valid
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PlaylistRulesTest {

    private fun validate(raw: String, existing: List<String> = emptyList()) =
        validatePlaylistName(raw, existing, Locale.ROOT)

    @Test
    fun name_isTrimmedAndInnerSpacesCollapsed() {
        assertEquals(Valid("Noite de Verão"), validate("  Noite   de\tVerão  "))
    }

    @Test
    fun blankNames_areRejected() {
        assertEquals(Empty, validate(""))
        assertEquals(Empty, validate("   \n "))
    }

    @Test
    fun lengthLimit_countsCharactersNotBytes() {
        assertEquals(Valid("a".repeat(PLAYLIST_NAME_MAX_LENGTH)), validate("a".repeat(PLAYLIST_NAME_MAX_LENGTH)))
        assertEquals(TooLong, validate("a".repeat(PLAYLIST_NAME_MAX_LENGTH + 1)))
        // Emoji are two UTF-16 units but one character.
        assertEquals(Valid("🎵".repeat(PLAYLIST_NAME_MAX_LENGTH)), validate("🎵".repeat(PLAYLIST_NAME_MAX_LENGTH)))
    }

    @Test
    fun duplicates_ignoreCaseAndAccents() {
        val existing = listOf("Verão", "Treino")
        assertEquals(Duplicate, validate("verao", existing))
        assertEquals(Duplicate, validate(" TREINO ", existing))
        assertEquals(Valid("Treino 2"), validate("Treino 2", existing))
    }

    @Test
    fun renamingToOwnNameWithDifferentCase_isAllowedWhenExcluded() {
        // The caller passes the other playlists' names only.
        assertEquals(Valid("treino"), validate("treino", existing = listOf("Verão")))
    }

    @Test
    fun moved_reordersAndIgnoresInvalidIndexes() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), list.moved(0, 2))
        assertEquals(listOf("a", "d", "b", "c"), list.moved(3, 1))
        assertEquals(list, list.moved(1, 1))
        assertEquals(list, list.moved(-1, 2))
        assertEquals(list, list.moved(0, 4))
    }
}
