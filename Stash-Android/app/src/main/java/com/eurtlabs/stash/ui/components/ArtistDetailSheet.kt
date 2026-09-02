package com.eurtlabs.stash.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eurtlabs.stash.data.downloader.InnerTubeMusicRepository
import com.eurtlabs.stash.data.model.SearchFilter
import com.eurtlabs.stash.data.model.SearchResultItem
import com.eurtlabs.stash.player.MusicPlayerManager
import com.eurtlabs.stash.ui.theme.LocalStashPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailSheet(
    artistName: String,
    artistImageUrl: String? = null,
    onDismiss: () -> Unit,
    onPlayTrack: (SearchResultItem, List<SearchResultItem>) -> Unit,
    onDownloadTrack: (SearchResultItem) -> Unit,
    activeDownloadsMap: Map<String, Float> = emptyMap(),
    downloadedTrackIds: Set<String> = emptySet()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val palette = LocalStashPalette.current
    val playerState by MusicPlayerManager.playerState.collectAsState()

    var topSongs by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(artistName) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val songs = InnerTubeMusicRepository.search("$artistName songs", SearchFilter.MUSIC)
            withContext(Dispatchers.Main) {
                topSongs = songs.take(25)
                isLoading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background,
        contentColor = palette.textPrimary,
        dragHandle = null,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Ambient Backdrop Halo
            if (!artistImageUrl.isNullOrBlank() || topSongs.firstOrNull()?.thumbnailUrl != null) {
                val haloUrl = artistImageUrl ?: topSongs.firstOrNull()?.thumbnailUrl ?: ""
                AsyncImage(
                    model = haloUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(90.dp)
                        .scale(1.4f)
                        .background(palette.background.copy(alpha = 0.55f))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.background.copy(alpha = 0.80f),
                                palette.background.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                // Top Close Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ARTIST PROFILE",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Artist Header Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(palette.surfaceVariant.copy(alpha = 0.85f), palette.surface.copy(alpha = 0.60f))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular Avatar
                    val avatarUrl = artistImageUrl ?: topSongs.firstOrNull()?.thumbnailUrl
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceVariant)
                            .border(1.5.dp, palette.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = artistName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(36.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = artistName,
                                color = palette.textPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = palette.primary,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Verified Official Artist",
                            color = palette.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Play Radio Action Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(palette.primary)
                                .clickable {
                                    if (topSongs.isNotEmpty()) {
                                        onPlayTrack(topSongs.first(), topSongs)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = palette.onPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play Radio", color = palette.onPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "TOP DISCOGRAPHY & SONGS (${topSongs.size})",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.primary, modifier = Modifier.size(36.dp), strokeWidth = 2.5.dp)
                    }
                } else if (topSongs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No songs found for this artist.", color = palette.textSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 30.dp)
                    ) {
                        itemsIndexed(topSongs, key = { _, item -> item.id }) { index, item ->
                            val isCurrentTrackPlaying = playerState.currentTrack?.id == item.id && playerState.isPlaying
                            val downloadProgress = activeDownloadsMap[item.id]
                            val isDownloaded = downloadedTrackIds.contains(item.id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isCurrentTrackPlaying) {
                                            Brush.verticalGradient(listOf(palette.primary.copy(alpha = 0.22f), palette.primary.copy(alpha = 0.08f)))
                                        } else {
                                            Brush.verticalGradient(listOf(palette.surfaceVariant.copy(alpha = 0.40f), palette.surface.copy(alpha = 0.20f)))
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (isCurrentTrackPlaying) palette.primary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onPlayTrack(item, topSongs) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Index or Equalizer
                                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    if (isCurrentTrackPlaying) {
                                        AudioEqualizerVisualizer(tint = palette.primary, modifier = Modifier.size(16.dp))
                                    } else {
                                        Text(text = "${index + 1}", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Artwork
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = if (isCurrentTrackPlaying) palette.primary else palette.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.artist,
                                        color = palette.textSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Download Action
                                if (downloadProgress != null) {
                                    CircularProgressIndicator(
                                        progress = { (downloadProgress / 100f).coerceIn(0.05f, 1f) },
                                        color = palette.primary,
                                        trackColor = palette.primary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else if (isDownloaded) {
                                    Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded", tint = palette.primary, modifier = Modifier.size(20.dp))
                                } else {
                                    IconButton(
                                        onClick = { onDownloadTrack(item) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Download", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
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
