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
import java.util.Base64

@Serializable
private data class KugouSearchResponse(
    val data: KugouSearchData? = null
)

@Serializable
private data class KugouSearchData(
    val info: List<KugouSearchSong>? = null
)

@Serializable
private data class KugouSearchSong(
    val hash: String? = null,
    val songname: String? = null
)

@Serializable
private data class KugouCandidateResponse(
    val candidates: List<KugouCandidate>? = null
)

@Serializable
private data class KugouCandidate(
    val id: String? = null,
    val accesskey: String? = null
)

@Serializable
private data class KugouDownloadResponse(
    val content: String? = null
)

class KugouLyricsProvider(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LyricsProvider {

    override val name = "Kugou"

    override suspend fun getLyrics(query: LyricsQuery): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val hash = searchSongHash(query) ?: return@withContext null
            val candidate = searchCandidate(hash, query.durationMs) ?: return@withContext null
            val id = candidate.id ?: return@withContext null
            val accesskey = candidate.accesskey ?: return@withContext null
            downloadLyric(id, accesskey)
        } catch (e: Exception) {
            LogHelper.printe("KugouLyricsProvider error: ${e.message}")
            null
        }
    }

    private fun searchSongHash(query: LyricsQuery): String? {
        val hash = doSearchHash("${query.cleanedTitle} ${query.cleanedArtist}".trim())
        if (hash != null) return hash
        return doSearchHash(query.cleanedTitle)
    }

    private fun doSearchHash(keyword: String): String? {
        val url = "http://mobilecdn.kugou.com/api/v3/search/song".toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("keyword", keyword)
            .addQueryParameter("page", "1")
            .addQueryParameter("pagesize", "5")
            .build()

        val request = Request.Builder().url(url).build()
        return UmihiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val parsed = json.decodeFromString<KugouSearchResponse>(body)
            parsed.data?.info?.firstOrNull()?.hash
        }
    }

    private fun searchCandidate(hash: String, durationMs: Long): KugouCandidate? {
        val url = "http://krcs.kugou.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("man", "yes")
            .addQueryParameter("client", "mobi")
            .addQueryParameter("keyword", "")
            .addQueryParameter("duration", durationMs.toString())
            .addQueryParameter("hash", hash)
            .build()

        val request = Request.Builder().url(url).build()
        return UmihiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val parsed = json.decodeFromString<KugouCandidateResponse>(body)
            parsed.candidates?.firstOrNull()
        }
    }

    private fun downloadLyric(id: String, accesskey: String): Lyrics? {
        val url = "http://krcs.kugou.com/download".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("client", "mobi")
            .addQueryParameter("fmt", "lrc")
            .addQueryParameter("charset", "utf8")
            .addQueryParameter("id", id)
            .addQueryParameter("accesskey", accesskey)
            .build()

        val request = Request.Builder().url(url).build()
        return UmihiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val parsed = json.decodeFromString<KugouDownloadResponse>(body)
            val base64Content = parsed.content ?: return@use null
            val decodedBytes = Base64.getDecoder().decode(base64Content)
            val rawLrc = String(decodedBytes, Charsets.UTF_8)
            val lines = parseSyncedLyrics(rawLrc)
            if (lines.isNotEmpty()) {
                Lyrics(lines = lines, unsyncedLyrics = rawLrc)
            } else null
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
