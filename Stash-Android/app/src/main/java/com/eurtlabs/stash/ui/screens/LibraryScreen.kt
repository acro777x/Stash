package com.eurtlabs.stash.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.eurtlabs.stash.data.model.DownloadBatch
import com.eurtlabs.stash.data.model.DownloadItem
import com.eurtlabs.stash.data.model.DownloadState
import com.eurtlabs.stash.data.model.TrackInfo
import com.eurtlabs.stash.data.model.UserPlaylist
import com.eurtlabs.stash.data.storage.PlaylistStore
import com.eurtlabs.stash.player.MusicPlayerManager
import com.eurtlabs.stash.ui.components.AnimatedHeartButton
import com.eurtlabs.stash.ui.components.AudioEqualizerVisualizer
import com.eurtlabs.stash.ui.components.LiquidGlassConfirmDialog
import com.eurtlabs.stash.ui.theme.LocalStashPalette
import com.eurtlabs.stash.util.ArtworkUtils
import java.io.File

enum class LibraryTab(val label: String) {
    ALL_TRACKS("All Tracks"),
    LIKED_SONGS("Liked Songs"),
    PLAYLISTS("Playlists"),
    DOWNLOADS("Downloads")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    batches: List<DownloadBatch>,
    onRemoveFromLibrary: (String) -> Unit = {},
    onDeleteFromDevice: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalStashPalette.current
    val context = LocalContext.current
    val playerState by MusicPlayerManager.playerState.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryTab.ALL_TRACKS) }
    var activePlaylistView by remember { mutableStateOf<UserPlaylist?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<UserPlaylist?>(null) }

    val likedTracks by PlaylistStore.likedTracks.collectAsState()
    val userPlaylists by PlaylistStore.userPlaylists.collectAsState()

    val sortModeState = remember { mutableStateOf(0) }
    val sortAscendingState = remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }

    val completedItems = batches.flatMap { it.items }
        .filter { it.state == DownloadState.COMPLETED && !it.finalFilePath.isNullOrBlank() }
        .let { items ->
            when (sortModeState.value) {
                1 -> if (sortAscendingState.value) items.sortedBy { it.trackInfo.title.lowercase() } else items.sortedByDescending { it.trackInfo.title.lowercase() }
                2 -> if (sortAscendingState.value) items.sortedBy { File(it.finalFilePath ?: "").length() } else items.sortedByDescending { File(it.finalFilePath ?: "").length() }
                else -> if (sortAscendingState.value) items else items.reversed()
            }
        }

    // Delete Song Confirmation Dialog
    if (itemToDelete != null) {
        val target = itemToDelete!!
        LiquidGlassConfirmDialog(
            title = "Delete Downloaded Song?",
            message = "Are you sure you want to remove this track from your library and delete the audio file from device storage?",
            confirmText = "Delete",
            cancelText = "Cancel",
            onConfirm = {
                onDeleteFromDevice(target.id)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null }
        )
    }

    // Delete Playlist Confirmation Dialog (Liquid Glass Theme)
    if (playlistToDelete != null) {
        val targetPlaylist = playlistToDelete!!
        LiquidGlassConfirmDialog(
            title = "Delete Playlist?",
            message = "Are you sure you want to delete '${targetPlaylist.name}'? Songs inside this playlist will remain safe in your library.",
            confirmText = "Delete",
            cancelText = "Cancel",
            onConfirm = {
                PlaylistStore.deletePlaylist(targetPlaylist.id)
                if (activePlaylistView?.id == targetPlaylist.id) {
                    activePlaylistView = null
                }
                playlistToDelete = null
            },
            onDismiss = { playlistToDelete = null }
        )
    }

    // Create Playlist Modal Dialog (Liquid Glass Theme)
    if (showCreatePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        var playlistDesc by remember { mutableStateOf("") }

        BasicAlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(palette.surfaceVariant.copy(alpha = 0.98f), palette.surface.copy(alpha = 0.96f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(26.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Create New Playlist",
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name", color = palette.textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = playlistDesc,
                    onValueChange = { playlistDesc = it },
                    label = { Text("Description (Optional)", color = palette.textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel",
                        color = palette.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { showCreatePlaylistDialog = false }
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (playlistName.isNotBlank()) palette.primary else palette.primary.copy(alpha = 0.35f))
                            .clickable(enabled = playlistName.isNotBlank()) {
                                PlaylistStore.createPlaylist(playlistName.trim(), playlistDesc.trim())
                                showCreatePlaylistDialog = false
                            }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "Create",
                            color = palette.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Active Playlist Detail View
    if (activePlaylistView != null) {
        val playlist = activePlaylistView!!
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { activePlaylistView = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = playlist.name,
                            color = palette.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Delete Playlist Top Action Button (Liquid Glass)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.error.copy(alpha = 0.15f))
                            .border(0.8.dp, palette.error.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                            .clickable { playlistToDelete = playlist }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = palette.error, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", color = palette.error, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    palette.surfaceVariant.copy(alpha = 0.95f),
                                    palette.surface.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Column {
                        Text(text = playlist.name, color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        if (playlist.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = playlist.description, color = palette.textSecondary, fontSize = 12.5.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "${playlist.tracks.size} tracks", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play All
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.primary)
                                    .clickable(enabled = playlist.tracks.isNotEmpty()) {
                                        MusicPlayerManager.playQueue(playlist.tracks, 0)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = palette.onPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play All", color = palette.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Shuffle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.surfaceVariant)
                                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                                    .clickable(enabled = playlist.tracks.isNotEmpty()) {
                                        if (playlist.tracks.isNotEmpty()) {
                                            val shuffled = playlist.tracks.shuffled()
                                            MusicPlayerManager.playQueue(shuffled, 0)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Shuffle", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (playlist.tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Playlist is Empty", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Add songs from Search or Discover to build this playlist.", color = palette.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                itemsIndexed(playlist.tracks) { index, track ->
                    val isPlaying = playerState.currentTrack?.id == track.id
                    TrackRowItem(
                        track = track,
                        isPlaying = isPlaying,
                        onClick = { MusicPlayerManager.playQueue(playlist.tracks, index) },
                        onRemove = {
                            PlaylistStore.removeTrackFromPlaylist(playlist.id, track.id)
                            activePlaylistView = PlaylistStore.userPlaylists.value.find { it.id == playlist.id }
                        }
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp, top = 6.dp)
    ) {
        // Top Header Row with Persistent "+ New Playlist" Action
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIBRARY",
                        color = palette.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${likedTracks.size} Liked • ${userPlaylists.size} Playlists • ${completedItems.size} Saved",
                        color = palette.textSecondary,
                        fontSize = 11.5.sp
                    )
                }

                // Persistent "+ New Playlist" Header Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceVariant)
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                        .clickable { showCreatePlaylistDialog = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = palette.primary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Playlist",
                            color = palette.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Category Pills (Liquid Glass)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedTab = tab }
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
                                color = if (isSelected) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val count = when (tab) {
                            LibraryTab.ALL_TRACKS -> completedItems.size + likedTracks.size
                            LibraryTab.LIKED_SONGS -> likedTracks.size
                            LibraryTab.PLAYLISTS -> userPlaylists.size
                            LibraryTab.DOWNLOADS -> completedItems.size
                        }
                        Text(
                            text = "${tab.label} ($count)",
                            color = if (isSelected) palette.onPrimary else palette.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }

        // Content Based on Selected Tab
        when (selectedTab) {
            LibraryTab.ALL_TRACKS -> {
                // Quick Dashboard Cards Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Liked Songs Mini Hero
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFFFF3366).copy(alpha = 0.22f),
                                            palette.surfaceVariant.copy(alpha = 0.70f)
                                        )
                                    )
                                )
                                .border(1.dp, Color(0xFFFF3366).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                .clickable { selectedTab = LibraryTab.LIKED_SONGS }
                                .padding(14.dp)
                        ) {
                            Column {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF3366), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Liked Songs", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("${likedTracks.size} tracks", color = palette.textSecondary, fontSize = 11.sp)
                            }
                        }

                        // Create Playlist Mini Hero
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            palette.surfaceVariant.copy(alpha = 0.90f),
                                            palette.surface.copy(alpha = 0.70f)
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                                .clickable { showCreatePlaylistDialog = true }
                                .padding(14.dp)
                        ) {
                            Column {
                                Icon(Icons.Default.Add, contentDescription = null, tint = palette.primary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("New Playlist", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Create collection", color = palette.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Your Playlists Quick Shelf (Obsidian Glass Cards)
                if (userPlaylists.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "YOUR PLAYLISTS (${userPlaylists.size})",
                                color = palette.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "See All",
                                color = palette.primary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { selectedTab = LibraryTab.PLAYLISTS }
                                    .padding(4.dp)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            userPlaylists.forEach { playlist ->
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    palette.surfaceVariant.copy(alpha = 0.85f),
                                                    palette.surface.copy(alpha = 0.65f)
                                                )
                                            )
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                        .clickable { activePlaylistView = playlist }
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(palette.primary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = null,
                                                tint = palette.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = playlist.name,
                                            color = palette.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.tracks.size} songs",
                                            color = palette.textSecondary,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Header for Tracks
                item {
                    Text(
                        text = "RECENT TRACKS (${completedItems.size})",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                if (completedItems.isEmpty()) {
                    item {
                        EmptyLibraryState(palette, onExplore = {})
                    }
                } else {
                    items(completedItems) { item ->
                        val isPlaying = playerState.currentTrack?.id == item.trackInfo.id
                        LibraryTrackRow(
                            item = item,
                            isPlaying = isPlaying,
                            palette = palette,
                            onPlay = {
                                val queue = completedItems.map { it.trackInfo }
                                val idx = completedItems.indexOf(item)
                                MusicPlayerManager.playQueue(queue, maxOf(0, idx))
                            },
                            onDelete = { itemToDelete = item },
                            onShare = { shareAudioFile(context, item) }
                        )
                    }
                }
            }

            LibraryTab.LIKED_SONGS -> {
                // Liked Songs Hero Banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFFFF3366).copy(alpha = 0.25f),
                                        palette.surfaceVariant.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFFFF3366).copy(alpha = 0.40f), RoundedCornerShape(24.dp))
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF3366), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Liked Songs", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${likedTracks.size} favorite songs saved locally",
                                    color = palette.textSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            if (likedTracks.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Play All
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(palette.primary)
                                            .clickable { MusicPlayerManager.playQueue(likedTracks, 0) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = palette.onPrimary, modifier = Modifier.size(22.dp))
                                    }

                                    // Shuffle
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(palette.surfaceVariant)
                                            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                                            .clickable {
                                                val shuffled = likedTracks.shuffled()
                                                MusicPlayerManager.playQueue(shuffled, 0)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (likedTracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF3366).copy(alpha = 0.5f), modifier = Modifier.size(42.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No Liked Songs Yet", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tap the heart icon on any playing song or search result to save it to your favorites.", color = palette.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    itemsIndexed(likedTracks) { index, track ->
                        val isPlaying = playerState.currentTrack?.id == track.id
                        TrackRowItem(
                            track = track,
                            isPlaying = isPlaying,
                            onClick = { MusicPlayerManager.playQueue(likedTracks, index) },
                            onRemove = { PlaylistStore.toggleLike(track) }
                        )
                    }
                }
            }

            LibraryTab.PLAYLISTS -> {
                // Prominent Hero Creation Card in Playlists Tab
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        palette.surfaceVariant.copy(alpha = 0.95f),
                                        palette.surface.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
                            .clickable { showCreatePlaylistDialog = true }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(palette.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = palette.onPrimary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Create New Playlist", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Organize songs into custom collections", color = palette.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "YOUR PLAYLISTS (${userPlaylists.size})",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                if (userPlaylists.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("No Custom Playlists", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Create your first custom playlist above to organize your music.", color = palette.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(userPlaylists) { playlist ->
                        PlaylistCardItem(
                            playlist = playlist,
                            onClick = { activePlaylistView = playlist },
                            onDelete = { playlistToDelete = playlist }
                        )
                    }
                }
            }

            LibraryTab.DOWNLOADS -> {
                if (completedItems.isEmpty()) {
                    item {
                        EmptyLibraryState(palette, onExplore = {})
                    }
                } else {
                    items(completedItems) { item ->
                        val isPlaying = playerState.currentTrack?.id == item.trackInfo.id
                        LibraryTrackRow(
                            item = item,
                            isPlaying = isPlaying,
                            palette = palette,
                            onPlay = {
                                val queue = completedItems.map { it.trackInfo }
                                val idx = completedItems.indexOf(item)
                                MusicPlayerManager.playQueue(queue, maxOf(0, idx))
                            },
                            onDelete = { itemToDelete = item },
                            onShare = { shareAudioFile(context, item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRowItem(
    track: TrackInfo,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val palette = LocalStashPalette.current
    val artUrl = ArtworkUtils.getHighResArtworkUrl(track.albumArtUrl, track.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    if (isPlaying) {
                        listOf(palette.primary.copy(alpha = 0.15f), palette.surfaceVariant.copy(alpha = 0.50f))
                    } else {
                        listOf(palette.surfaceVariant.copy(alpha = 0.70f), palette.surface.copy(alpha = 0.50f))
                    }
                )
            )
            .border(
                1.dp,
                if (isPlaying) palette.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceVariant),
            contentAlignment = Alignment.Center
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
                        .background(Color.Black.copy(alpha = 0.40f)),
                    contentAlignment = Alignment.Center
                ) {
                    AudioEqualizerVisualizer(tint = palette.primary, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlaying) palette.primary else palette.textPrimary,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artists.joinToString(", ").ifEmpty { "Unknown Artist" },
                color = palette.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Liked Heart Toggle Button with Spring Bounce Animation
        AnimatedHeartButton(
            isLiked = true,
            onToggleLike = onRemove,
            iconSize = 20.dp,
            containerSize = 36.dp
        )
    }
}

@Composable
private fun PlaylistCardItem(
    playlist: UserPlaylist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalStashPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.surfaceVariant.copy(alpha = 0.85f),
                        palette.surface.copy(alpha = 0.60f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(palette.primary.copy(alpha = 0.20f), palette.surfaceVariant)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = palette.primary, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = palette.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${playlist.tracks.size} songs",
                color = palette.textSecondary,
                fontSize = 12.sp
            )
        }

        // Dedicated Liquid Glass Delete Button (Crimson Frost)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(palette.error.copy(alpha = 0.12f))
                .border(0.8.dp, palette.error.copy(alpha = 0.30f), CircleShape)
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Playlist",
                tint = palette.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LibraryTrackRow(
    item: DownloadItem,
    isPlaying: Boolean,
    palette: com.eurtlabs.stash.ui.theme.ThemePalette,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val artUrl = ArtworkUtils.getHighResArtworkUrl(item.trackInfo.albumArtUrl, item.trackInfo.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    if (isPlaying) {
                        listOf(palette.primary.copy(alpha = 0.15f), palette.surfaceVariant.copy(alpha = 0.50f))
                    } else {
                        listOf(palette.surfaceVariant.copy(alpha = 0.70f), palette.surface.copy(alpha = 0.45f))
                    }
                )
            )
            .border(
                1.dp,
                if (isPlaying) palette.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(18.dp)
            )
            .clickable { onPlay() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = artUrl,
                contentDescription = item.trackInfo.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.40f)),
                    contentAlignment = Alignment.Center
                ) {
                    AudioEqualizerVisualizer(tint = palette.primary, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.trackInfo.title,
                color = if (isPlaying) palette.primary else palette.textPrimary,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.trackInfo.artists.joinToString(", ").ifEmpty { "Unknown Artist" },
                color = palette.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Options dropdown (Share / Delete)
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.surfaceVariant.copy(alpha = 0.96f),
                                palette.surface.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .border(1.2.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Share Audio File", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                    onClick = {
                        showMenu = false
                        onShare()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Share, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete from Device", color = palette.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = palette.error, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(palette: com.eurtlabs.stash.ui.theme.ThemePalette, onExplore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(42.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No Downloaded Tracks Yet", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Downloaded audio files and albums will appear here for offline playback.", color = palette.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun shareAudioFile(context: android.content.Context, item: DownloadItem) {
    val path = item.finalFilePath ?: return
    val file = File(path)
    if (!file.exists()) return

    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Audio Track"))
    } catch (e: Exception) {
        // Ignore
    }
}
