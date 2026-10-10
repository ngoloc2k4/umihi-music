package ca.ilianokokoro.umihi.music.ui.screens.home


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlaylistRepository
import ca.ilianokokoro.umihi.music.models.HomeSection
import ca.ilianokokoro.umihi.music.models.HomeSectionItem
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.enums.Privacy
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HomeState())
    val uiState = _uiState.asStateFlow()

    private val playlistRepository = PlaylistRepository(application)
    private val datastoreRepository = DatastoreRepository(application)
    private val historyRepository = HistoryRepository(application)
    private val songDataSource = ca.ilianokokoro.umihi.music.data.datasources.SongDataSource()
    private val blockedContentRepository = ca.ilianokokoro.umihi.music.data.repositories.BlockedContentRepository.getInstance(application)

    init {
        getPlaylists()
        observeDownloadedSongsCount()
    }

    private fun observeDownloadedSongsCount() {
        viewModelScope.launch {
            playlistRepository.getDownloadedSongsCountFlow().collect { count ->
                _uiState.update { currentState ->
                    currentState.copy(downloadedSongsCount = count)
                }
            }
        }
    }

    fun selectCategory(category: HomeCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
        getPlaylists()
    }

    fun getPlaylists() {
        viewModelScope.launch {
            getPlaylistsSuspend()
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isRefreshing = true)
            }

            try {
                refreshPlaylistsOnce()
            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
            } finally {
                _uiState.update { currentState ->
                    currentState.copy(isRefreshing = false)
                }
            }
        }
    }

    private suspend fun refreshPlaylistsOnce() = coroutineScope {
        val settings = datastoreRepository.getSettings()
        val category = _uiState.value.selectedCategory

        val sectionsDeferred = async { fetchSectionsForCategory(category, settings) }

        val playlistsDeferred = async {
            if (settings.cookies.isEmpty()) {
                emptyList()
            } else {
                try {
                    val result = playlistRepository.retrieveAll(settings)
                        .first { it is ApiResult.Success || it is ApiResult.Error }
                    if (result is ApiResult.Success) result.data else emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

        val sections = sectionsDeferred.await()
        val playlists = playlistsDeferred.await()

        applyFiltersAndUpdateState(
            sections = sections,
            playlists = playlists,
            settings = settings
        )
    }

    suspend fun getPlaylistsSuspend() = coroutineScope {
        try {
            _uiState.update { it.copy(screenState = ScreenState.Loading) }
            val settings = datastoreRepository.getSettings()
            val category = _uiState.value.selectedCategory

            val sectionsDeferred = async { fetchSectionsForCategory(category, settings) }

            val playlistsDeferred = async {
                if (settings.cookies.isEmpty()) {
                    emptyList()
                } else {
                    try {
                        val result = playlistRepository.retrieveAll(settings)
                            .first { it is ApiResult.Success || it is ApiResult.Error }
                        if (result is ApiResult.Success) result.data else emptyList()
                    } catch (e: Exception) {
                        printe(message = "Failed to load playlists: ${e.message}", exception = e)
                        emptyList()
                    }
                }
            }

            val sections = sectionsDeferred.await()
            val playlists = playlistsDeferred.await()

            applyFiltersAndUpdateState(
                sections = sections,
                playlists = playlists,
                settings = settings
            )
        } catch (ex: Exception) {
            printe(message = ex.toString(), exception = ex)
            _uiState.update { it.copy(screenState = ScreenState.Error(ex)) }
        }
    }

    private fun getTimeGreeting(): Pair<Int, String> {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> R.string.greeting_morning to "☀️"
            in 12..17 -> R.string.greeting_afternoon to "🌤️"
            in 18..22 -> R.string.greeting_evening to "🌆"
            else -> R.string.greeting_night to "🌙"
        }
    }

    fun openHistorySheet() {
        viewModelScope.launch {
            val list = try { historyRepository.getRecentSongsList(50) } catch (_: Exception) { emptyList() }
            _uiState.update { it.copy(showHistorySheet = true, historySongs = list) }
        }
    }

    fun closeHistorySheet() {
        _uiState.update { it.copy(showHistorySheet = false) }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                historyRepository.clearHistory()
                _uiState.update { it.copy(historySongs = emptyList(), quickPlaySongs = emptyList()) }
                getPlaylists()
            } catch (e: Exception) {
                printe(message = "Failed to clear history: ${e.message}", exception = e)
            }
        }
    }

    fun removeHistoryItem(youtubeId: String) {
        viewModelScope.launch {
            try {
                historyRepository.removeSongFromHistory(youtubeId)
                _uiState.update { state ->
                    state.copy(
                        historySongs = state.historySongs.filterNot { it.youtubeId == youtubeId },
                        quickPlaySongs = state.quickPlaySongs.filterNot { it.youtubeId == youtubeId }
                    )
                }
            } catch (e: Exception) {
                printe(message = "Failed to remove history item: ${e.message}", exception = e)
            }
        }
    }

    fun playArtistRadio(artistName: String) {
        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()
                val results = songDataSource.search(
                    query = "$artistName hits top songs",
                    settings = settings
                )
                val topSong = results.firstOrNull { it.artist.contains(artistName, ignoreCase = true) }
                    ?: results.firstOrNull()

                if (topSong != null) {
                    ca.ilianokokoro.umihi.music.core.managers.PlayerManager.playSong(topSong, autoRadio = true)
                }
            } catch (e: Exception) {
                printe(message = "Failed to play artist radio for $artistName: ${e.message}", exception = e)
            }
        }
    }

    private fun getCountryMusicTerm(countryCode: String?): String {
        val code = when (countryCode) {
            null, "", "SYSTEM" -> java.util.Locale.getDefault().country.ifBlank { "VN" }
            else -> countryCode
        }.uppercase()

        return when (code) {
            "VN" -> "Vietnam Pop V-Pop"
            "US" -> "US-UK Billboard Pop Hot"
            "GB", "UK" -> "UK Top Hits Pop"
            "JP" -> "J-Pop Japan Hits Anime"
            "KR" -> "K-Pop Korea Hits Idol"
            "TH" -> "Thai Pop Hits T-Pop"
            "ID" -> "Indonesia Pop Hits Indo"
            "PH" -> "OPM Philippines Pop Hits"
            "FR" -> "France Pop Hits Variete"
            "DE" -> "German Pop Hits Charts"
            "ES", "MX", "AR", "CO" -> "Latin Pop Reggaeton Hits"
            "BR", "PT" -> "Brasil Funk Pop Hits Sertanejo"
            "IN" -> "Bollywood India Hits Punjabi Pop"
            "CN", "TW", "HK" -> "C-Pop Mandopop Cantopop Hits"
            else -> "$code Pop Top Hits"
        }
    }

    private suspend fun fetchContextualTimeShelf(settings: UmihiSettings): List<HomeSection> {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val countryTerm = getCountryMusicTerm(settings.countryCode)
        val (query, titleRes) = when (hour) {
            in 5..11 -> "Acoustic Pop Morning Coffee Chill Songs $countryTerm" to R.string.context_morning_title
            in 12..17 -> "Deep Focus Study Piano Work Lofi Beats" to R.string.context_afternoon_title
            in 18..22 -> "Evening Wind Down Chillout Pop Songs $countryTerm" to R.string.context_evening_title
            else -> "Night Sleep Rain Lofi Bedtime Relax Music" to R.string.context_night_title
        }
        return try {
            val res = playlistRepository.retrieveMoodSections(query, application.getString(titleRes), settings)
                .first { it is ApiResult.Success || it is ApiResult.Error }
            if (res is ApiResult.Success) res.data else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun computeOrderedCategories(recentSongs: List<ca.ilianokokoro.umihi.music.models.Song>): List<HomeCategory> {
        if (recentSongs.isEmpty()) return HomeCategory.entries

        val categoryScores = mutableMapOf<HomeCategory, Int>()
        val chillKeywords = setOf("chill", "relax", "acoustic", "lofi", "piano", "coffee", "ballad", "ambient")
        val workoutKeywords = setOf("workout", "gym", "edm", "remix", "dance", "bass", "trap", "hardstyle", "energy", "run")
        val focusKeywords = setOf("focus", "study", "instrumental", "classical", "deep work", "reading")
        val partyKeywords = setOf("party", "club", "dj", "festival", "dancehall", "vinahouse", "electronic")
        val romanceKeywords = setOf("love", "romance", "sweet", "ballad", "tình", "romantic", "crush")
        val sleepKeywords = setOf("sleep", "rain", "night", "bedtime", "calm", "meditation")

        for (song in recentSongs) {
            val text = "${song.title} ${song.artist}".lowercase()
            if (chillKeywords.any { text.contains(it) }) categoryScores[HomeCategory.CHILL] = (categoryScores[HomeCategory.CHILL] ?: 0) + 1
            if (workoutKeywords.any { text.contains(it) }) categoryScores[HomeCategory.WORKOUT] = (categoryScores[HomeCategory.WORKOUT] ?: 0) + 1
            if (focusKeywords.any { text.contains(it) }) categoryScores[HomeCategory.FOCUS] = (categoryScores[HomeCategory.FOCUS] ?: 0) + 1
            if (partyKeywords.any { text.contains(it) }) categoryScores[HomeCategory.PARTY] = (categoryScores[HomeCategory.PARTY] ?: 0) + 1
            if (romanceKeywords.any { text.contains(it) }) categoryScores[HomeCategory.ROMANCE] = (categoryScores[HomeCategory.ROMANCE] ?: 0) + 1
            if (sleepKeywords.any { text.contains(it) }) categoryScores[HomeCategory.SLEEP] = (categoryScores[HomeCategory.SLEEP] ?: 0) + 1
        }

        val dynamicMoods = listOf(
            HomeCategory.CHILL,
            HomeCategory.WORKOUT,
            HomeCategory.FOCUS,
            HomeCategory.PARTY,
            HomeCategory.ROMANCE,
            HomeCategory.SLEEP
        ).sortedByDescending { categoryScores[it] ?: 0 }

        return listOf(HomeCategory.FOR_YOU, HomeCategory.CHARTS) + dynamicMoods
    }

    private suspend fun fetchSectionsForCategory(
        category: HomeCategory,
        settings: UmihiSettings
    ): List<HomeSection> = coroutineScope {
        val (greetingRes, greetingEmoji) = getTimeGreeting()
        _uiState.update { it.copy(timeGreetingRes = greetingRes, timeGreetingEmoji = greetingEmoji) }

        val countryTerm = getCountryMusicTerm(settings.countryCode)

        when (category) {
            HomeCategory.FOR_YOU -> {
                val historyDeferred = async {
                    try { historyRepository.getRecentSongsList(50) } catch (_: Exception) { emptyList() }
                }
                val homeSectionsDeferred = async {
                    try {
                        val res = playlistRepository.retrieveHomeSections(settings)
                            .first { it is ApiResult.Success || it is ApiResult.Error }
                        if (res is ApiResult.Success) res.data else emptyList()
                    } catch (_: Exception) { emptyList() }
                }
                val contextualDeferred = async {
                    fetchContextualTimeShelf(settings)
                }
                val trendingShelfDeferred = async {
                    try {
                        val res = playlistRepository.retrieveMoodSections(
                            "Trending Viral TikTok Hits $countryTerm",
                            application.getString(R.string.trending_tiktok_title),
                            settings
                        ).first { it is ApiResult.Success || it is ApiResult.Error }
                        if (res is ApiResult.Success) res.data else emptyList()
                    } catch (_: Exception) { emptyList() }
                }

                val recentSongs = historyDeferred.await()
                val homeSections = homeSectionsDeferred.await()
                val contextualSections = contextualDeferred.await()
                val trendingSections = trendingShelfDeferred.await()

                // Dynamically reorder category chips based on user's actual listening genres
                val orderedChips = computeOrderedCategories(recentSongs)

                // Update quickPlaySongs, historySongs, and orderedCategories in state
                _uiState.update { currentState ->
                    currentState.copy(
                        quickPlaySongs = recentSongs.take(6),
                        historySongs = recentSongs,
                        orderedCategories = orderedChips
                    )
                }

                val dynamicSections = mutableListOf<HomeSection>()

                if (recentSongs.isNotEmpty()) {
                    val artistCounts = recentSongs
                        .map { it.artist.trim() }
                        .filter { it.isNotBlank() }
                        .groupingBy { it }
                        .eachCount()
                        .toList()
                        .sortedByDescending { it.second }
                        .take(6)

                    // 1. Daily Mix 1, 2, 3 (Fetched concurrently in parallel)
                    val top3Artists = artistCounts.take(3).map { it.first }
                    val dailyMixDeferreds = top3Artists.mapIndexed { index, artist ->
                        async {
                            val artistSong = recentSongs.firstOrNull { it.artist.contains(artist, ignoreCase = true) }
                            if (artistSong != null && artistSong.youtubeId.isNotBlank()) {
                                try {
                                    val related = songDataSource.getRelatedSongs(artistSong.youtubeId, settings)
                                    if (related.isNotEmpty()) {
                                        val mixTitle = String.format(application.getString(R.string.daily_mix_title), index + 1) + " • $artist"
                                        val artistOwnSongs = recentSongs.filter { it.artist.contains(artist, ignoreCase = true) }.take(5)
                                        val blended = (artistOwnSongs + related.filter { rel -> artistOwnSongs.none { it.youtubeId == rel.youtubeId } }).distinctBy { it.youtubeId }.take(20)
                                        HomeSection(
                                            id = "daily_mix_${index + 1}",
                                            title = mixTitle,
                                            subtitle = application.getString(R.string.supermix_subtitle),
                                            items = blended.map { HomeSectionItem.SongItem(it) }
                                        )
                                    } else null
                                } catch (_: Exception) { null }
                            } else null
                        }
                    }
                    val dailyMixSections = dailyMixDeferreds.awaitAll().filterNotNull()
                    dynamicSections.addAll(dailyMixSections)

                    // 3. Forgotten Favorites (older songs from history)
                    if (recentSongs.size > 6) {
                        val olderSongs = recentSongs.drop(6).take(15).distinctBy { it.youtubeId }
                        if (olderSongs.isNotEmpty()) {
                            dynamicSections.add(
                                HomeSection(
                                    id = "forgotten_favorites",
                                    title = application.getString(R.string.forgotten_favorites_title),
                                    subtitle = application.getString(R.string.forgotten_favorites_subtitle),
                                    items = olderSongs.map { HomeSectionItem.SongItem(it) }
                                )
                            )
                        }
                    }
                } else {
                    // Fallback discovery shelves for new users or when history is empty
                    val discoveryDeferred = async {
                        try {
                            val res = playlistRepository.retrieveMoodSections(
                                "Top Hits $countryTerm Billboard Hot",
                                application.getString(R.string.discover_weekly_title),
                                settings
                            ).first { it is ApiResult.Success || it is ApiResult.Error }
                            if (res is ApiResult.Success) res.data else emptyList()
                        } catch (_: Exception) { emptyList() }
                    }
                    val cafeAcousticDeferred = async {
                        try {
                            val res = playlistRepository.retrieveMoodSections(
                                "Acoustic Pop Guitar Chill Cafe Songs $countryTerm",
                                application.getString(R.string.cafe_acoustic_title),
                                settings
                            ).first { it is ApiResult.Success || it is ApiResult.Error }
                            if (res is ApiResult.Success) res.data else emptyList()
                        } catch (_: Exception) { emptyList() }
                    }
                    dynamicSections.addAll(discoveryDeferred.await())
                    dynamicSections.addAll(cafeAcousticDeferred.await())
                }

                // 4. Add Contextual Time Shelf (Coffee morning / Deep focus / Night chill)
                dynamicSections.addAll(contextualSections)

                // 5. Add Trending / Themed Shelf
                dynamicSections.addAll(trendingSections)

                // 6. Add YouTube Music official recommendation sections (deduplicated)
                (dynamicSections + homeSections).distinctBy { it.id.ifBlank { it.title } }
            }

            HomeCategory.CHARTS -> {
                try {
                    val res = playlistRepository.retrieveChartsSections(settings)
                        .first { it is ApiResult.Success || it is ApiResult.Error }
                    val rawSections = if (res is ApiResult.Success && res.data.isNotEmpty()) res.data else emptyList()
                    rawSections.map { section ->
                        val rankedItems = section.items.mapIndexed { idx, item ->
                            if (item is HomeSectionItem.SongItem) {
                                item.copy(rank = idx + 1)
                            } else {
                                item
                            }
                        }
                        section.copy(items = rankedItems)
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            HomeCategory.CHILL -> {
                try {
                    val recentSongs = try { historyRepository.getRecentSongsList(20) } catch (_: Exception) { emptyList() }
                    val topArtist = recentSongs.map { it.artist }.firstOrNull { it.isNotBlank() }
                    val query = if (topArtist != null) "$topArtist Chill Acoustic Lofi Relax $countryTerm" else "Chill Acoustic Lofi Relax songs $countryTerm"
                    val res = playlistRepository.retrieveMoodSections(
                        query,
                        application.getString(R.string.category_chill),
                        settings
                    ).first { it is ApiResult.Success || it is ApiResult.Error }
                    if (res is ApiResult.Success) res.data else emptyList()
                } catch (_: Exception) { emptyList() }
            }

            HomeCategory.WORKOUT -> {
                try {
                    val recentSongs = try { historyRepository.getRecentSongsList(20) } catch (_: Exception) { emptyList() }
                    val topArtist = recentSongs.map { it.artist }.firstOrNull { it.isNotBlank() }
                    val query = if (topArtist != null) "$topArtist Workout Gym EDM Energy $countryTerm" else "Workout gym EDM dance energy music $countryTerm"
                    val res = playlistRepository.retrieveMoodSections(
                        query,
                        application.getString(R.string.category_workout),
                        settings
                    ).first { it is ApiResult.Success || it is ApiResult.Error }
                    if (res is ApiResult.Success) res.data else emptyList()
                } catch (_: Exception) { emptyList() }
            }

            HomeCategory.FOCUS -> {
                try {
                    val res = playlistRepository.retrieveMoodSections(
                        "Focus study piano classical deep work lofi $countryTerm",
                        application.getString(R.string.category_focus),
                        settings
                    ).first { it is ApiResult.Success || it is ApiResult.Error }
                    if (res is ApiResult.Success) res.data else emptyList()
                } catch (_: Exception) { emptyList() }
            }

            HomeCategory.PARTY -> {
                try {
                    val recentSongs = try { historyRepository.getRecentSongsList(20) } catch (_: Exception) { emptyList() }
                    val topArtist = recentSongs.map { it.artist }.firstOrNull { it.isNotBlank() }
                    val query = if (topArtist != null) "$topArtist Party Dance Club Remix $countryTerm" else "Party dance remix club festival $countryTerm"
                    val res = playlistRepository.retrieveMoodSections(
                        query,
                        application.getString(R.string.category_party),
                        settings
                    ).first { it is ApiResult.Success || it is ApiResult.Error }
                    if (res is ApiResult.Success) res.data else emptyList()
                } catch (_: Exception) { emptyList() }
            }

            HomeCategory.ROMANCE -> {
                try {
                    val recentSongs = try { historyRepository.getRecentSongsList(20) } catch (_: Exception) { emptyList() }
                    val topArtist = recentSongs.map { it.artist }.firstOrNull { it.isNotBlank() }
                    val query = if (topArtist != null) "$topArtist Love Ballad Romance $countryTerm" else "Romance acoustic love ballad sweet songs $countryTerm"
                    val res = playlistRepository.retrieveMoodSections(
                        query,
                        application.getString(R.string.category_romance),
                        settings
                    ).first { it is ApiResult.Success || it is ApiResult.Error }
                    if (res is ApiResult.Success) res.data else emptyList()
                } catch (_: Exception) { emptyList() }
            }

            HomeCategory.SLEEP -> {
                try {
                    val res = playlistRepository.retrieveMoodSections(
                        "Sleep rain relax calm bedtime lofi $countryTerm",
                        application.getString(R.string.category_sleep),
                        settings
                    ).first { it is ApiResult.Success || it is ApiResult.Error }
                    if (res is ApiResult.Success) res.data else emptyList()
                } catch (_: Exception) { emptyList() }
            }
        }
    }

    private fun applyFiltersAndUpdateState(
        sections: List<HomeSection>,
        playlists: List<PlaylistInfo>,
        settings: UmihiSettings
    ) {
        val filteredSections = sections.mapNotNull { section ->
            val cleanItems = section.items.filterNot { item ->
                when (item) {
                    is HomeSectionItem.SongItem -> blockedContentRepository.isSongBlocked(item.song)
                    is HomeSectionItem.ArtistItem -> blockedContentRepository.isContentBlocked(artist = item.name, title = "")
                    is HomeSectionItem.PlaylistItem -> false
                }
            }
            if (cleanItems.isEmpty()) null else section.copy(items = cleanItems)
        }

        val mutablePlaylists = playlists.toMutableList()
        val downloadedPlaylist = PlaylistInfo(
            id = Constants.Downloads.DOWNLOADED_PLAYLIST_ID,
            title = application.getString(R.string.downloaded),
        )

        mutablePlaylists.add(0, downloadedPlaylist)

        _uiState.update { currentState ->
            currentState.copy(
                screenState = ScreenState.LoggedIn(
                    sections = filteredSections,
                    playlistInfos = mutablePlaylists,
                    isLoggedIn = settings.cookies.isNotEmpty()
                )
            )
        }
    }

    fun createPlaylist(title: String, description: String, privacy: Privacy) {
        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()

                if (settings.cookies.isEmpty()) {
                    return@launch
                }

                playlistRepository.create(title, description, privacy, settings)
                    .collect { apiResult ->
                        if (apiResult !is ApiResult.Success || apiResult.data == null) {
                            return@collect
                        }

                        val currentState = _uiState.value.screenState
                        if (currentState !is ScreenState.LoggedIn) {
                            return@collect
                        }

                        val updatedPlaylists = currentState.playlistInfos
                            .toMutableList()
                            .apply {
                                add(index = 2.coerceAtMost(size), element = apiResult.data)
                            }

                        _uiState.update {
                            it.copy(
                                screenState = currentState.copy(
                                    playlistInfos = updatedPlaylists
                                )
                            )
                        }
                    }
            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
            }
        }
    }

    fun removePlaylistsFromList(playlistIds: Set<String>) {
        _uiState.update { currentState ->
            val loggedIn = currentState.screenState as? ScreenState.LoggedIn
                ?: return@update currentState

            currentState.copy(
                screenState = loggedIn.copy(
                    playlistInfos = loggedIn.playlistInfos.filterNot { playlist ->
                        playlist.id in playlistIds
                    }
                )
            )
        }
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(application)
            }
        }
    }
}