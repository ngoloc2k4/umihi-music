package ca.ilianokokoro.umihi.music.ui.screens.playlist


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.data.datasources.SongDataSource
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.DownloadRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlaylistRepository
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.navigation.viewmodels.SharedViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class PlaylistViewModel(
    private val playlistInfo: PlaylistInfo,
    private val sharedViewModel: SharedViewModel,
    private val application: Application
) :
    AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        PlaylistState(
            screenState = ScreenState.Loading(playlistInfo)
        )
    )
    val uiState = _uiState.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun showSearch() {
        _uiState.update { it.copy(showingSearch = true) }
    }

    fun hideSearch() {
        _uiState.update { it.copy(showingSearch = false, searchQuery = "") }
    }

    private val playlistRepository = PlaylistRepository(application)
    private val localPlaylistRepository = AppDatabase.getInstance(application).playlistRepository()
    private val datastoreRepository = DatastoreRepository(application)
    private val downloadRepository = DownloadRepository(application)
    private val songDataSource = SongDataSource()

    init {
        observeSongDownloads()
        viewModelScope.launch {
            getPlaylistInfoAsync()
            observerDownloadJob()
        }
    }

    private fun observeSongDownloads() {
        viewModelScope.launch {
            localPlaylistRepository.observePlaylistById(playlistInfo.id).collect { localPlaylist ->
                if (localPlaylist != null) {
                    _uiState.update { currentState ->
                        val screenState = currentState.screenState
                        if (screenState is ScreenState.Success) {
                            currentState.copy(
                                screenState = screenState.copy(
                                    playlist = updatePlaylistFrom(
                                        screenState.playlist,
                                        localPlaylist
                                    )
                                )
                            )
                        } else {
                            currentState
                        }
                    }
                }
            }

        }
    }

    suspend fun observerDownloadJob() {
        val playlist = getPlaylist() ?: return
        val existingJobFlow = downloadRepository.getExistingJobFlow(playlist)

        existingJobFlow.collect { workInfos ->
            val workInfo = workInfos.firstOrNull() ?: return@collect

            _uiState.update {
                it.copy(
                    isDownloading =
                        workInfo.state == WorkInfo.State.ENQUEUED ||
                                workInfo.state == WorkInfo.State.RUNNING ||
                                workInfo.state == WorkInfo.State.BLOCKED
                )
            }

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    printd("Download finished for ${playlist.info.title}")
                }

                WorkInfo.State.FAILED,
                WorkInfo.State.CANCELLED -> {
                    printd("Download failed or cancelled for ${playlist.info.title}")
                }

                else -> {}
            }
        }
    }

    fun refreshPlaylistInfo() {
        viewModelScope.launch {
            _uiState.update {
                _uiState.value.copy(
                    isRefreshing = true
                )
            }
            getPlaylistInfoAsync()
            _uiState.update {
                _uiState.value.copy(
                    isRefreshing = false
                )
            }
        }

    }

    fun getPlaylistInfo() {
        viewModelScope.launch {
            getPlaylistInfoAsync()
        }
    }

    fun playPlaylist(startingSong: Song? = null) {
        val playlist = getPlaylist() ?: return
        viewModelScope.launch {
            PlayerManager.playPlaylist(
                playlist,
                startingSong?.let { playlist.songs.indexOf(it) } ?: 0
            )
        }
    }

    fun shufflePlaylist() {
        val playlist = getPlaylist() ?: return
        viewModelScope.launch {
            PlayerManager.shufflePlaylist(playlist)
        }
    }

    fun downloadPlaylist() {
        val playlist = getPlaylist() ?: return
        viewModelScope.launch {
            if (playlist.downloaded) {
                return@launch
            }

            val settings = datastoreRepository.getSettings()
            downloadRepository.downloadPlaylist(playlist, settings.downloadOnMetered)
        }
    }

    fun deletePlaylist(onBack: () -> Unit) {
        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()
                if (settings.cookies.isEmpty()) {
                    throw Exception(application.getString(R.string.failed_get_to_login_cookies))
                }

                playlistRepository.delete(playlistInfo, settings)
                    .collect { apiResult ->
                        _uiState.update { _ ->
                            _uiState.value.copy(
                                screenState = when (apiResult) {
                                    is ApiResult.Error -> {
                                        ScreenState.Error(Exception(application.getString(R.string.failed_delete_playlist)))
                                    }

                                    ApiResult.Loading -> ScreenState.Loading(playlistInfo)
                                    is ApiResult.Success -> {
                                        onBack()
                                        sharedViewModel.markPlaylistDeleted(
                                            playlistInfo
                                        )
                                        ScreenState.Success(Playlist(PlaylistInfo()))
                                    }
                                }
                            )
                        }
                    }

            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
                _uiState.update {
                    _uiState.value.copy(
                        screenState = ScreenState.Error(ex)
                    )
                }
            }
        }
    }

    fun removeFromLibrary(onBack: () -> Unit) {
        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()
                if (settings.cookies.isEmpty()) {
                    throw Exception(application.getString(R.string.failed_get_to_login_cookies))
                }

                playlistRepository.removeFromLibrary(playlistInfo, settings)
                    .collect { apiResult ->
                        _uiState.update { _ ->
                            _uiState.value.copy(
                                screenState = when (apiResult) {
                                    is ApiResult.Error -> {
                                        ScreenState.Error(Exception(application.getString(R.string.failed_remove_from_library)))
                                    }

                                    ApiResult.Loading -> ScreenState.Loading(playlistInfo)
                                    is ApiResult.Success -> {
                                        onBack()
                                        sharedViewModel.markPlaylistDeleted(
                                            playlistInfo
                                        )
                                        ScreenState.Success(Playlist(PlaylistInfo()))
                                    }
                                }
                            )
                        }
                    }

            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
                _uiState.update {
                    _uiState.value.copy(
                        screenState = ScreenState.Error(ex)
                    )
                }
            }
        }
    }

    fun deleteLocalPlaylist() {
        val playlist = getPlaylist() ?: return
        viewModelScope.launch {
            downloadRepository.deletePlaylist(playlist)
            getPlaylistInfoAsync()
        }
    }

    fun cancelDownload() {
        if (!uiState.value.isDownloading) {
            return
        }
        val playlist = getPlaylist() ?: return
        viewModelScope.launch {
            downloadRepository.cancelPlaylistDownload(playlist)
        }
    }


    fun downloadSong(song: Song) {
        val playlist = getPlaylist() ?: return
        if (song.downloaded) {
            return
        }
        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            downloadRepository.downloadSong(playlist, song, settings.downloadOnMetered)
        }
    }

    private suspend fun getPlaylistInfoAsync() {
        try {
            val settings = datastoreRepository.getSettings()

            playlistRepository.retrieveOne(Playlist(playlistInfo), settings)
                .collect { apiResult ->
                    _uiState.update { _ ->
                        _uiState.value.copy(
                            screenState = when (apiResult) {
                                is ApiResult.Error -> {
                                    ScreenState.Error(apiResult.exception)
                                }

                                ApiResult.Loading -> ScreenState.Loading(playlistInfo)
                                is ApiResult.Success -> {
                                    ScreenState.Success(playlist = apiResult.data)
                                }
                            }
                        )
                    }

                    if (apiResult is ApiResult.Success) {
                        fetchRecommendations(apiResult.data)
                    }
                }

        } catch (ex: Exception) {
            printe(message = ex.toString(), exception = ex)
            _uiState.update {
                _uiState.value.copy(
                    screenState = ScreenState.Error(ex)
                )
            }
        }

    }

    private val usedSeedIds = mutableSetOf<String>()

    fun refreshRecommendations() {
        val playlist = getPlaylist() ?: return
        usedSeedIds.clear()
        fetchRecommendations(playlist, forceRefresh = true)
    }

    fun fetchRecommendations(playlist: Playlist, forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.recommendedSongs.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRecommendations = true) }
            try {
                val settings = datastoreRepository.getSettings()
                val existingIds = playlist.songs.map { it.youtubeId }.toSet()
                if (forceRefresh) {
                    usedSeedIds.clear()
                }

                val sampleSongs = playlist.songs.filterNot { it.youtubeId in usedSeedIds }.take(3)
                sampleSongs.forEach { usedSeedIds.add(it.youtubeId) }

                val suggestedSongs = if (sampleSongs.isNotEmpty()) {
                    coroutineScope {
                        sampleSongs.map { song ->
                            async {
                                try {
                                    songDataSource.getRelatedSongs(song.youtubeId, settings)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }.awaitAll().flatten()
                    }
                } else {
                    try {
                        val query = playlist.info.title.ifBlank { "Vietnam Pop Hits Music" }
                        songDataSource.search(query, settings = settings)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                val filtered = suggestedSongs
                    .filterNot { it.youtubeId in existingIds }
                    .distinctBy { it.youtubeId }
                    .take(20)

                _uiState.update {
                    it.copy(
                        recommendedSongs = filtered,
                        isLoadingRecommendations = false,
                        hasMoreRecommendations = filtered.isNotEmpty(),
                        showInfiniteSuggestions = settings.infinitePlaylistSuggestions
                    )
                }
            } catch (e: Exception) {
                printe(message = "Failed to load playlist recommendations: ${e.message}", exception = e)
                _uiState.update { it.copy(isLoadingRecommendations = false) }
            }
        }
    }

    fun loadMoreRecommendations() {
        val playlist = getPlaylist() ?: return
        val state = _uiState.value
        if (!state.showInfiniteSuggestions || state.isLoadingMoreRecommendations || state.isLoadingRecommendations || !state.hasMoreRecommendations) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreRecommendations = true) }
            try {
                val settings = datastoreRepository.getSettings()
                val playlistIds = playlist.songs.map { it.youtubeId }.toSet()
                val currentRecIds = state.recommendedSongs.map { it.youtubeId }.toSet()

                // Pick next seeds: first from unused playlist songs, then from unused recommended songs
                val nextSeeds = playlist.songs.filterNot { it.youtubeId in usedSeedIds }.take(2).ifEmpty {
                    state.recommendedSongs.filterNot { it.youtubeId in usedSeedIds }.take(2)
                }.ifEmpty {
                    // If all were used, pick 2 random from recommended songs to branch out
                    state.recommendedSongs.shuffled().take(2)
                }

                if (nextSeeds.isEmpty()) {
                    _uiState.update { it.copy(isLoadingMoreRecommendations = false, hasMoreRecommendations = false) }
                    return@launch
                }

                nextSeeds.forEach { usedSeedIds.add(it.youtubeId) }

                val newRawSongs = coroutineScope {
                    nextSeeds.map { song ->
                        async {
                            try {
                                songDataSource.getRelatedSongs(song.youtubeId, settings)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten()
                }

                val newUniqueSongs = newRawSongs
                    .filterNot { it.youtubeId in playlistIds || it.youtubeId in currentRecIds }
                    .distinctBy { it.youtubeId }
                    .take(15)

                if (newUniqueSongs.isNotEmpty()) {
                    val updatedList = state.recommendedSongs + newUniqueSongs
                    _uiState.update {
                        it.copy(
                            recommendedSongs = updatedList,
                            isLoadingMoreRecommendations = false,
                            hasMoreRecommendations = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingMoreRecommendations = false,
                            hasMoreRecommendations = false
                        )
                    }
                }
            } catch (e: Exception) {
                printe(message = "Failed to load more recommendations: ${e.message}", exception = e)
                _uiState.update { it.copy(isLoadingMoreRecommendations = false) }
            }
        }
    }

    fun addSongToPlaylist(song: Song) {
        val playlist = getPlaylist() ?: return
        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()
                if (settings.cookies.isEmpty()) {
                    return@launch
                }
                playlistRepository.edit(
                    playlistId = playlist.info.id,
                    settings = settings,
                    videoIdsToAdd = listOf(song.youtubeId)
                )
                android.widget.Toast.makeText(
                    application,
                    application.getString(R.string.added_to_playlist),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                getPlaylistInfoAsync()
            } catch (e: Exception) {
                printe(message = "Failed to add song to playlist: ${e.message}", exception = e)
            }
        }
    }

    private fun updatePlaylistFrom(oldPlaylist: Playlist, updatedPlaylist: Playlist?): Playlist {
        if (updatedPlaylist == null) {
            return oldPlaylist
        }
        val localMap = updatedPlaylist.songs.associateBy { it.youtubeId }
        val mergedSongs = oldPlaylist.songs.map { remoteSong ->
            localMap[remoteSong.youtubeId]?.copy(uid = Uuid.random().toString())
                ?: remoteSong
        }
        return oldPlaylist.copy(songs = mergedSongs)
    }

    private fun getPlaylist(): Playlist? {
        val screenState = _uiState.value.screenState
        if (screenState !is ScreenState.Success) {
            return null
        }
        return screenState.playlist
    }

    companion object {
        fun Factory(
            playlistInfo: PlaylistInfo,
            sharedViewModel: SharedViewModel,
            application: Application
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlaylistViewModel(playlistInfo, sharedViewModel, application)
                }
            }
    }
}