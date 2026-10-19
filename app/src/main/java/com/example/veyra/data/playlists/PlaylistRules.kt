package com.example.veyra.data.playlists

import java.text.Collator
import java.util.Locale

const val PLAYLIST_NAME_MAX_LENGTH = 60

sealed interface PlaylistNameValidation {
    /** [name] is the cleaned-up name to store. */
    data class Valid(val name: String) : PlaylistNameValidation
    data object Empty : PlaylistNameValidation
    data object TooLong : PlaylistNameValidation
    data object Duplicate : PlaylistNameValidation
}

/**
 * Trims the name, collapses inner whitespace and checks it.
 *
 * Duplicates are not allowed: the "add to playlist" menu shows only names, so two playlists
 * called the same would be indistinguishable there. Comparison ignores case and accents
 * ("Verão" equals "verao"), like the library sort.
 *
 * @param existingNames names of the other playlists (when renaming, exclude the playlist itself,
 *   so changing only its capitalisation is allowed).
 */
fun validatePlaylistName(
    raw: String,
    existingNames: Collection<String>,
    locale: Locale = Locale.getDefault(),
): PlaylistNameValidation {
    val name = raw.trim().replace(Regex("\\s+"), " ")
    if (name.isEmpty()) return PlaylistNameValidation.Empty
    if (name.codePointCount(0, name.length) > PLAYLIST_NAME_MAX_LENGTH) return PlaylistNameValidation.TooLong
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    if (existingNames.any { collator.compare(it, name) == 0 }) return PlaylistNameValidation.Duplicate
    return PlaylistNameValidation.Valid(name)
}

/** Returns a copy with the item at [from] moved to [to]; out-of-range indexes leave it unchanged. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
