package ca.ilianokokoro.umihi.music.models.lyrics

import ca.ilianokokoro.umihi.music.models.Song

data class LyricsQuery(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val album: String = "",
) {
    val cleanedTitle: String
        get() {
            var cleaned = title
                .replace(Regex("""(?i)\(official\s*(music\s*)?video\)"""), "")
                .replace(Regex("""(?i)\[official\s*(music\s*)?video\]"""), "")
                .replace(Regex("""(?i)\(official\s*audio\)"""), "")
                .replace(Regex("""(?i)\[official\s*audio\]"""), "")
                .replace(Regex("""(?i)\(official\s*hd\)"""), "")
                .replace(Regex("""(?i)\[official\s*hd\]"""), "")
                .replace(Regex("""(?i)\(lyrics(\s*video)?\)"""), "")
                .replace(Regex("""(?i)\[lyrics(\s*video)?\]"""), "")
                .replace(Regex("""(?i)\(visualizer\)"""), "")
                .replace(Regex("""(?i)\[visualizer\]"""), "")
                .replace(Regex("""(?i)\(audio\)"""), "")
                .replace(Regex("""(?i)\[audio\]"""), "")
                .replace(Regex("""(?i)\(mv\)"""), "")
                .replace(Regex("""(?i)\[mv\]"""), "")
                .replace(Regex("""(?i)\(live\)"""), "")
                .replace(Regex("""(?i)\[live\]"""), "")
                .replace(Regex("""(?i)\b(ft\.|feat\.)\s+.*$"""), "")
                .replace(Regex("""\s*\|\s*.*$"""), "")
                .replace(Regex("""\s*-\s*.*$"""), "")
                .trim()
            return if (cleaned.isBlank()) title else cleaned
        }

    val cleanedArtist: String
        get() {
            var cleaned = artist
                .replace(Regex("""(?i)\b(ft\.|feat\.)\s+.*$"""), "")
                .replace(Regex("""\s*,\s*.*$"""), "")
                .replace(Regex("""\s*&\s*.*$"""), "")
                .replace(Regex("""\s*x\s+.*$"""), "")
                .trim()
            return if (cleaned.isBlank()) artist else cleaned
        }
    companion object {
        fun fromSong(song: Song): LyricsQuery {
            val durationSeconds = song.durationSeconds ?: 0
            return LyricsQuery(
                title = song.title,
                artist = song.artists,
                durationMs = durationSeconds * 1_000L
            )
        }
    }
}