package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Immutable
@Entity(tableName = "blocked_artists")
data class BlockedArtist(
    @PrimaryKey
    val name: String,
    val blockedAt: Long = System.currentTimeMillis()
)

@Serializable
@Immutable
@Entity(tableName = "blocked_keywords")
data class BlockedKeyword(
    @PrimaryKey
    val keyword: String,
    val blockedAt: Long = System.currentTimeMillis()
)
