package com.example.veyra.data.media

/** An image file known to MediaStore: its id, folder ("Music/Album/") and file name. */
data class ImageEntry(val id: Long, val folder: String, val name: String)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/** Folder-level cover names, in order of preference (compared without extension, any case). */
private val FOLDER_COVER_NAMES = listOf("cover", "folder", "front", "album", "albumart")

/** Sub-folder where VidMate stores one thumbnail per download, named "<audio _ID>.png". */
private const val VIDMATE_THUMBNAIL_FOLDER = "lm_thumbnail/"

/**
 * Finds a cover stored as a separate image file, for tracks without embedded artwork.
 * Tried in order:
 * 1. Same folder, same base name as the audio file ("Song.mp3" -> "Song.jpg"), a common
 *    convention of downloaders and taggers.
 * 2. A folder cover in the same folder: cover / folder / front / album / albumart.
 * 3. VidMate: "<folder>/lm_thumbnail/<audio _ID>.png". The id is MediaStore's, so after a
 *    re-index it may point nowhere (then nothing matches and the default cover is shown).
 *
 * Built once from all device images; each lookup is a map access.
 */
class SidecarArtworkIndex(images: List<ImageEntry>) {

    private val byFolderAndBase: Map<Pair<String, String>, ImageEntry> = images
        .filter { it.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
        .associateBy { normalizeFolder(it.folder).lowercase() to it.name.substringBeforeLast('.').lowercase() }

    /** Image id for the track, or null when no separate cover exists. */
    fun find(audioId: Long, folder: String?, displayName: String?): Long? {
        val dir = folder?.let(::normalizeFolder)?.lowercase() ?: return null
        displayName?.substringBeforeLast('.')?.lowercase()?.takeIf { it.isNotEmpty() }?.let { base ->
            byFolderAndBase[dir to base]?.let { return it.id }
        }
        FOLDER_COVER_NAMES.forEach { name -> byFolderAndBase[dir to name]?.let { return it.id } }
        return byFolderAndBase[(dir + VIDMATE_THUMBNAIL_FOLDER) to audioId.toString()]?.id
    }

    val isEmpty: Boolean get() = byFolderAndBase.isEmpty()
}
