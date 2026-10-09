package ca.ilianokokoro.umihi.music.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.ComposeHelper
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.HomeSectionItem
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.FadingStatusBarWrapper
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.LoginBanner
import ca.ilianokokoro.umihi.music.ui.components.artist.ArtistCard
import ca.ilianokokoro.umihi.music.ui.components.bottomsheet.PlaylistCreationBottomSheet
import ca.ilianokokoro.umihi.music.ui.components.materialu.MaterialUButton
import ca.ilianokokoro.umihi.music.ui.components.playlist.PlaylistCard
import ca.ilianokokoro.umihi.music.ui.components.song.HomeSongCard
import ca.ilianokokoro.umihi.music.ui.navigation.viewmodels.SharedViewModel

@Composable
fun HomeScreen(
    sharedViewModel: SharedViewModel,
    onPlaylistPressed: (playlistInfo: PlaylistInfo) -> Unit,
    onLoginPressed: () -> Unit = {},
    application: Application,
    homeViewModel: HomeViewModel = viewModel(
        factory =
            HomeViewModel.Factory(application = application)
    )

) {
    val uiState = homeViewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    var createPlaylistOpen by remember { mutableStateOf(false) }

    val deletedPlaylistIds by sharedViewModel.deletedPlaylistIds.collectAsState()
    val playlistRefreshNeeded by sharedViewModel.playlistRefreshNeeded.collectAsState()

    LaunchedEffect(deletedPlaylistIds, playlistRefreshNeeded) {
        when {
            playlistRefreshNeeded -> {
                homeViewModel.refreshPlaylists()
                sharedViewModel.consumePlaylistRefresh()
                sharedViewModel.consumeDeletedPlaylists()
            }

            deletedPlaylistIds.isNotEmpty() -> {
                homeViewModel.removePlaylistsFromList(deletedPlaylistIds)
                sharedViewModel.consumeDeletedPlaylists()
            }
        }
    }

    FadingStatusBarWrapper { statusBarHeight ->
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (uiState.screenState) {
                    is ScreenState.LoggedIn -> {
                        val loggedIn = uiState.screenState
                        val playlists = loggedIn.playlistInfos.filter { !it.hidden }
                        val sections = loggedIn.sections

                        if (playlists.isEmpty() && sections.isEmpty()) {
                            Text(
                                stringResource(R.string.no_playlists),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            PullToRefreshBox(
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = homeViewModel::refreshPlaylists,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(
                                        top = paddingValues.calculateTopPadding() + statusBarHeight + 8.dp,
                                        bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
                                    )
                                ) {
                                    // Time-based Greeting Header + History Button
                                    item(key = "time_greeting_header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${uiState.timeGreetingEmoji}  ${stringResource(uiState.timeGreetingRes)}",
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            androidx.compose.material3.IconButton(
                                                onClick = { homeViewModel.openHistorySheet() }
                                            ) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.Outlined.History,
                                                    contentDescription = stringResource(R.string.history_title),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    // Category Filter Chips
                                    item(key = "category_filter_chips") {
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            items(HomeCategory.entries) { category ->
                                                val isSelected = category == uiState.selectedCategory
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { homeViewModel.selectCategory(category) },
                                                    label = {
                                                        Text(
                                                            text = "${category.iconEmoji} ${stringResource(category.titleRes)}",
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(20.dp),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    // Quick Resume Hero (2x3 Grid for recent songs)
                                    if (uiState.selectedCategory == HomeCategory.FOR_YOU && uiState.quickPlaySongs.isNotEmpty()) {
                                        item(key = "quick_play_grid") {
                                            ca.ilianokokoro.umihi.music.ui.components.home.QuickPlayGrid(
                                                songs = uiState.quickPlaySongs
                                            )
                                        }
                                    }

                                    // Login Banner if not logged in
                                    if (!loggedIn.isLoggedIn) {
                                        item(key = "login_banner") {
                                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                LoginBanner(
                                                    onLoginPressed = onLoginPressed
                                                )
                                            }
                                        }
                                    }

                                    // 1. Render YouTube Music Recommendation Sections
                                    sections.forEach { section ->
                                        if (section.items.isNotEmpty()) {
                                            item(key = "section_${section.id}_${section.title}") {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    // Section Title
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = section.title,
                                                            style = MaterialTheme.typography.titleLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (!section.subtitle.isNullOrBlank()) {
                                                            Text(
                                                                text = section.subtitle,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    // Horizontal Carousel of items
                                                    LazyRow(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        contentPadding = PaddingValues(horizontal = 16.dp)
                                                    ) {
                                                        items(
                                                            items = section.items,
                                                            key = { item ->
                                                                when (item) {
                                                                    is HomeSectionItem.SongItem -> "song_${item.song.youtubeId}_${item.song.uid}_${item.rank}"
                                                                    is HomeSectionItem.PlaylistItem -> "playlist_${item.playlistInfo.id}"
                                                                    is HomeSectionItem.ArtistItem -> "artist_${item.name}"
                                                                }
                                                            }
                                                        ) { item ->
                                                            when (item) {
                                                                is HomeSectionItem.SongItem -> {
                                                                    HomeSongCard(
                                                                        song = item.song,
                                                                        rank = item.rank,
                                                                        onClicked = {
                                                                            PlayerManager.playSong(item.song)
                                                                        },
                                                                        playNext = {
                                                                            PlayerManager.addNext(item.song, context)
                                                                        },
                                                                        addToQueue = {
                                                                            PlayerManager.addToQueue(item.song, context)
                                                                        }
                                                                    )
                                                                }
                                                                is HomeSectionItem.PlaylistItem -> {
                                                                    PlaylistCard(
                                                                        playlistInfo = item.playlistInfo,
                                                                        onClicked = { onPlaylistPressed(item.playlistInfo) },
                                                                        modifier = Modifier.width(150.dp)
                                                                    )
                                                                }
                                                                is HomeSectionItem.ArtistItem -> {
                                                                    ArtistCard(
                                                                        name = item.name,
                                                                        thumbnailHref = item.thumbnailHref,
                                                                        songCount = item.songCount,
                                                                        onClicked = {
                                                                            homeViewModel.playArtistRadio(item.name)
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2. Render User's Library Playlists Section
                                    if (playlists.isNotEmpty()) {
                                        item(key = "user_playlists_section") {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.your_playlists),
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )

                                                    if (loggedIn.isLoggedIn) {
                                                        MaterialUButton(
                                                            onClick = {
                                                                createPlaylistOpen = true
                                                            },
                                                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                            text = stringResource(R.string.create_playlist)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                                ) {
                                                    itemsIndexed(
                                                        items = playlists,
                                                        key = { index, playlist ->
                                                            ComposeHelper.getLazyKey(
                                                                playlist,
                                                                playlist.id,
                                                                index
                                                            )
                                                        }
                                                    ) { _, playlist ->
                                                        PlaylistCard(
                                                            playlistInfo = playlist,
                                                            onClicked = { onPlaylistPressed(playlist) },
                                                            modifier = Modifier.width(150.dp),
                                                            songCount = if (playlist.isDownloadedPlaylist) {
                                                                uiState.downloadedSongsCount
                                                            } else {
                                                                playlist.songCount
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ScreenState.Loading -> LoadingAnimation()
                    is ScreenState.Error -> ErrorMessage(
                        ex = uiState.screenState.exception,
                        onRetry = homeViewModel::getPlaylists
                    )

                }
                if (createPlaylistOpen) {
                    PlaylistCreationBottomSheet(
                        onClose = { createPlaylistOpen = false },
                        onConfirm = { title, description, privacy ->
                            homeViewModel.createPlaylist(title, description, privacy)
                            createPlaylistOpen = false
                        })

                }

                if (uiState.showHistorySheet) {
                    ca.ilianokokoro.umihi.music.ui.components.bottomsheet.HistoryBottomSheet(
                        onDismissRequest = { homeViewModel.closeHistorySheet() },
                        songs = uiState.historySongs,
                        onClearAll = { homeViewModel.clearAllHistory() },
                        onRemoveItem = { youtubeId -> homeViewModel.removeHistoryItem(youtubeId) }
                    )
                }
            }
        }
    }

}
