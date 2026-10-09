package ca.ilianokokoro.umihi.music.models.lyrics.providers

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper
import ca.ilianokokoro.umihi.music.models.lyrics.Lyrics
import ca.ilianokokoro.umihi.music.models.lyrics.LyricsProvider
import ca.ilianokokoro.umihi.music.models.lyrics.LyricsQuery
import ca.ilianokokoro.umihi.music.models.lyrics.SyncedLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

@Serializable
private data class NeteaseSearchResponse(
    val result: NeteaseSearchResult? = null
)

@Serializable
private data class NeteaseSearchResult(
    val songs: List<NeteaseSong>? = null
)

@Serializable
private data class NeteaseSong(
    val id: Long,
    val name: String,
    val dt: Long? = null
)

@Serializable
private data class NeteaseLyricResponse(
    val lrc: NeteaseLyricData? = null,
    val tlyric: NeteaseLyricData? = null
)

@Serializable
private data class NeteaseLyricData(
    val lyric: String? = null
)

class NeteaseLyricsProvider(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LyricsProvider {

    override val name = "Netease"

    override suspend fun getLyrics(query: LyricsQuery): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val songId = searchSong(query) ?: return@withContext null
            fetchLyrics(songId)
        } catch (e: Exception) {
            LogHelper.printe("NeteaseLyricsProvider error: ${e.message}")
            null
        }
    }

    private fun searchSong(query: LyricsQuery): Long? {
        val songId = doSearch("${query.cleanedTitle} ${query.cleanedArtist}".trim())
        if (songId != null) return songId
        return doSearch(query.cleanedTitle)
    }

    private fun doSearch(keyword: String): Long? {
        val url = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
            .addQueryParameter("s", keyword)
            .addQueryParameter("type", "1")
            .addQueryParameter("offset", "0")
            .addQueryParameter("total", "true")
            .addQueryParameter("limit", "5")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Referer", "https://music.163.com")
            .build()

        return UmihiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val parsed = json.decodeFromString<NeteaseSearchResponse>(body)
            val songs = parsed.result?.songs ?: return@use null
            songs.firstOrNull()?.id
        }
    }

    private fun fetchLyrics(songId: Long): Lyrics? {
        val url = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
            .addQueryParameter("id", songId.toString())
            .addQueryParameter("lv", "1")
            .addQueryParameter("kv", "1")
            .addQueryParameter("tv", "-1")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Referer", "https://music.163.com")
            .build()

        return UmihiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val parsed = json.decodeFromString<NeteaseLyricResponse>(body)
            val lyricText = parsed.lrc?.lyric?.takeIf { it.isNotBlank() } ?: return@use null
            val lines = parseSyncedLyrics(lyricText)
            if (lines.isNotEmpty()) {
                Lyrics(lines = lines, unsyncedLyrics = lyricText)
            } else {
                null
            }
        }
    }

    private fun parseSyncedLyrics(raw: String): List<SyncedLine> {
        return raw.lineSequence()
            .mapNotNull { LRC_LINE_REGEX.matchEntire(it.trim()) }
            .map { match ->
                val (min, sec, frac, text) = match.destructured
                val fracMs = if (frac.length == 2) frac.toLong() * 10 else frac.toLong()
                SyncedLine(
                    timeMs = min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs,
                    text = text.trim()
                )
            }
            .filter { it.text.isNotBlank() }
            .toList()
    }

    companion object {
        private val LRC_LINE_REGEX = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})](.*)$""")
    }
}
