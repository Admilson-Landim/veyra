package io.github.admilsonlandim.veyra.data.media

import java.text.Collator
import java.util.Locale

/** Library sort orders. The play order follows the visible order. */
enum class TrackSort { Title, Artist, Album, DateAdded, Duration }

/**
 * Sorts the library. Text keys ignore case and accents (locale collation, PRIMARY strength);
 * unknown values (null) go last; ties fall back to title, then id, so the order is stable.
 * DateAdded is newest first; Duration is shortest first.
 */
fun List<Track>.sortedFor(sort: TrackSort, locale: Locale = Locale.getDefault()): List<Track> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val text = Comparator<String?> { a, b ->
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            else -> collator.compare(a, b)
        }
    }
    val byTitle = Comparator<Track> { a, b -> text.compare(a.title, b.title) }.thenBy { it.id }
    val comparator: Comparator<Track> = when (sort) {
        TrackSort.Title -> byTitle
        TrackSort.Artist -> Comparator<Track> { a, b -> text.compare(a.artist, b.artist) }.then(byTitle)
        TrackSort.Album -> Comparator<Track> { a, b -> text.compare(a.album, b.album) }.then(byTitle)
        TrackSort.DateAdded -> compareByDescending<Track> { it.dateAddedSeconds }.then(byTitle)
        TrackSort.Duration -> compareBy<Track> { it.durationMs }.then(byTitle)
    }
    return sortedWith(comparator)
}

/** Normalises a folder for comparison: no leading slash, exactly one trailing slash. */
fun normalizeFolder(folder: String): String =
    folder.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }

/** True when [relativePath] is [folder] or one of its sub-folders (case-insensitive). */
fun isInFolder(relativePath: String?, folder: String): Boolean {
    if (relativePath == null) return false
    val target = normalizeFolder(folder)
    return target.isNotEmpty() && normalizeFolder(relativePath).startsWith(target, ignoreCase = true)
}

/**
 * Drops tracks in [excludedFolders] and, when [minDurationMs] is set, tracks shorter than it.
 * Tracks without a known folder (before Android 10 without a path) are never excluded by folder.
 */
fun List<Track>.filteredFor(excludedFolders: Set<String>, minDurationMs: Long?): List<Track> =
    filter { track ->
        excludedFolders.none { isInFolder(track.relativePath, it) } &&
            (minDurationMs == null || track.durationMs >= minDurationMs)
    }

/** "/storage/emulated/0/Music/Album/song.mp3" -> "Music/Album/" (for Android 9 and older). */
fun relativeFolderFromPath(path: String): String? {
    @Suppress("DEPRECATION")
    val root = android.os.Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"
    if (!path.startsWith(root)) return null
    return path.removePrefix(root).substringBeforeLast('/', missingDelimiterValue = "")
        .let { if (it.isEmpty()) null else "$it/" }
}
