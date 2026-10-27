package com.jktv.offline.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SongIndexer(context: Context) {
    private val root = File(context.filesDir, "karaoke/library/songs")
    private val dao = KaraokeDatabase.get(context).karaokeDao()

    suspend fun index(packageVersion: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int = withContext(Dispatchers.IO) {
        val found = root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("mid", "midi", "kar") }
            .sortedBy { it.name.lowercase() }
            .toList()
        val batch = ArrayList<SongEntity>(500)
        var indexed = 0
        dao.markAllUnavailable()
        suspend fun saveBatch() {
            if (batch.isEmpty()) return
            val existing = dao.songsByPaths(batch.map { it.relativePath }).associateBy { it.relativePath }
            val updated = batch.map { scanned ->
                existing[scanned.relativePath]?.copy(
                    songNumber = scanned.songNumber.ifBlank { existing[scanned.relativePath]?.songNumber.orEmpty() },
                    title = scanned.title,
                    artist = scanned.artist,
                    relativePath = scanned.relativePath,
                    packageVersion = scanned.packageVersion,
                    available = true
                ) ?: scanned
            }
            dao.putSongs(updated)
            indexed += updated.size
            batch.clear()
        }
        val leadingNumRegex = Regex("^\\s*(\\d+)\\s*[-._ ]+\\s*")
        val splitRegex = Regex("\\s+[-–—]\\s+")
        found.forEachIndexed { index, file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val stem = file.nameWithoutExtension
            val parsedName = stem.replace(leadingNumRegex, "").replace('_', ' ').trim()
            val parts = parsedName.split(splitRegex).map(String::trim).filter(String::isNotEmpty)
            val title = parts.firstOrNull().orEmpty().ifBlank { parsedName }
            val artist = parts.drop(1).joinToString(" - ")
            if (title.isNotBlank()) {
                val explicitNumber = leadingNumRegex.find(stem)?.groupValues?.getOrNull(1).orEmpty()
                val assignedNumber = explicitNumber.ifBlank {
                    (index + 1).toString().padStart(5, '0')
                }
                batch += SongEntity(
                    songNumber = assignedNumber,
                    title = title.take(255),
                    artist = artist.take(190),
                    relativePath = relative,
                    packageVersion = packageVersion
                )
            }
            if (batch.size == 500 || index == found.lastIndex) {
                saveBatch()
                onProgress(index + 1, found.size)
            }
        }
        if (found.isEmpty()) dao.clearQueue()
        indexed
    }
}
