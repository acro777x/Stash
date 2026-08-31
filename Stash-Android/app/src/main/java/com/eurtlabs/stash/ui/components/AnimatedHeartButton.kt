package com.eurtlabs.stash.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eurtlabs.stash.ui.theme.LocalStashPalette
import kotlinx.coroutines.launch

/**
 * High-performance, tactile Animated Heart / Like button.
 * Uses keyframed spring physics (compress -> explosive pop overshoot -> settle to rest)
 * with device haptics and smooth color morphing. Rest state is strictly 1.0f.
 */
@Composable
fun AnimatedHeartButton(
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    containerSize: Dp = 40.dp
) {
    val palette = LocalStashPalette.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    val heartColor by animateColorAsState(
        targetValue = if (isLiked) Color(0xFFFF3366) else palette.textSecondary.copy(alpha = 0.70f),
        animationSpec = tween(durationMillis = 200),
        label = "heartColor"
    )

    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } catch (_: Exception) {}

                onToggleLike()

                scope.launch {
                    scaleAnim.animateTo(
                        targetValue = 0.70f,
                        animationSpec = tween(durationMillis = 80)
                    )
                    scaleAnim.animateTo(
                        targetValue = 1.38f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                    scaleAnim.animateTo(
                        targetValue = 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isLiked) "Unlike Track" else "Like Track",
            tint = heartColor,
            modifier = Modifier
                .size(iconSize)
                .scale(scaleAnim.value)
        )
    }
}
