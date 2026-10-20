package io.github.admilsonlandim.veyra.data.media

import java.text.Normalizer
import java.util.Locale

/** Title and artist as the app shows them. Null means unknown. */
data class DisplayNames(val title: String?, val artist: String?)

/**
 * A file name read as "Artist - Title". [artist] is null when the name has no separator;
 * [unsplit] is the same cleaned name without splitting, for when the split cannot be trusted.
 */
data class ParsedFileName(val artist: String?, val title: String, val unsplit: String)

/**
 * Display names for a track, cleaning the file name only when the tags do not help.
 * Presentation only: files and tags are never changed.
 *
 * - Title missing, or the same as the file name (MediaStore puts the file name there when the
 *   file has no title tag): the title comes from the cleaned file name. Its "Artist - " part is
 *   used too, unless a valid artist tag disagrees with it; then the name is not split at all
 *   ("Soja - Remix.mp3" by a tagged "Black Sherif" stays "Soja - Remix").
 * - Real title tag but no artist: the artist comes from the file name only when the file name's
 *   title matches the tag, i.e. the file name really describes this track.
 * - Otherwise the tags are kept as they are.
 *
 * [title] is expected after [resolveTitle] (so a missing tag already holds the file name) and
 * [artist] after [cleanTag].
 */
fun displayNames(title: String?, artist: String?, displayName: String?): DisplayNames {
    val baseName = displayName?.let(::fileBaseName)?.takeIf { it.isNotBlank() }
        ?: return DisplayNames(title, artist)
    val titleIsFileName = title == null || looseName(title) == looseName(baseName)
    if (!titleIsFileName && artist != null) return DisplayNames(title, artist)
    val parsed = parseFileName(baseName) ?: return DisplayNames(title, artist)
    // Nothing to clean: keep the title as MediaStore has it ("Soja" for "soja.mp3").
    if (title != null && parsed.artist == null && looseName(parsed.title) == looseName(title)) {
        return DisplayNames(title, artist)
    }

    return when {
        titleIsFileName && (artist == null || parsed.artist == null) ->
            DisplayNames(parsed.title, artist ?: parsed.artist)
        titleIsFileName -> if (matchKey(artist!!) == matchKey(parsed.artist!!)) {
            DisplayNames(parsed.title, artist)
        } else {
            DisplayNames(parsed.unsplit, artist)
        }
        // A real title tag, without an artist tag.
        parsed.artist != null && matchKey(cleanWords(title!!)) == matchKey(parsed.title) ->
            DisplayNames(title, parsed.artist)
        else -> DisplayNames(title, null)
    }
}

/** The track with [displayNames] applied to its title and artist. */
fun Track.withCleanNames(): Track {
    val names = displayNames(title, artist, displayName)
    return if (names.title == title && names.artist == artist) this else copy(title = names.title, artist = names.artist)
}

/** Removes a short alphanumeric extension: "song.mp3" -> "song", "my.song.flac" -> "my.song". */
fun fileBaseName(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    if (dot <= 0) return fileName
    val extension = fileName.substring(dot + 1)
    return if (extension.length in 1..5 && extension.all { it.isLetterOrDigit() }) fileName.substring(0, dot) else fileName
}

/**
 * Cleans a file name, already without its extension, into artist and title. Steps, in order:
 * 1. Drop YouTube ids ("[dQw4w9WgXcQ]"), bitrate tags added by downloaders ("(256k)", with
 *    VidMate's "-49" copy counter) and web addresses ("www.site.com", "mp3juices.cc").
 * 2. Underscores become spaces; whitespace is collapsed. When the name used underscores, the
 *    downloader also replaced punctuation with them, so "I_m" becomes "I'm" and, in the artist
 *    part, a triple underscore between names becomes " & " ("Gyakie___Omah_Lay").
 * 3. Remove noise: bracketed groups made only of noise words ("(Official Music Video)", "[HD]",
 *    "(Lyrics)", "(4K)") or a production credit ("(Prod. by X)"), and the same words without
 *    brackets, since downloaders often drop them (see [removeNoiseRuns]). Everything else is
 *    kept: "(Remix)", "(feat. X)", "(Live)", "(Acoustic)", "(Spider-Man: Into the Spider-Verse)".
 * 4. Remove a leading track number when it clearly is one ("01 - ", "01. ", "1. ", "7) ").
 * 5. Split artist and title at the first dash with a space on at least one side ("-", "–",
 *    "—", or a run like "---"). The first one, because artist names rarely contain " - " while
 *    titles often do ("Song - Live at X"). A dash inside a word ("T-Rex", "Spider-Man") never
 *    splits.
 * 6. In the title only, drop a trailing production credit without brackets ("Song prod by X").
 *
 * Returns null when nothing usable is left.
 */
fun parseFileName(baseName: String): ParsedFileName? {
    val usedUnderscores = '_' in baseName
    var name = baseName
        .replace(BRACKETED_TOKEN) { if (looksLikeVideoId(it.groupValues[1])) " " else it.value }
        .replace(BITRATE_TAG, " ")
        .replace(WEB_ADDRESS, " ")
    if (usedUnderscores) name = name.replace(TRIPLE_UNDERSCORE, " $AMPERSAND_MARK ")
    name = name.replace('_', ' ').collapseSpaces()
    if (usedUnderscores) name = restoreApostrophes(name)

    val withoutGroups = removeNoiseGroups(name)
    val cleaned = tidy(stripTrackNumber(withoutGroups).ifEmpty { withoutGroups })
    if (cleaned.isEmpty()) return null
    val unsplit = cleanTitle(cleaned.replace(AMPERSAND_MARK, " "))

    val separator = SEPARATOR.find(cleaned)
    if (separator != null) {
        val artist = tidy(cleaned.substring(0, separator.range.first).replace(AMPERSAND_MARK, " & "))
        val rawTitle = cleaned.substring(separator.range.last + 1).replace(AMPERSAND_MARK, " ").collapseSpaces()
        // "Name - Official Video": what follows the dash is noise, not a title.
        val titleIsNoise = ' ' in rawTitle && isNoisePhrase(rawTitle)
        val title = cleanTitle(rawTitle)
        if (artist.isNotEmpty() && title.isNotEmpty() && !titleIsNoise) return ParsedFileName(artist, title, unsplit)
    }
    return unsplit.takeIf { it.isNotEmpty() }?.let { ParsedFileName(null, it, it) }
}

/**
 * The main artist of a credit, for grouping: "Joeboy ft. Qing Madi" -> "Joeboy",
 * "Black Sherif, Seyi Vibez" -> "Black Sherif", "Danni Gato x Loreta KBA" -> "Danni Gato".
 * "&" does not split: it is usually part of a duo's name ("Simon & Garfunkel").
 */
fun primaryArtist(artist: String): String =
    artist.split(COLLABORATION, limit = 2).first().trim().ifEmpty { artist.trim() }

/** Lowercase, without accents and with single spaces: "  Hélio   BATALHA" -> "helio batalha". */
fun foldForMatching(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .collapseSpaces()

/** The same name for MediaStore's purposes: case, underscores and spacing aside. */
private fun looseName(text: String): String = text.replace('_', ' ').collapseSpaces().lowercase(Locale.ROOT)

/** Compares names ignoring case, accents, spacing and punctuation. */
private fun matchKey(text: String): String = foldForMatching(text).filter { it.isLetterOrDigit() }

private fun String.collapseSpaces(): String = trim().replace(WHITESPACE, " ")

/** A title tag cleaned the same way as a file name's title, for comparing the two. */
private fun cleanWords(text: String): String = cleanTitle(removeNoiseGroups(text))

/** Step 3, bracketed groups: on the whole name, since brackets make their meaning clear. */
private fun removeNoiseGroups(text: String): String =
    BRACKETED.replace(text) { match ->
        val content = match.value.substring(1, match.value.length - 1).trim()
        val noise = content.isEmpty() || isNoisePhrase(content) || PRODUCTION_CREDIT_START.containsMatchIn(content)
        if (noise) " " else match.value
    }.collapseSpaces()

/**
 * Step 3 without brackets and step 6, on the title alone, so the whole title is never taken for
 * noise ("Artist - Audio"). Noise words go after the credit, which may hide them ("Song Official
 * prod X" -> "Song").
 */
private fun cleanTitle(title: String): String {
    val withoutCredit = title.replace(TRAILING_PRODUCTION_CREDIT, "").trim()
    return tidy(removeNoiseRuns(withoutCredit).ifEmpty { title })
}

/**
 * Drops noise words outside brackets: a run of two or more that has a strong noise word
 * ("Official Video", "Video Oficial 2024", "Official Music Video"), and one of the words that
 * are noise on their own at the very end ("Dreamer Visualizer", "Like I Do Audio"). A lone
 * "Video" or "Music" is kept, and a run is never removed when it is the whole name.
 */
private fun removeNoiseRuns(text: String): String {
    val words = text.split(' ').filter { it.isNotEmpty() }
    if (words.size < 2) return text
    val keep = BooleanArray(words.size) { true }
    var start = 0
    while (start < words.size) {
        if (!isNoiseWord(words[start])) {
            start++
            continue
        }
        var end = start
        while (end + 1 < words.size && isNoiseWord(words[end + 1])) end++
        val run = words.subList(start, end + 1)
        val atEnd = end == words.lastIndex
        val removable = !(start == 0 && atEnd) && if (run.size >= 2) {
            run.any { noiseKey(it) in STRONG_NOISE }
        } else {
            atEnd && noiseKey(run[0]) in NOISE_ALONE_AT_END
        }
        if (removable) for (i in start..end) keep[i] = false
        start = end + 1
    }
    return words.filterIndexed { i, _ -> keep[i] }.joinToString(" ")
}

/** Inside brackets the context is clear: any strong noise word counts, even alone ("(Video)"). */
private fun isNoisePhrase(text: String): Boolean {
    val words = text.split(WHITESPACE).filter { it.isNotEmpty() }
    return words.isNotEmpty() && words.all(::isNoiseWord) && words.any { noiseKey(it) in STRONG_NOISE }
}

private fun noiseKey(word: String): String = word.trim(',', '.', ':', ';', '!', '|').lowercase(Locale.ROOT)

private fun isNoiseWord(word: String): Boolean =
    noiseKey(word).let { it in STRONG_NOISE || it in WEAK_NOISE || YEAR.matches(it) }

/**
 * A bracketed token that is a video id rather than words: 11 characters (YouTube's length)
 * with a digit or a lower-to-upper case change, or 6 to 10 letters and digits with at least
 * two of each that is not a measure like "320kbps" or "1080p".
 */
private fun looksLikeVideoId(token: String): Boolean {
    val digits = token.count { it.isDigit() }
    val letters = token.count { it.isLetter() }
    return when (token.length) {
        11 -> digits > 0 || token.zipWithNext().any { (a, b) -> a.isLowerCase() && b.isUpperCase() }
        in 6..10 -> token.all { it.isLetterOrDigit() } && digits >= 2 && letters >= 2 && !MEASURE.matches(token)
        else -> false
    }
}

/** "01 - Song", "01. Song", "1. Song", "07 Song", "7) Song" -> "Song". Never 3-digit numbers (band "112"). */
private fun stripTrackNumber(text: String): String = text.replaceFirst(TRACK_NUMBER, "").trim()

/** Only English contractions whose apostrophe a downloader turned into an underscore. */
private fun restoreApostrophes(text: String): String = CONTRACTIONS.fold(text) { acc, regex -> regex.replace(acc, "$1'$2") }

/** Trims leftover separators and punctuation at the ends, and empty brackets. */
private fun tidy(text: String): String =
    text.replace(EMPTY_BRACKETS, " ").collapseSpaces().trim('-', '–', '—', '&', '|', ',', ';', ':', '+', ' ', '\u0001').trim()

// Placeholder word for "___" until we know whether it is in the artist part.
private const val AMPERSAND_MARK = "\u0001"

private val WHITESPACE = Regex("""\s+""")
private val COMBINING_MARKS = Regex("""\p{Mn}+""")
private val YEAR = Regex("""(19|20)\d{2}""")
private val MEASURE = Regex("""\d+[a-zA-Z]+""")
private val BRACKETED_TOKEN = Regex("""[\[(]([A-Za-z0-9_-]{6,11})[\])]""")
private val BITRATE_TAG = Regex("""[(\[]\s*\d{2,4}\s?k(?:bps)?\s*[)\]](?:-\d{1,3})?|\b\d{2,4}\s?kbps\b""", RegexOption.IGNORE_CASE)
private val WEB_ADDRESS = Regex(
    """\b(?:www\.[\w-]+(?:\.[\w-]+)+|[\w-]{3,}\.(?:com|net|org|cc|io|me|to|info|biz|ws|xyz|ru|top|site|fm|tv))\b""",
    RegexOption.IGNORE_CASE,
)
private val TRIPLE_UNDERSCORE = Regex("""(?<=[\p{L}\p{N}])___(?=[\p{L}\p{N}])""")
private val BRACKETED = Regex("""\([^()]*\)|\[[^\[\]]*\]|\{[^\{\}]*\}""")
private val EMPTY_BRACKETS = Regex("""\(\s*\)|\[\s*\]|\{\s*\}""")
private val PRODUCTION_CREDIT_START = Regex("""^(?:prod(?:uced)?(?!\p{L})|beat\s+by\b)""", RegexOption.IGNORE_CASE)
private val TRAILING_PRODUCTION_CREDIT = Regex("""\s(?:prod(?:uced)?(?!\p{L})\.?(?:\s*by\.?)?|beat\s+by)\s+\S.*$""", RegexOption.IGNORE_CASE)
private val TRACK_NUMBER = Regex("""^(?:0\d(?:\s*[-–—.)]\s*|\s+)|\d{1,2}[.)]\s+|\d{1,2}\s+[-–—]\s+)(?=\S)""")
private val SEPARATOR = Regex("""\s*[-–—]+\s+|\s+[-–—]+\s*""")
private val COLLABORATION = Regex("""\s+(?:feat\.?|ft\.?|featuring|part\.)\s+|\s*,\s+|\s+x\s+|\s+/\s+|\s*;\s*""", RegexOption.IGNORE_CASE)
private val CONTRACTIONS = listOf(
    Regex("""\b(I) (m|ll|ve|d)\b"""),
    Regex("""\b(don|can|won|isn|aren|wasn|weren|doesn|didn|couldn|shouldn|wouldn|ain) (t)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(it|that|let|he|she|what|there|here|who) (s)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(you|we|they) (re|ve|ll|d)\b""", RegexOption.IGNORE_CASE),
)

/** Words that describe the upload, not the song. */
private val STRONG_NOISE = setOf(
    "official", "oficial", "video", "vídeo", "videoclip", "videoclipe", "audio", "áudio", "lyrics",
    "visualizer", "visualiser", "hd", "hq", "4k", "8k", "1080p", "720p",
)

/** Strong noise words that are noise even alone at the end of a name. "Video" is not: "Home Video". */
private val NOISE_ALONE_AT_END = setOf(
    "official", "oficial", "audio", "áudio", "lyrics", "visualizer", "visualiser", "hd", "hq", "4k", "8k", "1080p", "720p",
)

/** Noise only next to a strong noise word: "Official Music Video", "Lyric Video", "Vídeo letra 2020". */
private val WEAK_NOISE = setOf("music", "lyric", "letra", "clip", "clipe", "full")
