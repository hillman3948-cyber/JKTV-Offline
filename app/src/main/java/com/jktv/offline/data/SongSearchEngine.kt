package com.jktv.offline.data

import kotlin.math.max
import kotlin.math.min

data class SmartSearchResult(
    val songs: List<SongEntity>,
    val suggestions: List<String>,
    val duplicateCountRemoved: Int
)

/**
 * High-performance offline search engine optimized for 50,000+ MIDI/KAR songs.
 * Combines SQLite FTS4 prefix matching, numeric songbook code lookup,
 * typo-tolerant fuzzy scoring (edit distance + character bigrams),
 * duplicate detection, and instant autocomplete suggestions.
 */
object SongSearchEngine {

    private val nonAlphaNumRegex = Regex("[^\\p{L}\\p{N}]+")
    private val parenSuffixRegex = Regex("\\s*[(\\[].*?[)\\]]\\s*")

    suspend fun search(
        dao: KaraokeDao,
        rawQuery: String,
        typoTolerance: Boolean = true,
        hideDuplicates: Boolean = false,
        favoritesOnly: Boolean = false,
        limit: Int = 200
    ): SmartSearchResult {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            val base = if (favoritesOnly) dao.favorites() else dao.page(limit = limit, offset = 0)
            return postProcess(base, "", hideDuplicates, favoritesOnly, limit)
        }

        val cleanDigits = query.removePrefix("#").trim()
        if (cleanDigits.all { it.isDigit() } && cleanDigits.isNotEmpty()) {
            val numericId = cleanDigits.toLongOrNull() ?: -1L
            val padded = cleanDigits.padStart(5, '0')
            val numberHits = dao.searchByNumber(cleanDigits, padded, numericId, limit)
            if (numberHits.isNotEmpty()) {
                return postProcess(numberHits, query, hideDuplicates, favoritesOnly, limit)
            }
        }

        val tokens = query.lowercase()
            .split(nonAlphaNumRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val combined = LinkedHashMap<Long, SongEntity>()

        // 1. Fast FTS4 multi-token prefix search
        if (tokens.isNotEmpty()) {
            val ftsMatch = tokens.joinToString(" ") { "$it*" }
            val ftsHits = runCatching { dao.searchFts(ftsMatch, limit) }.getOrDefault(emptyList())
            for (song in ftsHits) {
                combined[song.id] = song
            }
        }

        // 2. Substring fallback if FTS returned few matches
        if (combined.size < 35) {
            val fallbackHits = runCatching { dao.searchFallback(query, limit = 120) }.getOrDefault(emptyList())
            for (song in fallbackHits) {
                combined.putIfAbsent(song.id, song)
            }
        }

        // 3. Typo-tolerant fuzzy search when enabled and exact matches are sparse
        if (typoTolerance && combined.size < 25 && tokens.isNotEmpty()) {
            val firstToken = tokens.first()
            val secondToken = tokens.getOrNull(1).orEmpty()
            val prefix1 = firstToken.take(2).ifEmpty { firstToken.take(1) }
            val prefix2 = secondToken.take(2)
            val candidates = runCatching {
                dao.fuzzyCandidates(prefix1, prefix2, limit = 450)
            }.getOrDefault(emptyList())

            val normQuery = normalizeForMatch(query)
            val scored = candidates
                .asSequence()
                .filter { !combined.containsKey(it.id) }
                .map { song ->
                    val titleSim = similarityScore(normQuery, normalizeForMatch(song.title), tokens)
                    val artistSim = if (song.artist.isNotBlank()) {
                        similarityScore(normQuery, normalizeForMatch(song.artist), tokens) * 0.92
                    } else 0.0
                    val fullSim = similarityScore(normQuery, normalizeForMatch("${song.title} ${song.artist}"), tokens)
                    val best = maxOf(titleSim, artistSim, fullSim)
                    song to best
                }
                .filter { it.second >= 0.52 }
                .sortedByDescending { it.second }
                .take(80)
                .toList()

            for ((song, _) in scored) {
                combined.putIfAbsent(song.id, song)
            }
        }

        val ranked = rankResults(combined.values.toList(), query, tokens)
        return postProcess(ranked, query, hideDuplicates, favoritesOnly, limit)
    }

    private fun rankResults(
        songs: List<SongEntity>,
        rawQuery: String,
        tokens: List<String>
    ): List<SongEntity> {
        if (songs.size <= 1) return songs
        val qLower = rawQuery.lowercase()
        return songs.sortedWith(
            compareByDescending<SongEntity> { song ->
                val tLower = song.title.lowercase()
                val aLower = song.artist.lowercase()
                var score = 0
                if (song.displayNumber == qLower || song.songNumber == qLower) score += 1000
                if (tLower == qLower) score += 800
                else if (tLower.startsWith(qLower)) score += 500
                else if (tLower.contains(qLower)) score += 300
                if (aLower == qLower) score += 450
                else if (aLower.startsWith(qLower)) score += 280
                else if (aLower.contains(qLower)) score += 160
                for (tok in tokens) {
                    if (tLower.contains(tok)) score += 60
                    if (aLower.contains(tok)) score += 40
                }
                if (song.favorite) score += 35
                score + min(song.playCount * 5, 50)
            }.thenBy { it.title.lowercase() }
        )
    }

    private fun postProcess(
        input: List<SongEntity>,
        query: String,
        hideDuplicates: Boolean,
        favoritesOnly: Boolean,
        limit: Int
    ): SmartSearchResult {
        val filteredFav = if (favoritesOnly) input.filter { it.favorite } else input
        var duplicatesRemoved = 0
        val deduplicated = if (hideDuplicates) {
            val seenKeys = HashSet<String>(filteredFav.size)
            val out = ArrayList<SongEntity>(filteredFav.size)
            for (song in filteredFav) {
                val key = duplicateSignature(song)
                if (seenKeys.add(key)) {
                    out.add(song)
                } else {
                    duplicatesRemoved++
                }
            }
            out
        } else {
            filteredFav
        }

        val finalSongs = deduplicated.take(limit)
        val suggestions = buildSuggestions(finalSongs, query)
        return SmartSearchResult(
            songs = finalSongs,
            suggestions = suggestions,
            duplicateCountRemoved = duplicatesRemoved
        )
    }

    fun duplicateSignature(song: SongEntity): String {
        val cleanTitle = song.title
            .replace(parenSuffixRegex, " ")
            .lowercase()
            .replace(nonAlphaNumRegex, "")
        val cleanArtist = song.artist
            .lowercase()
            .replace(nonAlphaNumRegex, "")
            .take(18)
        return "$cleanTitle|$cleanArtist"
    }

    private fun buildSuggestions(songs: List<SongEntity>, query: String): List<String> {
        if (query.isBlank() || songs.isEmpty()) return emptyList()
        val qLower = query.lowercase()
        val set = LinkedHashSet<String>()
        for (song in songs.take(30)) {
            if (song.title.lowercase() != qLower && song.title.isNotBlank()) {
                set.add(song.title)
            }
            if (song.artist.isNotBlank() && song.artist.lowercase() != qLower &&
                song.artist.lowercase().contains(qLower)
            ) {
                set.add(song.artist)
            }
            if (set.size >= 6) break
        }
        return set.toList()
    }

    private fun normalizeForMatch(text: String): String =
        text.lowercase().replace(nonAlphaNumRegex, " ").trim()

    private fun similarityScore(queryNorm: String, targetNorm: String, queryTokens: List<String>): Double {
        if (queryNorm.isEmpty() || targetNorm.isEmpty()) return 0.0
        if (targetNorm.contains(queryNorm)) return 0.96

        val targetTokens = targetNorm.split(' ').filter { it.isNotEmpty() }
        if (targetTokens.isEmpty()) return 0.0

        var tokenScoreSum = 0.0
        for (qTok in queryTokens) {
            var bestTokSim = 0.0
            for (tTok in targetTokens) {
                val dist = levenshtein(qTok, tTok)
                val maxLen = max(qTok.length, tTok.length).coerceAtLeast(1)
                val editSim = 1.0 - (dist.toDouble() / maxLen.toDouble())
                val prefixBonus = if (tTok.startsWith(qTok.take(min(3, qTok.length)))) 0.12 else 0.0
                bestTokSim = max(bestTokSim, (editSim + prefixBonus).coerceAtMost(1.0))
            }
            tokenScoreSum += bestTokSim
        }
        val avgTokenSim = tokenScoreSum / queryTokens.size.coerceAtLeast(1)
        val dice = bigramDice(queryNorm, targetNorm)
        return max(avgTokenSim * 0.80 + dice * 0.20, dice)
    }

    private fun bigramDice(a: String, b: String): Double {
        if (a.length < 2 || b.length < 2) return if (a == b) 1.0 else 0.0
        val setA = HashSet<String>(a.length)
        for (i in 0 until a.length - 1) setA.add(a.substring(i, i + 2))
        var matches = 0
        val totalB = b.length - 1
        for (i in 0 until totalB) {
            if (setA.contains(b.substring(i, i + 2))) matches++
        }
        return (2.0 * matches) / (setA.size + totalB)
    }

    private fun levenshtein(lhs: String, rhs: String): Int {
        if (lhs == rhs) return 0
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length
        val n = lhs.length
        val m = rhs.length
        if (kotlin.math.abs(n - m) > 5) return kotlin.math.abs(n - m)

        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            val c1 = lhs[i - 1]
            for (j in 1..m) {
                val cost = if (c1 == rhs[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }
}
