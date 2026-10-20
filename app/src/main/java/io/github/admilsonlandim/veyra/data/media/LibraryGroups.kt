package io.github.admilsonlandim.veyra.data.media

import java.text.Collator
import java.util.Locale

/** How the Library is browsed. */
enum class LibraryView { Tracks, Folders, Artists }

/**
 * A folder that directly holds music. [path] is normalised ("Music/Album/"), "" for the storage
 * root, or null for tracks whose folder is unknown (Android 9 and older, outside the main
 * storage). [tracks] keep the order they were given in.
 */
data class FolderGroup(val path: String?, val tracks: List<Track>) {
    /** Stable identifier, unique among the groups (a normalised path never is "?"). */
    val id: String get() = path ?: UNKNOWN_FOLDER_ID

    /** Last segment of the path, or null for the root and unknown folders. */
    val name: String? get() = path?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }

    /** The path without its trailing slash, shortened to its last [maxSegments] folders. */
    fun shortPath(maxSegments: Int = 3): String? {
        val segments = path?.trimEnd('/')?.split('/')?.filter { it.isNotEmpty() } ?: return null
        if (segments.isEmpty()) return null
        return if (segments.size <= maxSegments) segments.joinToString("/") else "…/" + segments.takeLast(maxSegments).joinToString("/")
    }

    companion object {
        const val UNKNOWN_FOLDER_ID = "?"
    }
}

/**
 * The tracks whose main artist (see [primaryArtist]) is [name]. [key] is the normalised name;
 * both are null for tracks without an artist.
 */
data class ArtistGroup(val key: String?, val name: String?, val tracks: List<Track>) {
    /** Stable identifier, unique among the groups (real keys are never empty). */
    val id: String get() = key.orEmpty()
}

/**
 * Groups tracks by the folder that holds them, ignoring case (paths are case-insensitive on
 * shared storage). Folders are sorted by name, then path; the root and unknown folders go last.
 * Expects the tracks already filtered (excluded folders) and sorted: each folder keeps that order.
 */
fun List<Track>.groupByFolder(locale: Locale = Locale.getDefault()): List<FolderGroup> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return groupBy { track -> track.relativePath?.let(::normalizeFolder)?.lowercase(Locale.ROOT) }
        .values
        .map { tracks -> FolderGroup(tracks.first().relativePath?.let(::normalizeFolder), tracks) }
        .sortedWith(
            compareBy<FolderGroup> { if (it.name == null) 1 else 0 }
                .thenComparator { a, b -> collator.compare(a.name.orEmpty(), b.name.orEmpty()) }
                .thenComparator { a, b -> collator.compare(a.path.orEmpty(), b.path.orEmpty()) }
                .thenBy { it.path == null },
        )
}

/**
 * Groups tracks by main artist, ignoring case, accents and extra spaces ("black  sherif" =
 * "Black Sherif"); each group shows its most frequent spelling. A collaboration counts only for
 * its main artist: guests are not reliable to extract, and listing a track under each of them
 * would repeat it across groups. Artists are sorted by name; tracks without one go last.
 * Expects the tracks already sorted: each artist keeps that order.
 */
fun List<Track>.groupByArtist(locale: Locale = Locale.getDefault()): List<ArtistGroup> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val main = map { track -> track.artist?.let(::primaryArtist) }
    return indices
        .groupBy { i -> main[i]?.let(::foldForMatching)?.takeIf { it.isNotEmpty() } }
        .map { (key, indices) ->
            val name = key?.let {
                indices.mapNotNull { main[it] }
                    .groupingBy { it }.eachCount()
                    .entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenComparator { a, b -> a.key.compareTo(b.key) })
                    .first().key
            }
            ArtistGroup(key, name, indices.map { this[it] })
        }
        .sortedWith(
            compareBy<ArtistGroup> { if (it.key == null) 1 else 0 }
                .thenComparator { a, b -> collator.compare(a.name.orEmpty(), b.name.orEmpty()) }
                .thenBy { it.key.orEmpty() },
        )
}
