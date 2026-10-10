package ca.ilianokokoro.umihi.music.ui.components.bottomsheet

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.models.BlockedArtist
import ca.ilianokokoro.umihi.music.models.BlockedKeyword
import ca.ilianokokoro.umihi.music.ui.components.SheetHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedContentBottomSheet(
    blockedArtists: List<BlockedArtist>,
    blockedKeywords: List<BlockedKeyword>,
    onBlockArtist: (String) -> Unit,
    onUnblockArtist: (String) -> Unit,
    onBlockKeyword: (String) -> Unit,
    onUnblockKeyword: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val handler = remember { Handler(Looper.getMainLooper()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = {
            handler.post { onDismiss() }
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SheetHeader(
                icon = Icons.Rounded.Block,
                title = stringResource(R.string.blocked_content_title),
                modifier = Modifier.padding(bottom = 8.dp),
            )

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; inputText = "" },
                    text = { Text(stringResource(R.string.blocked_artists_tab)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; inputText = "" },
                    text = { Text(stringResource(R.string.blocked_keywords_tab)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input section to add new blocked artist/keyword
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (selectedTab == 0) stringResource(R.string.artist_hint)
                            else stringResource(R.string.keyword_hint)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotBlank()) {
                            if (selectedTab == 0) {
                                onBlockArtist(trimmed)
                            } else {
                                onBlockKeyword(trimmed)
                            }
                            inputText = ""
                        }
                    },
                    enabled = inputText.trim().isNotBlank()
                ) {
                    Text(stringResource(R.string.add))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // List of blocked items
            if (selectedTab == 0) {
                if (blockedArtists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_blocked_artists),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = blockedArtists,
                            key = { it.name }
                        ) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onUnblockArtist(artist.name) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.unblock),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (blockedKeywords.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_blocked_keywords),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = blockedKeywords,
                            key = { it.keyword }
                        ) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.keyword,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onUnblockKeyword(item.keyword) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.unblock),
                                        tint = MaterialTheme.colorScheme.error
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
