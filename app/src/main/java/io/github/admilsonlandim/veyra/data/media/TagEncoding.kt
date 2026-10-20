package io.github.admilsonlandim.veyra.data.media

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Unicode's replacement character: a byte that could not be decoded, lost for good. */
const val REPLACEMENT_CHAR = '�'

/**
 * Undoes "mojibake": UTF-8 text that was read as Windows-1252 / ISO-8859-1, as when a tagger
 * writes UTF-8 bytes into an ID3 frame declared Latin-1 ("JoÃ£o" -> "João", "Donâ€™t" ->
 * "Don’t"). Presentation only: the file is never changed.
 *
 * Conservative by construction, so a correct name is never damaged:
 * 1. The text must show the signature of mojibake: a lead character of a UTF-8 multi-byte
 *    sequence (Â..ô) directly followed by what would be a continuation byte (0x80..0xBF in
 *    Windows-1252). "Ângela", "São Vicente", "Café" and "Funaná" have no such pair.
 * 2. Every character must map back to a single byte (Windows-1252, or Latin-1 for the five
 *    bytes Windows-1252 leaves undefined); anything else (an emoji, "’" typed for real...) means
 *    the text is not a byte-for-byte misreading, and it is left alone.
 * 3. Those bytes must be strictly valid UTF-8, and every character they decode to must be
 *    plausible in a real title (see [isPlausible]): "Ç’est" is correct French, yet its bytes
 *    C7 92 are valid UTF-8 for "ǒ", which no title is likely to hold.
 * Text mis-decoded twice is repaired once more, at most twice in total.
 */
fun repairMojibake(text: String): String {
    var current = text
    repeat(2) {
        val repaired = repairOnce(current) ?: return current
        current = repaired
    }
    return current
}

/** True when the text holds an undecodable byte (U+FFFD): it cannot be repaired. */
fun hasLostCharacters(text: String?): Boolean = text != null && REPLACEMENT_CHAR in text

private fun repairOnce(text: String): String? {
    if (!MOJIBAKE_SIGNATURE.containsMatchIn(text)) return null
    val bytes = ByteArray(text.length)
    for ((index, char) in text.withIndex()) {
        bytes[index] = (WINDOWS_1252_BYTES[char] ?: char.code.takeIf { it < 0x100 } ?: return null).toByte()
    }
    val decoded = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        return null
    }
    return decoded.takeIf { it != text && it.all(::isPlausible) }
}

/**
 * Characters that genuine text decodes to. Excluded: C1 controls, Latin Extended-B, IPA, spacing
 * modifiers and combining marks (U+0180..U+036F), which wrong guesses produce but titles almost
 * never contain, and [REPLACEMENT_CHAR]. Latin-1, Latin Extended-A, punctuation and other
 * scripts (Greek, Cyrillic, CJK, emoji...) are all fine.
 */
private fun isPlausible(char: Char): Boolean =
    char != REPLACEMENT_CHAR && char.code !in 0x80..0x9F && char.code !in 0x180..0x36F

/** The 27 characters Windows-1252 puts in 0x80..0x9F, with their byte. */
private val WINDOWS_1252_BYTES: Map<Char, Int> = mapOf(
    '€' to 0x80, '‚' to 0x82, 'ƒ' to 0x83, '„' to 0x84, '…' to 0x85, '†' to 0x86, '‡' to 0x87,
    'ˆ' to 0x88, '‰' to 0x89, 'Š' to 0x8A, '‹' to 0x8B, 'Œ' to 0x8C, 'Ž' to 0x8E,
    '‘' to 0x91, '’' to 0x92, '“' to 0x93, '”' to 0x94, '•' to 0x95, '–' to 0x96, '—' to 0x97,
    '˜' to 0x98, '™' to 0x99, 'š' to 0x9A, '›' to 0x9B, 'œ' to 0x9C, 'ž' to 0x9E, 'Ÿ' to 0x9F,
)

// A UTF-8 lead byte read as Windows-1252 (Â..ô = 0xC2..0xF4), followed by a continuation byte
// read the same way: 0x80..0xBF, i.e. the Windows-1252 characters above or U+0080..U+00BF.
private val MOJIBAKE_SIGNATURE = Regex(
    "[Â-ô][\u0080-¿" + WINDOWS_1252_BYTES.keys.joinToString("") + "]",
)

/**
 * Title, artist and album as the app shows them: mojibake repaired ([fixEncoding]), then names
 * cleaned from the file name ([cleanNames], see [withCleanNames]).
 *
 * A title or artist with lost characters (U+FFFD) cannot be repaired. When both options are on,
 * it counts as missing for the cleaning, so the file name's version is shown if the file name
 * gives one; otherwise the damaged text stays (better than nothing). The album has no file-name
 * equivalent and always stays.
 */
fun Track.withDisplayNames(fixEncoding: Boolean, cleanNames: Boolean): Track {
    val repaired = if (fixEncoding) withRepairedTags() else this
    if (!cleanNames) return repaired
    val lostTitle = fixEncoding && hasLostCharacters(repaired.title)
    val lostArtist = fixEncoding && hasLostCharacters(repaired.artist)
    if (!lostTitle && !lostArtist) return repaired.withCleanNames()
    val cleaned = repaired.copy(
        title = repaired.title.takeUnless { lostTitle },
        artist = repaired.artist.takeUnless { lostArtist },
    ).withCleanNames()
    return repaired.copy(title = cleaned.title ?: repaired.title, artist = cleaned.artist ?: repaired.artist)
}

private fun Track.withRepairedTags(): Track {
    val newTitle = title?.let(::repairMojibake)
    val newArtist = artist?.let(::repairMojibake)
    val newAlbum = album?.let(::repairMojibake)
    return if (newTitle == title && newArtist == artist && newAlbum == album) {
        this
    } else {
        copy(title = newTitle, artist = newArtist, album = newAlbum)
    }
}
