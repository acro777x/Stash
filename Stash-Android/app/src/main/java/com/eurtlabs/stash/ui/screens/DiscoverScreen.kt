package com.eurtlabs.stash.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eurtlabs.stash.data.model.SearchResultItem
import com.eurtlabs.stash.player.MusicPlayerManager
import com.eurtlabs.stash.ui.components.AudioEqualizerVisualizer
import com.eurtlabs.stash.ui.theme.LocalStashPalette
import com.eurtlabs.stash.util.ArtworkUtils

data class MoodStation(val title: String, val subtitle: String, val query: String, val gradientColors: List<Color>)
data class FeaturedArtist(val name: String, val genre: String, val imageUrl: String)

@Composable
fun DiscoverScreen(
    quickPicks: List<SearchResultItem>,
    trendingHits: List<SearchResultItem>,
    topCharts: List<SearchResultItem> = emptyList(),
    newReleases: List<SearchResultItem> = emptyList(),
    isLoading: Boolean,
    selectedMood: String,
    activeDownloadsMap: Map<String, Float> = emptyMap(),
    downloadedTrackIds: Set<String> = emptySet(),
    onSelectMood: (String) -> Unit,
    onPlayTrack: (SearchResultItem, List<SearchResultItem>) -> Unit,
    onDownloadTrack: (SearchResultItem) -> Unit,
    onSearchArtist: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalStashPalette.current
    val playerState by MusicPlayerManager.playerState.collectAsState()

    val moods = listOf(
        "Top Hits", "Relax", "Energize", "Focus", "Workout",
        "Commute", "Party", "Lo-Fi", "Pop", "Rock",
        "Electronic", "Acoustic", "Hip-Hop", "Indie"
    )

    val moodStations = listOf(
        MoodStation("Deep Focus", "Instrumental Beats", "Focus", listOf(Color(0xFF131C2E), Color(0xFF1E293B))),
        MoodStation("Late Night Lo-Fi", "Chill & Study", "Lo-Fi", listOf(Color(0xFF1F172E), Color(0xFF2E2442))),
        MoodStation("Workout Power", "High Energy Hype", "Workout", listOf(Color(0xFF2A1520), Color(0xFF3B1E2E))),
        MoodStation("Party Wave", "Dance & Electronic", "Party", listOf(Color(0xFF2B1D12), Color(0xFF3D2A1C))),
        MoodStation("Chill Acoustic", "Warm Vibes", "Acoustic", listOf(Color(0xFF12221A), Color(0xFF1B3327)))
    )

    val featuredArtists = listOf(
        FeaturedArtist("The Weeknd", "R&B / Pop", "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg"),
        FeaturedArtist("Ed Sheeran", "Pop / Acoustic", "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg"),
        FeaturedArtist("Taylor Swift", "Pop / Country", "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg"),
        FeaturedArtist("Dua Lipa", "Dance Pop", "https://i.ytimg.com/vi/CevxZvSJLk8/hqdefault.jpg"),
        FeaturedArtist("Lofi Girl", "Chill Lo-Fi", "https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg")
    )

    if (quickPicks.isEmpty() && isLoading) {
        com.eurtlabs.stash.ui.components.DiscoverScreenSkeleton(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
    ) {
        // Filter Chips Row (Mood Pills)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(moods) { mood ->
                    val isSelected = selectedMood.equals(mood, ignoreCase = true) || (mood == "Top Hits" && (selectedMood == "All" || selectedMood == "Top Hits"))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) {
                                    Brush.verticalGradient(
                                        listOf(palette.primary, palette.primary.copy(alpha = 0.85f))
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(palette.surface.copy(alpha = 0.90f), palette.surfaceVariant.copy(alpha = 0.65f))
                                    )
                                }
                            )
                            .border(
                                width = if (isSelected) 1.2.dp else 1.dp,
                                brush = Brush.verticalGradient(
                                    if (isSelected) listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.15f))
                                    else listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable(
                                interactionSource = remember(mood) { MutableInteractionSource() },
                                indication = null
                            ) { onSelectMood(mood) }
                            .padding(horizontal = 16.dp, vertical = 7.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mood,
                            color = if (isSelected) palette.onPrimary else palette.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Now Playing / Your Vibe Glass Card
        if (playerState.currentTrack != null) {
            item {
                val currentTrack = playerState.currentTrack!!
                val artUrl = currentTrack.albumArtUrl ?: ""

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    palette.surfaceVariant.copy(alpha = 0.92f),
                                    palette.primary.copy(alpha = 0.22f),
                                    palette.surface.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(palette.primary.copy(alpha = 0.65f), Color.White.copy(alpha = 0.15f))
                            ),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = artUrl,
                                contentDescription = currentTrack.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (playerState.isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AudioEqualizerVisualizer(
                                        tint = palette.primary,
                                        modifier = Modifier.size(24.dp, 16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(palette.primary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (playerState.isPlaying) "NOW PLAYING" else "PAUSED",
                                    color = palette.primary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentTrack.title,
                                color = palette.textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack.artists.firstOrNull() ?: "",
                                color = palette.textSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { MusicPlayerManager.togglePlayPause() },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = palette.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Section: Quick Picks
        if (quickPicks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = palette.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "QUICK PICKS",
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickPicks) { track ->
                        val isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying
                        val downloadProgress = activeDownloadsMap[track.id]
                        val isDownloaded = downloadedTrackIds.contains(track.id)

                        QuickPickCard(
                            track = track,
                            isPlaying = isPlaying,
                            downloadProgress = downloadProgress,
                            isDownloaded = isDownloaded,
                            onPlay = { onPlayTrack(track, quickPicks) },
                            onDownload = { onDownloadTrack(track) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Section: Mood & Energy Stations
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "MOOD & ENERGY STATIONS",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(moodStations) { station ->
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(84.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.horizontalGradient(station.gradientColors))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                            .clickable { onSelectMood(station.query) }
                            .padding(12.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            Text(
                                text = station.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = station.subtitle,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Top Charts Worldwide
        val chartsToDisplay = topCharts.ifEmpty { trendingHits.take(6) }
        if (chartsToDisplay.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "TOP CHARTS WORLDWIDE",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            itemsIndexed(chartsToDisplay) { index, track ->
                val isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying
                val downloadProgress = activeDownloadsMap[track.id]
                val isDownloaded = downloadedTrackIds.contains(track.id)

                TrendingTrackRow(
                    index = index + 1,
                    track = track,
                    isPlaying = isPlaying,
                    downloadProgress = downloadProgress,
                    isDownloaded = isDownloaded,
                    onPlay = { onPlayTrack(track, chartsToDisplay) },
                    onDownload = { onDownloadTrack(track) }
                )
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        // Section: New Releases
        val releasesToDisplay = newReleases.ifEmpty { trendingHits.drop(6) }
        if (releasesToDisplay.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "NEW RELEASES",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(releasesToDisplay) { track ->
                        val isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying
                        val downloadProgress = activeDownloadsMap[track.id]
                        val isDownloaded = downloadedTrackIds.contains(track.id)

                        QuickPickCard(
                            track = track,
                            isPlaying = isPlaying,
                            downloadProgress = downloadProgress,
                            isDownloaded = isDownloaded,
                            onPlay = { onPlayTrack(track, releasesToDisplay) },
                            onDownload = { onDownloadTrack(track) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Section: Featured Global Artists
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "FEATURED ARTISTS",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(featuredArtists) { artist ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onSearchArtist(artist.name) }
                            .width(86.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(palette.surfaceVariant)
                                .border(1.2.dp, palette.primary.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = artist.imageUrl,
                                contentDescription = artist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = artist.name,
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist.genre,
                            color = palette.textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickCard(
    track: SearchResultItem,
    isPlaying: Boolean,
    downloadProgress: Float?,
    isDownloaded: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val palette = LocalStashPalette.current
    val artUrl = ArtworkUtils.getHighResArtworkUrl(track.thumbnailUrl, track.id)

    Column(
        modifier = Modifier
            .width(152.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.surfaceVariant.copy(alpha = 0.92f),
                        palette.surface.copy(alpha = 0.72f)
                    )
                )
            )
            .border(
                width = if (isPlaying) 1.5.dp else 1.dp,
                brush = Brush.verticalGradient(
                    if (isPlaying) listOf(palette.primary, palette.primary.copy(alpha = 0.4f))
                    else listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = remember(track.id) { MutableInteractionSource() },
                indication = null
            ) { onPlay() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(palette.surfaceVariant)
        ) {
            AsyncImage(
                model = artUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette.primary)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    AudioEqualizerVisualizer(tint = palette.onPrimary)
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = palette.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            color = if (isPlaying) palette.primary else palette.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = track.artist,
                color = palette.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            DownloadStatusWidget(
                progress = downloadProgress,
                isDownloaded = isDownloaded,
                onDownload = onDownload
            )
        }
    }
}

@Composable
private fun TrendingTrackRow(
    index: Int,
    track: SearchResultItem,
    isPlaying: Boolean,
    downloadProgress: Float?,
    isDownloaded: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val palette = LocalStashPalette.current
    val artUrl = ArtworkUtils.getHighResArtworkUrl(track.thumbnailUrl, track.id)

    val rankColor = when (index) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> palette.textSecondary.copy(alpha = 0.6f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    if (isPlaying) listOf(palette.primary.copy(alpha = 0.16f), palette.surfaceVariant.copy(alpha = 0.90f))
                    else listOf(palette.surfaceVariant.copy(alpha = 0.85f), palette.surface.copy(alpha = 0.65f))
                )
            )
            .border(
                1.dp,
                if (isPlaying) palette.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = remember(track.id) { MutableInteractionSource() },
                indication = null
            ) { onPlay() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%02d".format(index),
            color = rankColor,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            modifier = Modifier.width(26.dp)
        )

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = artUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    AudioEqualizerVisualizer(tint = palette.primary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlaying) palette.primary else palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = palette.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        DownloadStatusWidget(
            progress = downloadProgress,
            isDownloaded = isDownloaded,
            onDownload = onDownload
        )
    }
}

@Composable
fun DownloadStatusWidget(
    progress: Float?,
    isDownloaded: Boolean,
    onDownload: () -> Unit
) {
    val palette = LocalStashPalette.current

    if (isDownloaded) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(palette.primary.copy(alpha = 0.18f))
                .border(1.dp, palette.primary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Saved",
                    tint = palette.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SAVED",
                    color = palette.primary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    } else if (progress != null) {
        val pct = (progress * 100).toInt().coerceIn(0, 100)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surfaceVariant)
                .border(1.dp, palette.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(12.dp),
                    color = palette.primary,
                    strokeWidth = 2.dp,
                    trackColor = palette.primary.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "$pct%",
                    color = palette.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(palette.surfaceVariant, palette.surface)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                .clickable { onDownload() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download Track",
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
