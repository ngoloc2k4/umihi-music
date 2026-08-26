package ca.ilianokokoro.umihi.music.ui.screens.playlist

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.FadingStatusBarWrapper
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.SearchBar
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.navigation.viewmodels.SharedViewModel
import ca.ilianokokoro.umihi.music.ui.screens.playlist.components.PlaylistHeader


@Composable
fun PlaylistScreen(
    sharedViewModel: SharedViewModel,
    playlistInfo: PlaylistInfo,
    onOpenPlayer: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    application: Application,
    playlistViewModel: PlaylistViewModel = viewModel(
        factory =
            PlaylistViewModel.Factory(
                playlistInfo = playlistInfo,
                sharedViewModel = sharedViewModel,
                application = application
            )
    )

) {
    val uiState = playlistViewModel.uiState.collectAsStateWithLifecycle().value
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()

    val isNearBottom by remember {
        derivedStateOf {
            val totalItemsCount = lazyListState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 3
        }
    }

    LaunchedEffect(isNearBottom, uiState.showInfiniteSuggestions, uiState.isLoadingMoreRecommendations, uiState.hasMoreRecommendations) {
        if (isNearBottom && uiState.showInfiniteSuggestions && !uiState.isLoadingMoreRecommendations && !uiState.isLoadingRecommendations && uiState.hasMoreRecommendations && uiState.recommendedSongs.isNotEmpty() && uiState.searchQuery.isBlank()) {
            playlistViewModel.loadMoreRecommendations()
        }
    }

    LaunchedEffect(uiState.showingSearch) {
        if (uiState.showingSearch) {
            focusRequester.requestFocus()
        }
    }

    FadingStatusBarWrapper {
        Scaffold(topBar = {
            if (uiState.showingSearch) {
                TopAppBar(
                    modifier = modifier,
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    actions = {
                        FilledIconButton(
                            onClick = playlistViewModel::hideSearch,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    title = {
                        SearchBar(
                            modifier = Modifier
                                .focusRequester(focusRequester),
                            value = uiState.searchQuery,
                            onValueChange = playlistViewModel::onSearchQueryChange,
                            onSearch = {
                                focusRequester.freeFocus()
                            },
                            focusManager = focusManager,
                            focusRequester = focusRequester,
                        )
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            playlistInfo.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    actions = {
                        FilledIconButton(
                            onClick = playlistViewModel::showSearch,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                    }
                )
            }
        }) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxSize()
            ) {

                if (uiState.screenState is ScreenState.Error) {
                    ErrorMessage(
                        ex = uiState.screenState.exception,
                        onRetry = playlistViewModel::getPlaylistInfo
                    )
                } else {
                    val playlistInfo: Playlist = when (uiState.screenState) {
                        is ScreenState.Loading -> {
                            Playlist(uiState.screenState.playlistInfo)
                        }

                        is ScreenState.Success -> {
                            uiState.screenState.playlist
                        }
                    }
                    val songs = playlistInfo.songs

                    if (uiState.screenState is ScreenState.Loading) {
                        Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
                        PlaylistHeader(
                            onOpenPlayer = onOpenPlayer,
                            isDownloading = uiState.isDownloading,
                            onDownloadPlaylist = playlistViewModel::downloadPlaylist,
                            onShufflePlaylist = playlistViewModel::shufflePlaylist,
                            onPlayPlaylist = playlistViewModel::playPlaylist,
                            onDeleteDownloadPlaylist = playlistViewModel::deleteLocalPlaylist,
                            onDeletePlaylist = { playlistViewModel.deletePlaylist(onBack) },
                            onRemoveFromLibrary = { playlistViewModel.removeFromLibrary(onBack) },
                            onCancelDownload = playlistViewModel::cancelDownload,
                            playlist = playlistInfo
                        )
                        LoadingAnimation()
                    } else {
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = playlistViewModel::refreshPlaylistInfo,
                            modifier = modifier
                                .fillMaxSize()
                        ) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING)
                            ) {
                                item { Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding())) }

                                item {
                                    PlaylistHeader(
                                        onOpenPlayer = onOpenPlayer,
                                        isDownloading = uiState.isDownloading,
                                        onDownloadPlaylist = playlistViewModel::downloadPlaylist,
                                        onShufflePlaylist = playlistViewModel::shufflePlaylist,
                                        onPlayPlaylist = playlistViewModel::playPlaylist,
                                        onDeleteDownloadPlaylist = playlistViewModel::deleteLocalPlaylist,
                                        onDeletePlaylist = { playlistViewModel.deletePlaylist(onBack) },
                                        onRemoveFromLibrary = { playlistViewModel.removeFromLibrary(onBack) },
                                        onCancelDownload = playlistViewModel::cancelDownload,
                                        playlist = playlistInfo
                                    )
                                }

                                val filteredSongs = if (uiState.searchQuery.isBlank()) {
                                    songs
                                } else {
                                    songs.filter { song ->
                                        song.title.contains(
                                            uiState.searchQuery,
                                            ignoreCase = true
                                        ) ||
                                                song.artist.contains(
                                                    uiState.searchQuery,
                                                    ignoreCase = true
                                                )
                                    }
                                }

                                if (uiState.searchQuery.isNotBlank() && filteredSongs.isEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.no_results),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp)
                                        )
                                    }
                                }

                                if (songs.isEmpty() && uiState.searchQuery.isBlank()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.empty_playlist_suggestions),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                        )
                                    }
                                }

                                items(
                                    items = filteredSongs,
                                    key = { song ->
                                        song.uid
                                    }
                                ) { song ->
                                    SongListItem(song, onPress = {
                                        onOpenPlayer()
                                        playlistViewModel.playPlaylist(song)
                                    }, playNext = {
                                        PlayerManager.addNext(song, application)
                                    }, addToQueue = {
                                        PlayerManager.addToQueue(
                                            song,
                                            application
                                        )
                                    }, download = {
                                        playlistViewModel.downloadSong(song)
                                    })
                                }

                                // Recommended Songs Section (Suggested Tracks)
                                if (uiState.searchQuery.isBlank() && (uiState.recommendedSongs.isNotEmpty() || uiState.isLoadingRecommendations)) {
                                    item {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = stringResource(R.string.recommended_for_playlist),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.recommended_for_playlist_subtitle),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            FilledIconButton(
                                                onClick = playlistViewModel::refreshRecommendations,
                                                shapes = IconButtonDefaults.shapes(),
                                                colors = IconButtonDefaults.filledIconButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                if (uiState.isLoadingRecommendations) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Refresh,
                                                        contentDescription = stringResource(R.string.refresh_recommendations),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    items(
                                        items = uiState.recommendedSongs,
                                        key = { song -> "rec_${song.youtubeId}_${song.uid}" }
                                    ) { song ->
                                        SongListItem(
                                            song,
                                            onPress = {
                                                onOpenPlayer()
                                                PlayerManager.playSong(song)
                                            },
                                            playNext = {
                                                PlayerManager.addNext(song, application)
                                            },
                                            addToQueue = {
                                                PlayerManager.addToQueue(song, application)
                                            },
                                            download = {
                                                playlistViewModel.downloadSong(song)
                                            }
                                        )
                                    }

                                    // Infinite scroll loading indicator / load more trigger
                                    if (uiState.showInfiniteSuggestions && uiState.recommendedSongs.isNotEmpty()) {
                                        item {
                                            if (uiState.isLoadingMoreRecommendations) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 20.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(22.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.size(12.dp))
                                                    Text(
                                                        text = stringResource(R.string.loading_more_suggestions),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            } else if (uiState.hasMoreRecommendations) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 12.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(
                                                        onClick = playlistViewModel::loadMoreRecommendations,
                                                        shapes = ButtonDefaults.shapes()
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.AutoAwesome,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.size(8.dp))
                                                        Text(stringResource(R.string.load_more_suggestions))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

