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

@Entity(
    tableName = "songs",
    indices = [Index(value = ["relativePath"], unique = true), Index(value = ["title"]), Index(value = ["artist"])]
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
)

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
    val requestedAt: Long = System.currentTimeMillis()
)

data class QueueRow(
    val id: Long,
    val songId: Long,
    val singer: String,
    val position: Int,
    val title: String,
    val artist: String
)

@Dao
interface KaraokeDao {
    @Query("SELECT * FROM songs WHERE available = 1 ORDER BY title COLLATE NOCASE LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<SongEntity>

    @Query("SELECT COUNT(*) FROM songs WHERE available = 1")
    suspend fun songCount(): Int

    @Query("""
        SELECT songs.* FROM songs
        JOIN songs_fts ON songs.rowid = songs_fts.rowid
        WHERE songs.available = 1 AND songs_fts MATCH :match
        ORDER BY songs.title COLLATE NOCASE
        LIMIT :limit
    """)
    suspend fun searchFts(match: String, limit: Int = 100): List<SongEntity>

    @Query("SELECT * FROM songs WHERE available = 1 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR songNumber LIKE '%' || :query || '%') ORDER BY title COLLATE NOCASE LIMIT :limit")
    suspend fun searchFallback(query: String, limit: Int = 100): List<SongEntity>

    @Query("SELECT * FROM songs WHERE available = 1 AND favorite = 1 ORDER BY title COLLATE NOCASE")
    suspend fun favorites(): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE relativePath IN (:paths)")
    suspend fun songsByPaths(paths: List<String>): List<SongEntity>

    @Query("UPDATE songs SET available = 0")
    suspend fun markAllUnavailable()

    @Query("SELECT * FROM singer_queue ORDER BY position")
    suspend fun queue(): List<QueueEntity>

    @Query("SELECT q.id, q.songId, q.singer, q.position, s.title, s.artist FROM singer_queue q JOIN songs s ON s.id = q.songId ORDER BY q.position")
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
}

@Database(entities = [SongEntity::class, SongSearchEntity::class, QueueEntity::class], version = 1, exportSchema = false)
abstract class KaraokeDatabase : RoomDatabase() {
    abstract fun karaokeDao(): KaraokeDao

    companion object {
        @Volatile private var instance: KaraokeDatabase? = null

        fun get(context: Context): KaraokeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, KaraokeDatabase::class.java, "jktv-library.db")
                .build().also { instance = it }
        }
    }
}
