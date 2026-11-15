package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DepthDark
import com.example.ui.theme.LocalIsDarkTheme
import java.util.Locale

/**
 * Tactile 3D-layered card inspired by Uber / Swiggy / Zomato UI cards:
 * - Subtle 3D bottom lip & soft ambient shadow (obsidian-recessed in dark theme)
 * - Spring bounce scale on press
 * - Clean hairline highlight border
 */
@Composable
fun ThreeDTactileCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    depthColor: Color = MaterialTheme.colorScheme.outline,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
    cornerRadius: Dp = 22.dp,
    elevationDp: Dp = 4.dp,
    depthDp: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val resolvedDepthColor = remember(isDark, depthColor) {
        if (isDark && depthColor.luminance() > 0.08f) {
            DepthDark
        } else {
            depthColor
        }
    }
    val resolvedBorderColor = remember(isDark, borderColor) {
        if (isDark && borderColor.luminance() > 0.65f) {
            borderColor.copy(alpha = 0.35f)
        } else {
            borderColor
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    val pressOffsetY by animateDpAsState(
        targetValue = if (isPressed && onClick != null) depthDp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "cardOffset"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                // 3D bottom bevel base
                drawRoundRect(
                    color = resolvedDepthColor,
                    topLeft = Offset(0f, depthDp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                )
            }
            .padding(bottom = depthDp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = pressOffsetY)
                .shadow(
                    elevation = if (isPressed) 1.dp else if (isDark) 2.dp else elevationDp,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.08f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.12f)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = ripple(),
                            onClick = onClick
                        )
                    } else Modifier
                ),
            shape = RoundedCornerShape(cornerRadius),
            color = containerColor,
            border = BorderStroke(1.dp, resolvedBorderColor)
        ) {
            Column(content = content)
        }
    }
}

/**
 * Tactile 3D push-button with glossy top gradient & solid 3D bottom bevel.
 */
@Composable
fun ThreeDActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    depthColor: Color = Color(0xFF3730A3),
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    cornerRadius: Dp = 16.dp,
    height: Dp = 50.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val effectiveDepth = if (enabled) 3.5.dp else 1.dp
    val offsetY by animateDpAsState(
        targetValue = if (isPressed && enabled) effectiveDepth else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "btnOffset"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btnScale"
    )

    val activeBg = if (enabled) containerColor else containerColor.copy(alpha = 0.45f)
    val activeDepth = if (enabled) depthColor else depthColor.copy(alpha = 0.35f)

    BoxWithConstraints(
        modifier = modifier
            .height(height + effectiveDepth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        val stretchWidth = constraints.hasFixedWidth
        val widthModifier = if (stretchWidth) Modifier.fillMaxWidth() else Modifier

        Box(modifier = widthModifier.height(height + effectiveDepth)) {
            // 3D base bevel
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = effectiveDepth)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(activeDepth)
            )
            // Top tactile button face
            Surface(
                modifier = widthModifier
                    .height(height)
                    .offset(y = offsetY)
                    .clip(RoundedCornerShape(cornerRadius))
                    .clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = ripple(color = Color.White),
                        onClick = onClick
                    ),
                shape = RoundedCornerShape(cornerRadius),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    activeBg.copy(alpha = 1f),
                                    activeBg
                                )
                            )
                        )
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * 3D Floating Bobbing Container for 3D hero illustrations & orbs.
 */
@Composable
fun Floating3DObjectBox(
    modifier: Modifier = Modifier,
    bobDistanceDp: Float = 5f,
    durationMs: Int = 2600,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "float3d")
    val dy by infiniteTransition.animateFloat(
        initialValue = -bobDistanceDp,
        targetValue = bobDistanceDp,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    Box(
        modifier = modifier.graphicsLayer {
            translationY = dy
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Animated pulsing live status beacon (Uber/Swiggy live tracking indicator).
 */
@Composable
fun LivePulseBeacon(
    isActive: Boolean,
    activeColor: Color = Color(0xFF10B981),
    inactiveColor: Color = Color(0xFF94A3B8),
    size: Dp = 10.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "liveBeacon")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier.size(size * 2.2f),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .clip(CircleShape)
                    .background(activeColor)
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (isActive) activeColor else inactiveColor)
        )
    }
}

/**
 * Smooth rolling numeric display for Rupee totals (fintech ticker feel).
 */
@Composable
fun AnimatedRupeeText(
    amount: Double,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    fontWeight: FontWeight = FontWeight.ExtraBold
) {
    val animatedValue by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "rupeeCounter"
    )

    val formatted = if (amount % 1.0 == 0.0 && Math.abs(animatedValue - amount.toFloat()) < 0.05f) {
        String.format(Locale.US, "%,.0f", amount)
    } else {
        String.format(Locale.US, "%,.2f", animatedValue.toDouble())
    }

    Text(
        text = "₹$formatted",
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight
    )
}

/**
 * Sleek 3D Isometric Speaker Pill Badge (visual accent with glowing depth).
 */
@Composable
fun StatusPillBadge(
    text: String,
    color: Color,
    containerColor: Color,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
