package com.jktv.offline.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["relativePath"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"])
    ]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songNumber: String = "",
    val title: String,
    val artist: String = "",
    val relativePath: String,
    val durationMs: Long = 0,
    val hasLyrics: Boolean = false,
    val favorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val packageVersion: String = "",
    val available: Boolean = true
) {
    val displayNumber: String
        get() = songNumber.ifBlank { id.coerceAtLeast(1L).toString().padStart(5, '0') }
}

@Fts4(contentEntity = SongEntity::class)
@Entity(tableName = "songs_fts")
data class SongSearchEntity(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String
)

@Entity(tableName = "singer_queue", indices = [Index(value = ["position"])])
data class QueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val singer: String,
    val position: Int,
    val requestedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") val secondSinger: String = "",
    @ColumnInfo(defaultValue = "SOLO") val mode: String = "SOLO",
    @ColumnInfo(defaultValue = "0") val keyOffset: Int = 0,
    @ColumnInfo(defaultValue = "100") val tempoPercent: Int = 100
)

data class QueueRow(
    val id: Long,
    val songId: Long,
    val singer: String,
    val position: Int,
    val title: String,
    val artist: String,
    val songNumber: String = "",
    val secondSinger: String = "",
    val mode: String = "SOLO",
    val keyOffset: Int = 0,
    val tempoPercent: Int = 100
) {
    val displayNumber: String
        get() = songNumber.ifBlank { songId.coerceAtLeast(1L).toString().padStart(5, '0') }
}

@Entity(tableName = "playlists", indices = [Index(value = ["name"], unique = true)])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["songId"])]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val songCount: Int
)

@Entity(tableName = "singers", indices = [Index(value = ["name"], unique = true)])
data class SingerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val active: Boolean = true,
    val songsSung: Int = 0,
    val bestScore: Int = 0,
    val lastScore: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class ArtistCountRow(
    val artist: String,
    val songCount: Int
)

@Dao
interface KaraokeDao {
    @Query("SELECT * FROM songs WHERE available = 1 ORDER BY title COLLATE NOCASE LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun songById(id: Long): SongEntity?

    @Query("SELECT COUNT(*) FROM songs WHERE available = 1")
    suspend fun songCount(): Int

    @Query("""
        SELECT songs.* FROM songs
        JOIN songs_fts ON songs.rowid = songs_fts.rowid
        WHERE songs.available = 1 AND songs_fts MATCH :match
        ORDER BY songs.playCount DESC, songs.favorite DESC, songs.title COLLATE NOCASE
        LIMIT :limit
    """)
    suspend fun searchFts(match: String, limit: Int = 180): List<SongEntity>

    @Query("""
        SELECT * FROM songs
        WHERE available = 1
          AND (
            songNumber = :exactCode
            OR songNumber = :paddedCode
            OR id = :numericId
            OR songNumber LIKE :exactCode || '%'
          )
        ORDER BY
          CASE
            WHEN songNumber = :exactCode OR songNumber = :paddedCode OR id = :numericId THEN 0
            ELSE 1
          END,
          id ASC
        LIMIT :limit
    """)
    suspend fun searchByNumber(
        exactCode: String,
        paddedCode: String,
        numericId: Long,
        limit: Int = 60
    ): List<SongEntity>

    @Query("""
        SELECT * FROM songs
        WHERE available = 1
          AND (
            title LIKE '%' || :query || '%'
            OR artist LIKE '%' || :query || '%'
            OR songNumber LIKE '%' || :query || '%'
          )
        ORDER BY
          CASE
            WHEN title LIKE :query || '%' THEN 0
            WHEN artist LIKE :query || '%' THEN 1
            ELSE 2
          END,
          playCount DESC,
          title COLLATE NOCASE
        LIMIT :limit
    """)
    suspend fun searchFallback(query: String, limit: Int = 150): List<SongEntity>

    @Query("""
        SELECT * FROM songs
        WHERE available = 1
          AND (
            title LIKE :prefix1 || '%'
            OR title LIKE '% ' || :prefix1 || '%'
            OR artist LIKE :prefix1 || '%'
            OR (:prefix2 != '' AND (title LIKE :prefix2 || '%' OR artist LIKE :prefix2 || '%'))
          )
        LIMIT :limit
    """)
    suspend fun fuzzyCandidates(prefix1: String, prefix2: String, limit: Int = 450): List<SongEntity>

    @Query("SELECT * FROM songs WHERE available = 1 AND title LIKE :letter || '%' ORDER BY title COLLATE NOCASE LIMIT :limit OFFSET :offset")
    suspend fun songsByLetter(letter: String, limit: Int = 200, offset: Int = 0): List<SongEntity>

    @Query("SELECT * FROM songs WHERE available = 1 AND favorite = 1 ORDER BY title COLLATE NOCASE")
    suspend fun favorites(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE available = 1 AND lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun recentlyPlayed(limit: Int = 60): List<SongEntity>

    @Query("SELECT * FROM songs WHERE available = 1 AND playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    suspend fun mostPlayed(limit: Int = 60): List<SongEntity>

    @Query("""
        SELECT artist, COUNT(*) AS songCount
        FROM songs
        WHERE available = 1 AND artist != ''
        GROUP BY artist
        ORDER BY songCount DESC, artist COLLATE NOCASE
        LIMIT :limit
    """)
    suspend fun topArtists(limit: Int = 120): List<ArtistCountRow>

    @Query("SELECT * FROM songs WHERE available = 1 AND artist = :artist ORDER BY title COLLATE NOCASE LIMIT :limit")
    suspend fun songsByArtist(artist: String, limit: Int = 250): List<SongEntity>

    @Query("SELECT * FROM songs WHERE available = 1 ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomSongs(limit: Int = 10): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE relativePath IN (:paths)")
    suspend fun songsByPaths(paths: List<String>): List<SongEntity>

    @Query("UPDATE songs SET available = 0")
    suspend fun markAllUnavailable()

    @Query("SELECT * FROM singer_queue ORDER BY position")
    suspend fun queue(): List<QueueEntity>

    @Query("""
        SELECT q.id, q.songId, q.singer, q.position, s.title, s.artist, s.songNumber,
               q.secondSinger, q.mode, q.keyOffset, q.tempoPercent
        FROM singer_queue q
        JOIN songs s ON s.id = q.songId
        ORDER BY q.position
    """)
    suspend fun queueRows(): List<QueueRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addQueueItem(item: QueueEntity)

    @Query("DELETE FROM singer_queue WHERE id = :id")
    suspend fun removeQueueItem(id: Long)

    @Query("UPDATE singer_queue SET position = (SELECT COUNT(*) FROM singer_queue AS earlier WHERE earlier.position < singer_queue.position)")
    suspend fun reindexQueue()

    @Query("UPDATE singer_queue SET position = :position WHERE id = :id")
    suspend fun setQueuePosition(id: Long, position: Int)

    @Query("DELETE FROM singer_queue")
    suspend fun clearQueue()

    @Query("UPDATE songs SET favorite = :favorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, favorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :songId")
    suspend fun markPlayed(songId: Long, playedAt: Long = System.currentTimeMillis())

    // Playlists
    @Query("""
        SELECT p.id, p.name, p.description, p.createdAt, COUNT(ps.songId) AS songCount
        FROM playlists p
        LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.createdAt DESC
    """)
    suspend fun playlistSummaries(): List<PlaylistSummaryRow>

    @Query("""
        SELECT s.* FROM songs s
        JOIN playlist_songs ps ON ps.songId = s.id
        WHERE ps.playlistId = :playlistId AND s.available = 1
        ORDER BY ps.addedAt DESC
    """)
    suspend fun playlistSongs(playlistId: Long): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    // Singers & Rotation
    @Query("SELECT * FROM singers ORDER BY active DESC, id ASC")
    suspend fun singers(): List<SingerEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSinger(singer: SingerEntity): Long

    @Query("UPDATE singers SET active = :active WHERE id = :id")
    suspend fun setSingerActive(id: Long, active: Boolean)

    @Query("UPDATE singers SET songsSung = songsSung + 1, lastScore = :score, bestScore = CASE WHEN :score > bestScore THEN :score ELSE bestScore END WHERE name = :name COLLATE NOCASE")
    suspend fun recordSingerScore(name: String, score: Int)

    @Query("DELETE FROM singers WHERE id = :id")
    suspend fun deleteSinger(id: Long)
}

@Database(
    entities = [
        SongEntity::class,
        SongSearchEntity::class,
        QueueEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        SingerEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KaraokeDatabase : RoomDatabase() {
    abstract fun karaokeDao(): KaraokeDao

    companion object {
        @Volatile private var instance: KaraokeDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `singer_queue` ADD COLUMN `secondSinger` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `singer_queue` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'SOLO'")
                db.execSQL("ALTER TABLE `singer_queue` ADD COLUMN `keyOffset` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `singer_queue` ADD COLUMN `tempoPercent` INTEGER NOT NULL DEFAULT 100")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_name` ON `playlists` (`name`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_songs` (
                        `playlistId` INTEGER NOT NULL,
                        `songId` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `songId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` ON `playlist_songs` (`playlistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_songId` ON `playlist_songs` (`songId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `singers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `active` INTEGER NOT NULL,
                        `songsSung` INTEGER NOT NULL,
                        `bestScore` INTEGER NOT NULL,
                        `lastScore` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_singers_name` ON `singers` (`name`)")
            }
        }

        fun get(context: Context): KaraokeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                KaraokeDatabase::class.java,
                "jktv-library.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
                .also { instance = it }
        }
    }
}
