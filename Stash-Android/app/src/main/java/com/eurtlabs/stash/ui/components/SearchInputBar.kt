package com.eurtlabs.stash.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eurtlabs.stash.data.downloader.InnerTubeMusicRepository
import com.eurtlabs.stash.ui.theme.LocalStashPalette
import kotlinx.coroutines.delay

@Composable
fun SearchInputBar(
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    isFetching: Boolean,
    fetchingMessage: String,
    onAnalyzeUrl: (String) -> Unit,
    onCancelFetch: () -> Unit = {},
    onClearSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val palette = LocalStashPalette.current

    val searchButtonInteraction = remember { MutableInteractionSource() }
    val isSearchPressed by searchButtonInteraction.collectIsPressedAsState()
    val searchScale = if (isSearchPressed) 0.90f else 1.0f

    // Live search suggestions with 120ms debounce
    LaunchedEffect(query, isFocused) {
        if (isFocused && query.trim().length >= 2) {
            delay(120)
            suggestions = InnerTubeMusicRepository.getSearchSuggestions(query.trim())
            showSuggestions = suggestions.isNotEmpty()
        } else {
            suggestions = emptyList()
            showSuggestions = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Liquid Glass Search Capsule
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(palette.surfaceVariant.copy(alpha = 0.95f), palette.surface.copy(alpha = 0.85f))
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.03f)
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(19.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = query,
                    onValueChange = {
                        onQueryChange(it)
                    },
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(palette.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) {
                            showSuggestions = false
                            focusManager.clearFocus()
                            onAnalyzeUrl(query.trim())
                        }
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged {
                            isFocused = it.isFocused
                            if (!it.isFocused) {
                                showSuggestions = false
                            }
                        },
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = if (isFetching) fetchingMessage.ifEmpty { "Analyzing media..." } else "Search song, artist, or paste link...",
                                color = palette.textSecondary.copy(alpha = 0.65f),
                                fontSize = 13.5.sp
                            )
                        }
                        innerTextField()
                    }
                )

                if (query.isEmpty() && !isFetching) {
                    // Liquid Glass PASTE Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.surfaceVariant)
                            .border(
                                width = 0.8.dp,
                                color = Color.White.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val pasteData = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!pasteData.isNullOrBlank()) {
                                    onQueryChange(pasteData.trim())
                                    showSuggestions = false
                                    focusManager.clearFocus()
                                    onAnalyzeUrl(pasteData.trim())
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = palette.textPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PASTE",
                                color = palette.textPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                } else if (isFetching) {
                    // CANCEL Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.error.copy(alpha = 0.15f))
                            .border(
                                width = 0.8.dp,
                                color = palette.error.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onCancelFetch() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CANCEL",
                            color = palette.error,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Clear button - Always erases query with 1 tap
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(palette.surfaceVariant)
                                .border(0.8.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                            .clickable {
                                onQueryChange("")
                                suggestions = emptyList()
                                showSuggestions = false
                                onClearSearch()
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Submit Go button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .scale(searchScale)
                                .clip(CircleShape)
                                .background(palette.primary)
                                .clickable(
                                    interactionSource = searchButtonInteraction,
                                    indication = null,
                                    enabled = !isFetching
                                ) {
                                    if (query.isNotBlank()) {
                                        showSuggestions = false
                                        focusManager.clearFocus()
                                        onAnalyzeUrl(query.trim())
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(
                                    color = palette.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Search",
                                    tint = palette.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Suggestions Dropdown
        AnimatedVisibility(
            visible = showSuggestions && suggestions.isNotEmpty(),
            enter = fadeIn(animationSpec = spring()),
            exit = fadeOut(animationSpec = spring())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                palette.surfaceVariant.copy(alpha = 0.98f),
                                palette.surface.copy(alpha = 0.96f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.15f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(vertical = 6.dp)
            ) {
                Column {
                    suggestions.take(5).forEachIndexed { index, suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onQueryChange(suggestion)
                                    showSuggestions = false
                                    focusManager.clearFocus()
                                    onAnalyzeUrl(suggestion)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = palette.textSecondary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = suggestion,
                                    color = palette.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Arrow icon to fill into search bar without immediate submission
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onQueryChange(suggestion)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NorthWest,
                                    contentDescription = "Fill query",
                                    tint = palette.textSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        if (index < minOf(4, suggestions.size - 1)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .padding(horizontal = 16.dp)
                                    .background(palette.border.copy(alpha = 0.4f))
                            )
                        }
                    }
                }
            }
        }
    }
}
