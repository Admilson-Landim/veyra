package com.example.veyra.data.media

import java.text.Collator
import java.util.Locale

// MediaStore's placeholder for a missing artist/album tag.
private const val MEDIASTORE_UNKNOWN = "<unknown>"

/** Returns the trimmed tag, or null when it is missing, blank or MediaStore's "<unknown>". */
fun cleanTag(raw: String?): String? {
    val value = raw?.trim()
    return if (value.isNullOrEmpty() || value.equals(MEDIASTORE_UNKNOWN, ignoreCase = true)) null else value
}

/** Uses the title tag, falling back to the file name without its extension. */
fun resolveTitle(title: String?, displayName: String?): String? =
    cleanTag(title) ?: cleanTag(displayName?.substringBeforeLast('.'))

/**
 * Sorts by title ignoring case and accents ("água" sits next to "Agua"), using the
 * locale's collation rules. Tracks without a title go last; ties keep a stable order by id.
 */
fun List<Track>.sortedByTitle(locale: Locale = Locale.getDefault()): List<Track> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return sortedWith(
        compareBy<Track> { it.title == null }
            .thenComparator { a, b -> collator.compare(a.title.orEmpty(), b.title.orEmpty()) }
            .thenBy { it.id },
    )
}
