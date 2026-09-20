package com.example.playbox.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.playbox.ui.theme.Playbox

/** The red progress bar. Grows under the finger while dragging. */
@Composable
fun Scrubber(
    progress: Float,
    durationMs: Long,
    modifier: Modifier = Modifier,
    onBegin: () -> Unit = {},
    onChange: (Long) -> Unit = {},
    onEnd: (Long) -> Unit = {},
) {
    var widthPx by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val shown = (if (dragging) dragProgress else progress).coerceIn(0f, 1f)
    val trackHeight by animateDpAsState(if (dragging) 5.dp else 3.dp, label = "track")
    val knob by animateDpAsState(if (dragging) 16.dp else 11.dp, label = "knob")
    val density = LocalDensity.current

    fun emit(p: Float) = (p * durationMs).toLong()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                        onBegin()
                        onChange(emit(dragProgress))
                    },
                    onDragEnd = {
                        dragging = false
                        onEnd(emit(dragProgress))
                    },
                    onDragCancel = {
                        dragging = false
                        onEnd(emit(dragProgress))
                    },
                ) { change, dragAmount ->
                    change.consume()
                    dragProgress = (dragProgress + dragAmount / widthPx).coerceIn(0f, 1f)
                    onChange(emit(dragProgress))
                }
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val p = (offset.x / widthPx).coerceIn(0f, 1f)
                    dragProgress = p
                    onEnd(emit(p))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.28f)),
        )
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(Playbox.Accent),
        )
        Box(
            Modifier
                .offset(
                    x = with(density) {
                        ((widthPx * shown) - (knob.toPx() / 2f)).toDp()
                    },
                )
                .size(knob)
                .clip(CircleShape)
                .background(Playbox.Accent),
        )
    }
}

/** Thin non-interactive version drawn under the mini player. */
@Composable
fun ThinProgress(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Color.White.copy(alpha = 0.18f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Playbox.Accent),
        )
    }
}
