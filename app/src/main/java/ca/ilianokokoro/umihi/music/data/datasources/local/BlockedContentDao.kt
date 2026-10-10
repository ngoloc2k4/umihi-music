package ca.ilianokokoro.umihi.music.data.datasources.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ca.ilianokokoro.umihi.music.models.BlockedArtist
import ca.ilianokokoro.umihi.music.models.BlockedKeyword
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockArtist(artist: BlockedArtist)

    @Query("DELETE FROM blocked_artists WHERE LOWER(name) = LOWER(:name)")
    suspend fun unblockArtist(name: String)

    @Query("SELECT * FROM blocked_artists ORDER BY blockedAt DESC")
    fun getAllBlockedArtistsFlow(): Flow<List<BlockedArtist>>

    @Query("SELECT * FROM blocked_artists ORDER BY blockedAt DESC")
    suspend fun getAllBlockedArtists(): List<BlockedArtist>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_artists WHERE LOWER(name) = LOWER(:name))")
    suspend fun isArtistBlocked(name: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockKeyword(keyword: BlockedKeyword)

    @Query("DELETE FROM blocked_keywords WHERE LOWER(keyword) = LOWER(:keyword)")
    suspend fun unblockKeyword(keyword: String)

    @Query("SELECT * FROM blocked_keywords ORDER BY blockedAt DESC")
    fun getAllBlockedKeywordsFlow(): Flow<List<BlockedKeyword>>

    @Query("SELECT * FROM blocked_keywords ORDER BY blockedAt DESC")
    suspend fun getAllBlockedKeywords(): List<BlockedKeyword>
}
