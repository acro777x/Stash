package com.eurtlabs.stash.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eurtlabs.stash.data.model.PlaybackRepeatMode
import com.eurtlabs.stash.data.model.TrackInfo
import com.eurtlabs.stash.data.storage.PlaylistStore
import com.eurtlabs.stash.player.MusicPlayerManager
import com.eurtlabs.stash.player.PlayerState
import com.eurtlabs.stash.ui.theme.LocalStashPalette
import com.eurtlabs.stash.util.ArtworkUtils
import kotlinx.coroutines.launch

data class TikTokHeart(
    val id: Long = System.currentTimeMillis() + (0..9999).random(),
    val x: Float,
    val y: Float,
    val color: Color = Color(0xFFFF1493),
    val size: Dp = 115.dp
)

@Composable
fun TikTokPopHeart(
    heart: TikTokHeart,
    onFinished: () -> Unit
) {
    val scaleAnim = remember { Animatable(0.15f) }
    val alphaAnim = remember { Animatable(1f) }
    val floatAnim = remember { Animatable(0f) }

    LaunchedEffect(heart.id) {
        // Snappy spring pop
        launch {
            scaleAnim.animateTo(
                targetValue = 1.25f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            scaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        // Gentle float upwards
        launch {
            floatAnim.animateTo(
                targetValue = -90f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
            )
        }
        // Alpha holds solid then smoothly fades away
        launch {
            delay(420)
            alphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 330, easing = LinearEasing)
            )
            onFinished()
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val halfSizePx = with(density) { (heart.size / 2).toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (heart.x - halfSizePx).toInt(),
                        y = (heart.y - halfSizePx + floatAnim.value).toInt()
                    )
                }
                .size(heart.size),
            contentAlignment = Alignment.Center
        ) {
            // Radiant Pink Glow Halo Aura
            Box(
                modifier = Modifier
                    .size(heart.size * 1.55f)
                    .graphicsLayer {
                        scaleX = scaleAnim.value * 1.15f
                        scaleY = scaleAnim.value * 1.15f
                        this.alpha = alphaAnim.value * 0.65f
                    }
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                heart.color.copy(alpha = 0.70f),
                                Color(0xFFFF69B4).copy(alpha = 0.30f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // 4 Micro Sparkle Stars (✦) expanding outward symmetrically
            val sparkleOffsets = listOf(
                IntOffset(-42, -42),
                IntOffset(42, -42),
                IntOffset(-42, 42),
                IntOffset(42, 42)
            )
            sparkleOffsets.forEach { offset ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (offset.x * (scaleAnim.value - 0.2f)).toInt(),
                                (offset.y * (scaleAnim.value - 0.2f)).toInt()
                            )
                        }
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = alphaAnim.value * 0.9f))
                )
            }

            // Big Bold Hero Pink Heart
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = heart.color,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        this.alpha = alphaAnim.value
                    }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullPlayerSheet(
    onDismiss: () -> Unit,
    onDeleteTrack: (String) -> Unit = {},
    onDownloadTrack: (TrackInfo) -> Unit = {},
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val palette = LocalStashPalette.current
    val playerState by MusicPlayerManager.playerState.collectAsState()
    val track = playerState.currentTrack
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val coroutineScope = rememberCoroutineScope()

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderTempPos by remember { mutableStateOf(0f) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val highResArt = ArtworkUtils.getHighResArtworkUrl(track?.albumArtUrl, track?.id)
    val isLocalSong = !playerState.isStreaming || isDownloaded || playerState.currentItem != null

    BackHandler(enabled = true) {
        coroutineScope.launch {
            try {
                sheetState.hide()
            } finally {
                onDismiss()
            }
        }
    }

    if (showDeleteConfirmDialog && track != null) {
        LiquidGlassConfirmDialog(
            title = "Delete Downloaded Song?",
            message = "Are you sure you want to remove this track from your library and delete the audio file from device storage?",
            confirmText = "Delete",
            cancelText = "Cancel",
            onConfirm = {
                showDeleteConfirmDialog = false
                onDeleteTrack(track.id)
                MusicPlayerManager.stop()
                onDismiss()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
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
            if (highResArt.isNotBlank()) {
                AsyncImage(
                    model = highResArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(85.dp)
                        .scale(1.35f)
                        .alpha(0.35f)
                )
            }

            // Dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.background.copy(alpha = 0.70f),
                                palette.background.copy(alpha = 0.88f),
                                palette.background.copy(alpha = 0.96f)
                            )
                        )
                    )
            )

            // Main Container
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    sheetState.hide()
                                } finally {
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = if (isLocalSong) "OFFLINE LIBRARY" else "ONLINE STREAMING",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (track != null) {
                            if (isLocalSong) {
                                IconButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete from Device",
                                        tint = palette.error.copy(alpha = 0.90f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                if (isDownloading) {
                                    Box(
                                        modifier = Modifier.size(38.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { if (downloadProgress > 0f) (downloadProgress / 100f).coerceIn(0.05f, 1f) else 0.5f },
                                            color = palette.primary,
                                            trackColor = palette.primary.copy(alpha = 0.20f),
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = if (downloadProgress > 0f) "${downloadProgress.toInt()}%" else "...",
                                            color = palette.primary,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { onDownloadTrack(track) },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download Song",
                                            tint = palette.primary,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                val nextTimer = when (playerState.sleepTimerRemainingSeconds) {
                                    0 -> 15
                                    15 * 60 -> 30
                                    30 * 60 -> 60
                                    else -> 0
                                }
                                MusicPlayerManager.setSleepTimer(nextTimer)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = "Sleep Timer",
                                    tint = if (playerState.sleepTimerRemainingSeconds > 0) palette.primary else palette.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (playerState.sleepTimerRemainingSeconds > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(palette.primary)
                                    )
                                }
                            }
                        }
                    }
                }

                // Synchronized Segmented Glass 4-Tab Switcher
                val tabTitles = listOf("Track", "Up Next", "Lyrics", "Audio FX")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    palette.surfaceVariant.copy(alpha = 0.85f),
                                    palette.surface.copy(alpha = 0.65f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isSelected) palette.primary else Color.Transparent)
                                    .clickable(
                                        interactionSource = remember(index) { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) palette.onPrimary else palette.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Swipeable Horizontal Pager (0: Track, 1: Up Next Queue, 2: Lyrics, 3: Audio FX)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> {
                            NowPlayingView(
                                track = track,
                                playerState = playerState,
                                isDraggingSlider = isDraggingSlider,
                                sliderTempPos = sliderTempPos,
                                onSliderValueChange = {
                                    isDraggingSlider = true
                                    sliderTempPos = it
                                },
                                onSliderValueChangeFinished = {
                                    isDraggingSlider = false
                                    MusicPlayerManager.seekTo(sliderTempPos.toLong())
                                }
                            )
                        }
                        1 -> {
                            QueueView(
                                playerState = playerState,
                                onTrackSelect = { index -> MusicPlayerManager.playTrackFromQueue(index) }
                            )
                        }
                        2 -> {
                            SyncedLyricsView(
                                playerState = playerState,
                                onSeekTo = { MusicPlayerManager.seekTo(it) }
                            )
                        }
                        3 -> {
                            EqualizerFxView()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingView(
    track: TrackInfo?,
    playerState: PlayerState,
    isDraggingSlider: Boolean,
    sliderTempPos: Float,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit
) {
    val palette = LocalStashPalette.current
    val haptic = LocalHapticFeedback.current
    var heartBursts by remember { mutableStateOf<List<TikTokHeart>>(emptyList()) }

    val currentMs = if (isDraggingSlider) sliderTempPos.toLong() else playerState.currentPositionMs
    val durationMs = if (playerState.durationMs > 0) playerState.durationMs else 1L
    val highResArt = ArtworkUtils.getHighResArtworkUrl(track?.albumArtUrl, track?.id)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Center Ultra-HD Artwork with Liquid Glass border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(palette.surfaceVariant)
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.08f))
                        ),
                        RoundedCornerShape(32.dp)
                    )
                    .pointerInput(track?.id) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                if (track != null && !PlaylistStore.isLiked(track.id)) {
                                    PlaylistStore.toggleLike(track)
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val hero = TikTokHeart(
                                    x = offset.x,
                                    y = offset.y,
                                    color = Color(0xFFFF1493),
                                    size = 135.dp
                                )
                                heartBursts = heartBursts + hero
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = highResArt,
                    contentDescription = track?.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // TikTok Floating Hearts Layer
                heartBursts.forEach { heart ->
                    key(heart.id) {
                        TikTokPopHeart(
                            heart = heart,
                            onFinished = {
                                heartBursts = heartBursts.filter { it.id != heart.id }
                            }
                        )
                    }
                }

                if (playerState.isBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = palette.primary,
                            modifier = Modifier.size(44.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Track Title, Artist & Like Heart Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = track?.title ?: "No Track Playing",
                    color = palette.textPrimary,
                    fontSize = 18.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = track?.artists?.joinToString(", ") ?: "Select a song to start listening",
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (track != null) {
                val likedTrackIds by PlaylistStore.likedTrackIds.collectAsState()
                val isLiked = likedTrackIds.contains(track.id)

                AnimatedHeartButton(
                    isLiked = isLiked,
                    onToggleLike = { PlaylistStore.toggleLike(track) },
                    iconSize = 25.dp,
                    containerSize = 42.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom UI/UX Pro Max Scrubber
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Slider(
                value = (if (isDraggingSlider) sliderTempPos else currentMs.toFloat()).coerceIn(0f, durationMs.toFloat()),
                onValueChange = onSliderValueChange,
                onValueChangeFinished = onSliderValueChangeFinished,
                valueRange = 0f..durationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = palette.primary,
                    activeTrackColor = palette.primary,
                    inactiveTrackColor = palette.surfaceVariant.copy(alpha = 0.8f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentMs),
                    color = palette.textSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatMs(durationMs),
                    color = palette.textSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Floating Frosted Glass Playback Deck
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.surfaceVariant.copy(alpha = 0.75f),
                            palette.surface.copy(alpha = 0.50f)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                    ),
                    RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = { MusicPlayerManager.toggleShuffle() },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playerState.isShuffleEnabled) palette.primary else palette.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = { MusicPlayerManager.skipPrevious() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Master Play / Pause Glowing Capsule
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(palette.primary, palette.primary.copy(alpha = 0.85f))
                            )
                        )
                        .border(
                            1.5.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.10f))
                            ),
                            CircleShape
                        )
                        .clickable { MusicPlayerManager.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = palette.onPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = { MusicPlayerManager.skipNext() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Repeat
                IconButton(
                    onClick = { MusicPlayerManager.toggleRepeatMode() },
                    modifier = Modifier.size(42.dp)
                ) {
                    val icon = when (playerState.repeatMode) {
                        PlaybackRepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = if (playerState.repeatMode != PlaybackRepeatMode.OFF) palette.primary else palette.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(44.dp))
    }
}

@Composable
private fun QueueView(
    playerState: PlayerState,
    onTrackSelect: (Int) -> Unit
) {
    val palette = LocalStashPalette.current

    if (playerState.queue.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Queue is Empty",
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Play songs from Discover or Search to build your queue.",
                    color = palette.textSecondary,
                    fontSize = 12.5.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UP NEXT (${playerState.queue.size} TRACKS)",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Autoplay Active",
                    color = palette.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        itemsIndexed(playerState.queue) { index, queueTrack ->
            val isCurrent = index == playerState.queueIndex
            val highResArt = ArtworkUtils.getHighResArtworkUrl(queueTrack.albumArtUrl, queueTrack.id)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isCurrent) {
                            Brush.verticalGradient(
                                listOf(palette.primary.copy(alpha = 0.20f), palette.primary.copy(alpha = 0.06f))
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(palette.surface.copy(alpha = 0.70f), palette.surfaceVariant.copy(alpha = 0.40f))
                            )
                        }
                    )
                    .border(
                        1.dp,
                        if (isCurrent) palette.primary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onTrackSelect(index) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrent) {
                        AudioEqualizerVisualizer(tint = palette.primary, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            text = "${index + 1}",
                            color = palette.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceVariant)
                ) {
                    AsyncImage(
                        model = highResArt,
                        contentDescription = queueTrack.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = queueTrack.title,
                        color = if (isCurrent) palette.primary else palette.textPrimary,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = queueTrack.artists.joinToString(", "),
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsView(
    playerState: PlayerState,
    onSeekTo: (Long) -> Unit
) {
    val palette = LocalStashPalette.current
    val listState = rememberLazyListState()
    val currentMs = playerState.currentPositionMs

    if (playerState.isLyricsLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = palette.primary, modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Searching synchronized lyrics...", color = palette.textSecondary, fontSize = 12.5.sp)
            }
        }
        return
    }

    if (playerState.syncedLyrics.isEmpty() && playerState.plainLyrics.isNullOrBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(38.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Synced Lyrics Found", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Lyrics are not yet indexed for this song in open lyric databases.", color = palette.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }

    if (playerState.syncedLyrics.isNotEmpty()) {
        val effectiveLyricsMs = currentMs + 1200L
        val activeIndex = playerState.syncedLyrics.indexOfLast { it.timestampMs <= effectiveLyricsMs }.takeIf { it >= 0 } ?: 0

        LaunchedEffect(activeIndex) {
            if (activeIndex in playerState.syncedLyrics.indices) {
                listState.animateScrollToItem(maxOf(0, activeIndex - 2))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f)
                .drawWithContent {
                    drawContent()
                    // Top fade mask
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startY = 0f,
                            endY = 32.dp.toPx()
                        ),
                        blendMode = BlendMode.DstIn
                    )
                    // Bottom fade mask
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = size.height - 32.dp.toPx(),
                            endY = size.height
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            contentPadding = PaddingValues(vertical = 36.dp)
        ) {
            itemsIndexed(playerState.syncedLyrics) { index, line ->
                val isActive = index == activeIndex
                val isPast = line.timestampMs < effectiveLyricsMs && !isActive

                val textColor by animateColorAsState(
                    targetValue = if (isActive) palette.primary else if (isPast) palette.textSecondary.copy(alpha = 0.40f) else palette.textSecondary.copy(alpha = 0.70f),
                    animationSpec = tween(250),
                    label = "lyricColor"
                )

                val textScale by animateFloatAsState(
                    targetValue = if (isActive) 1.08f else 1.0f,
                    animationSpec = spring(stiffness = 400f),
                    label = "lyricScale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isActive) palette.surfaceVariant.copy(alpha = 0.50f) else Color.Transparent)
                        .clickable(
                            interactionSource = remember(index) { MutableInteractionSource() },
                            indication = null
                        ) { onSeekTo(line.timestampMs) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = line.text,
                        color = textColor,
                        fontSize = if (isActive) 21.sp else 16.sp,
                        fontWeight = if (isActive) FontWeight.Black else FontWeight.SemiBold,
                        textAlign = TextAlign.Start,
                        lineHeight = 28.sp,
                        modifier = Modifier.scale(textScale)
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp)
        ) {
            item {
                Text(
                    text = playerState.plainLyrics ?: "",
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EqualizerFxView() {
    val palette = LocalStashPalette.current
    val playerState by MusicPlayerManager.playerState.collectAsState()
    var selectedPreset by remember { mutableStateOf(playerState.currentEqPreset) }
    var bassBoost by remember { mutableStateOf(playerState.bassBoostStrength) }

    val presets = listOf(
        "No Effect (Off)",
        "Flat",
        "Bass Boost",
        "Vocal Clarity",
        "Electronic",
        "Rock",
        "Acoustic",
        "Deep Lounge"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                text = "EQUALIZER PRESETS",
                color = palette.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    val isSelected = selectedPreset.equals(preset, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) palette.surfaceVariant else palette.surface.copy(alpha = 0.6f))
                            .border(
                                1.dp,
                                if (isSelected) palette.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable(
                                interactionSource = remember(preset) { MutableInteractionSource() },
                                indication = null
                            ) {
                                selectedPreset = preset
                                MusicPlayerManager.setEqualizerPreset(preset)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset,
                            color = if (isSelected) palette.primary else palette.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.5.sp
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = palette.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "BASS BOOST STRENGTH ($bassBoost%)",
                color = palette.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = bassBoost.toFloat(),
                onValueChange = {
                    bassBoost = it.toInt()
                    MusicPlayerManager.setBassBoost(it.toInt())
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = palette.primary,
                    activeTrackColor = palette.primary,
                    inactiveTrackColor = palette.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
