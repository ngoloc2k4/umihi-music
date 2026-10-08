package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.AddToPlaylistOption
import ca.ilianokokoro.umihi.music.models.HomeSection
import ca.ilianokokoro.umihi.music.models.HomeSectionItem
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import ca.ilianokokoro.umihi.music.models.enums.Privacy

class PlaylistDataSource {
    suspend fun retrieveHomeSections(settings: UmihiSettings): List<HomeSection> {
        return YoutubeDataExtractor.extractHomeSections(
            YoutubeApiClient.browseHome(settings),
            settings
        )
    }

    suspend fun retrieveChartsSections(settings: UmihiSettings): List<HomeSection> {
        return try {
            val result = YoutubeDataExtractor.extractHomeSections(
                YoutubeApiClient.browse(Constants.YoutubeApi.Browse.CHARTS_BROWSE_ID, settings),
                settings
            )
            if (result.isNotEmpty()) result else retrieveHomeSections(settings)
        } catch (_: Exception) {
            retrieveHomeSections(settings)
        }
    }

    suspend fun retrieveMoodSections(query: String, title: String, settings: UmihiSettings): List<HomeSection> {
        return try {
            val songs = YoutubeDataExtractor.extractSearchResults(
                YoutubeApiClient.search(
                    query = query,
                    filterParams = Constants.YoutubeApi.Search.FILTER_SONGS,
                    settings = settings
                )
            )
            if (songs.isNotEmpty()) {
                listOf(
                    HomeSection(
                        id = query,
                        title = title,
                        subtitle = null,
                        items = songs.map { HomeSectionItem.SongItem(it) }
                    )
                )
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun retrieveAll(settings: UmihiSettings): List<PlaylistInfo> {
        return YoutubeDataExtractor.extractPlaylists(
            YoutubeApiClient.browse(
                Constants.YoutubeApi.Browse.PLAYLIST_BROWSE_ID,
                settings,
                //  fields = Constants.YoutubeApi.Browse.Fields.PLAYLISTS,
            ), settings
        )
    }

    suspend fun retrieveOne(
        playlist: Playlist,
        settings: UmihiSettings,
        onProgress: (Int) -> Unit = {}
    ): Playlist {
        return playlist.copy(
            songs = YoutubeDataExtractor.extractSongList(
                YoutubeApiClient.browse(
                    playlist.info.id,
                    settings,
                    //   fields = Constants.YoutubeApi.Browse.Fields.SONGS,
                ), settings, onProgress
            )
        )
    }

    suspend fun create(
        title: String,
        description: String,
        privacy: Privacy,
        settings: UmihiSettings
    ): PlaylistInfo? {

        return YoutubeDataExtractor.extractCreatedPlaylist(
            YoutubeApiClient.createPlaylist(
                title,
                description,
                privacy,
                settings = settings
            )
        )
    }

    suspend fun delete(
        playlist: PlaylistInfo,
        settings: UmihiSettings
    ) {
        YoutubeApiClient.deletePlaylist(
            playlist,
            settings = settings
        )
    }

    suspend fun removeFromLibrary(
        playlist: PlaylistInfo,
        settings: UmihiSettings
    ) {
        YoutubeApiClient.removePlaylistFromLibrary(
            playlist,
            settings = settings
        )
    }

    suspend fun retrieveAddToPlaylistOptions(
        videoId: String,
        settings: UmihiSettings
    ): List<AddToPlaylistOption> {
        return YoutubeDataExtractor.extractAddToPlaylistOptions(
            YoutubeApiClient.getAddToPlaylists(
                videoId = videoId,
                settings = settings
            )
        )
    }

    suspend fun findSetVideoId(
        playlistId: String,
        videoId: String,
        settings: UmihiSettings
    ): String {
        val browseId = "VL${playlistId.removePrefix("VL")}"
        val playlist = retrieveOne(Playlist(PlaylistInfo(id = browseId)), settings)
        return playlist.songs
            .firstOrNull { it.youtubeId == videoId }
            ?.setVideoId
            ?: throw IllegalStateException("Track not found in playlist $browseId")
    }

    suspend fun edit(
        playlistId: String,
        settings: UmihiSettings,
        title: String? = null,
        description: String? = null,
        privacy: Privacy? = null,
        videoIdsToAdd: List<String>? = null,
        videosToRemove: List<Pair<String, String?>>? = null,
    ) {
        YoutubeApiClient.editPlaylist(
            playlistId = playlistId,
            settings = settings,
            title = title,
            description = description,
            privacy = privacy,
            videoIdsToAdd = videoIdsToAdd,
            videosToRemove = videosToRemove,
        )
    }
}
