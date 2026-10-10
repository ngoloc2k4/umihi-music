package ca.ilianokokoro.umihi.music.data.repositories

import android.content.Context
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.models.BlockedArtist
import ca.ilianokokoro.umihi.music.models.BlockedKeyword
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BlockedContentRepository private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: BlockedContentRepository? = null

        fun getInstance(context: Context): BlockedContentRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BlockedContentRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val blockedContentDao = AppDatabase.getInstance(context).blockedContentDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _blockedArtistsSet = MutableStateFlow<Set<String>>(emptySet())
    val blockedArtistsSet = _blockedArtistsSet.asStateFlow()

    private val _blockedKeywordsSet = MutableStateFlow<Set<String>>(emptySet())
    val blockedKeywordsSet = _blockedKeywordsSet.asStateFlow()

    init {
        scope.launch {
            blockedContentDao.getAllBlockedArtistsFlow().collect { list ->
                _blockedArtistsSet.value = list.map { it.name.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            }
        }
        scope.launch {
            blockedContentDao.getAllBlockedKeywordsFlow().collect { list ->
                _blockedKeywordsSet.value = list.map { it.keyword.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            }
        }
    }

    fun getAllBlockedArtistsFlow(): Flow<List<BlockedArtist>> = blockedContentDao.getAllBlockedArtistsFlow()
    fun getAllBlockedKeywordsFlow(): Flow<List<BlockedKeyword>> = blockedContentDao.getAllBlockedKeywordsFlow()

    suspend fun blockArtist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            blockedContentDao.blockArtist(BlockedArtist(name = trimmed))
        }
    }

    suspend fun unblockArtist(name: String) {
        blockedContentDao.unblockArtist(name.trim())
    }

    suspend fun blockKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isNotBlank()) {
            blockedContentDao.blockKeyword(BlockedKeyword(keyword = trimmed))
        }
    }

    suspend fun unblockKeyword(keyword: String) {
        blockedContentDao.unblockKeyword(keyword.trim())
    }

    fun isSongBlocked(song: Song): Boolean {
        return isContentBlocked(artist = song.artist, title = song.title)
    }

    fun isContentBlocked(artist: String, title: String): Boolean {
        val blockedArtists = _blockedArtistsSet.value
        val blockedKeywords = _blockedKeywordsSet.value

        if (blockedArtists.isNotEmpty()) {
            val lowerArtist = artist.lowercase()
            for (blocked in blockedArtists) {
                if (lowerArtist.contains(blocked)) return true
            }
        }

        if (blockedKeywords.isNotEmpty()) {
            val lowerText = "$title $artist".lowercase()
            for (keyword in blockedKeywords) {
                if (lowerText.contains(keyword)) return true
            }
        }

        return false
    }

    fun filterSongs(songs: List<Song>): List<Song> {
        if (_blockedArtistsSet.value.isEmpty() && _blockedKeywordsSet.value.isEmpty()) return songs
        return songs.filterNot { isSongBlocked(it) }
    }
}
