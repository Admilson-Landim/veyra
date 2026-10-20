package io.github.admilsonlandim.veyra.data.media

/**
 * Library search, ignoring case and accents ("joao" finds "João"). Every word of the query must
 * appear somewhere in the searched text, in any order ("sherif soja" finds "Soja" by Black Sherif).
 * A blank query matches nothing: the caller shows the unfiltered list instead.
 */
class SearchQuery(query: String) {
    private val words = foldForMatching(query).split(' ').filter { it.isNotEmpty() }

    val isBlank: Boolean get() = words.isEmpty()

    fun matches(vararg fields: String?): Boolean {
        if (words.isEmpty()) return false
        val text = fields.filterNotNull().joinToString(" ") { foldForMatching(it) }
        return words.all { it in text }
    }
}

/** Tracks whose title, artist, album or folder name matches; in the given order. */
fun List<Track>.search(query: SearchQuery): List<Track> =
    filter { query.matches(it.title, it.artist, it.album, it.folderName) }

/** Folders whose name or path matches. */
fun List<FolderGroup>.searchFolders(query: SearchQuery): List<FolderGroup> = filter { query.matches(it.name, it.path) }

/** Artists whose name matches. */
fun List<ArtistGroup>.searchArtists(query: SearchQuery): List<ArtistGroup> = filter { query.matches(it.name) }

/** The name of the folder holding the track: "Music/Album/" -> "Album". */
val Track.folderName: String?
    get() = relativePath?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
