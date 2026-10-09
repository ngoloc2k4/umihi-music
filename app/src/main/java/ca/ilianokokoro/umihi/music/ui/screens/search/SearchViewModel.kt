package ca.ilianokokoro.umihi.music.ui.screens.search


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SearchState())
    val uiState = _uiState.asStateFlow()

    private val datastoreRepository = DatastoreRepository(application)
    val songRepository = SongRepository(application)

    init {
        observeLoginState()
    }

    private fun observeLoginState() {
        viewModelScope.launch {
            datastoreRepository.cookies.collect { cookies ->
                _uiState.update { it.copy(isLoggedIn = cookies.isNotEmpty()) }
            }
        }
    }


    fun search() {
        val query = _uiState.value.search.trim()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(screenState = ScreenState.Success(results = listOf()))
            }
            return
        }

        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            val currentFilter = _uiState.value.activeFilter

            songRepository.search(
                query = query,
                filterParams = currentFilter.params,
                settings = settings
            ).collect { apiResult ->
                when (apiResult) {
                    ApiResult.Loading -> {
                        _uiState.update { it.copy(screenState = ScreenState.Loading) }
                    }
                    is ApiResult.Error -> {
                        _uiState.update { it.copy(screenState = ScreenState.Error(apiResult.exception)) }
                    }
                    is ApiResult.Success -> {
                        var results = apiResult.data

                        // If SONGS filter returned empty or very few/poor matches for viral/colloquial query,
                        // fallback to search ALL to retrieve music videos/tracks and surface them.
                        if (currentFilter == SearchFilter.SONGS && results.size < 3) {
                            try {
                                val allResults = songRepository.search(
                                    query = query,
                                    filterParams = null,
                                    settings = settings
                                )
                                allResults.collect { allApiResult ->
                                    if (allApiResult is ApiResult.Success && allApiResult.data.isNotEmpty()) {
                                        results = (results + allApiResult.data).distinctBy { it.youtubeId }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        // Relevance ranking: songs matching title/artist words ranked highest
                        val ranked = rankSearchResults(results, query)

                        _uiState.update {
                            it.copy(screenState = ScreenState.Success(results = ranked))
                        }
                    }
                }
            }
        }
    }

    private fun rankSearchResults(songs: List<Song>, query: String): List<Song> {
        val cleanQuery = query.lowercase().trim()
        val queryWords = cleanQuery.split("\\s+".toRegex()).filter { it.length > 1 }

        fun score(song: Song): Int {
            val title = song.title.lowercase()
            val artist = song.artist.lowercase()
            var s = 0

            // Exact match
            if (title == cleanQuery) s += 1000
            if (title.contains(cleanQuery)) s += 500
            if (artist.contains(cleanQuery)) s += 300

            // Word-level matching
            val matchedWords = queryWords.count { title.contains(it) || artist.contains(it) }
            s += matchedWords * 50

            // Slight preference for official songs over random clips unless title matches strongly
            if (!song.isVideo) s += 20

            return s
        }

        return songs.sortedByDescending { score(it) }
    }

    fun onFilterChange(filter: SearchFilter) {
        if (_uiState.value.activeFilter != filter) {
            _uiState.update {
                it.copy(activeFilter = filter)
            }
            if (_uiState.value.search.isNotBlank()) {
                search()
            }
        }
    }


    fun onSearchFieldChange(newValue: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(search = newValue)
            }

            songRepository.searchAutocomplete(newValue).collect { apiResult ->
                _uiState.update {
                    _uiState.value.copy(
                        suggestions = when (apiResult) {
                            is ApiResult.Success -> {
                                apiResult.data
                            }

                            is ApiResult.Error -> listOf()
                            ApiResult.Loading -> _uiState.value.suggestions
                        }
                    )
                }
            }
        }
    }


    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(application)
            }
        }
    }
}