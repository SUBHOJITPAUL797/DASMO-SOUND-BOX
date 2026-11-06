package com.example.presentation.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

enum class PatternState {
    NORMAL,
    ERROR,
    SUCCESS
}

@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
    state: PatternState = PatternState.NORMAL,
    enabled: Boolean = true,
    minDots: Int = 4,
    onPatternStarted: () -> Unit = {},
    onPatternCompleted: (String) -> Unit,
    onPatternTooShort: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedDots by remember { mutableStateOf(listOf<Int>()) }
    var currentTouchPosition by remember { mutableStateOf<Offset?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // Clear pattern when state transitions back to normal
    LaunchedEffect(state) {
        if (state == PatternState.NORMAL && !isDragging) {
            selectedDots = emptyList()
            currentTouchPosition = null
        }
    }

    val lineColor = when (state) {
        PatternState.ERROR -> Color(0xFFEF4444) // Bright red
        PatternState.SUCCESS -> Color(0xFF22C55E) // Bright green
        PatternState.NORMAL -> MaterialTheme.colorScheme.primary
    }

    val activeDotColor = when (state) {
        PatternState.ERROR -> Color(0xFFEF4444)
        PatternState.SUCCESS -> Color(0xFF22C55E)
        PatternState.NORMAL -> MaterialTheme.colorScheme.primary
    }

    val inactiveDotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(enabled, state) {
                if (!enabled) return@pointerInput

                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onPatternStarted()
                        val hitDot = findHitDot(offset, size.toPx(), 40.dp.toPx())
                        selectedDots = if (hitDot != null) {
                            vibrateTick(context)
                            listOf(hitDot)
                        } else {
                            emptyList()
                        }
                        currentTouchPosition = offset
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentTouchPosition = change.position
                        val hitDot = findHitDot(change.position, size.toPx(), 40.dp.toPx())
                        if (hitDot != null && !selectedDots.contains(hitDot)) {
                            // Check for intermediate dot on straight lines
                            val lastDot = selectedDots.lastOrNull()
                            val newDots = selectedDots.toMutableList()
                            if (lastDot != null) {
                                val intermediate = getIntermediateDot(lastDot, hitDot)
                                if (intermediate != null && !newDots.contains(intermediate)) {
                                    newDots.add(intermediate)
                                }
                            }
                            newDots.add(hitDot)
                            selectedDots = newDots
                            vibrateTick(context)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        currentTouchPosition = null
                        if (selectedDots.size >= minDots) {
                            onPatternCompleted(selectedDots.joinToString("-"))
                        } else if (selectedDots.isNotEmpty()) {
                            onPatternTooShort()
                            vibrateError(context)
                            selectedDots = emptyList()
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        currentTouchPosition = null
                        selectedDots = emptyList()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size.width
            val dotCenters = calculateDotCenters(canvasSize)

            // 1. Draw connected lines between selected dots
            if (selectedDots.size > 1) {
                for (i in 0 until selectedDots.size - 1) {
                    val p1 = dotCenters[selectedDots[i]]
                    val p2 = dotCenters[selectedDots[i + 1]]
                    drawLine(
                        color = lineColor,
                        start = p1,
                        end = p2,
                        strokeWidth = 10.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 2. Draw live line to current touch position
            if (isDragging && selectedDots.isNotEmpty() && currentTouchPosition != null) {
                val lastCenter = dotCenters[selectedDots.last()]
                drawLine(
                    color = lineColor.copy(alpha = 0.8f),
                    start = lastCenter,
                    end = currentTouchPosition!!,
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. Draw 9 dots (outer ring when selected, inner circle)
            dotCenters.forEachIndexed { index, center ->
                val isSelected = selectedDots.contains(index)

                if (isSelected) {
                    // Outer glow/ring
                    drawCircle(
                        color = activeDotColor.copy(alpha = 0.25f),
                        radius = 28.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = activeDotColor,
                        radius = 28.dp.toPx(),
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    // Inner solid dot
                    drawCircle(
                        color = activeDotColor,
                        radius = 12.dp.toPx(),
                        center = center
                    )
                } else {
                    // Inactive small dot
                    drawCircle(
                        color = inactiveDotColor,
                        radius = 8.dp.toPx(),
                        center = center
                    )
                }
            }
        }
    }
}

private fun calculateDotCenters(canvasSize: Float): List<Offset> {
    val step = canvasSize / 3f
    val halfStep = step / 2f
    val centers = mutableListOf<Offset>()

    for (row in 0..2) {
        for (col in 0..2) {
            val x = col * step + halfStep
            val y = row * step + halfStep
            centers.add(Offset(x, y))
        }
    }
    return centers
}

private fun findHitDot(touch: Offset, canvasSize: Float, hitRadius: Float): Int? {
    val centers = calculateDotCenters(canvasSize)
    for (i in centers.indices) {
        val center = centers[i]
        val dx = touch.x - center.x
        val dy = touch.y - center.y
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (dist <= hitRadius) {
            return i
        }
    }
    return null
}

private fun getIntermediateDot(d1: Int, d2: Int): Int? {
    val r1 = d1 / 3
    val c1 = d1 % 3
    val r2 = d2 / 3
    val c2 = d2 % 3

    if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
        val midR = (r1 + r2) / 2
        val midC = (c1 + c2) / 2
        return midR * 3 + midC
    }
    return null
}

private fun vibrateTick(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25)
        }
    } catch (_: Exception) {}
}

private fun vibrateError(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 70, 70), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 70, 70, 70), -1)
        }
    } catch (_: Exception) {}
}

