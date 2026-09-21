package com.infusory.modelviewer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.infusory.modelviewer.data.model.ModelContainerState
import com.infusory.modelviewer.ui.theme.SurfaceCardBorder
import com.infusory.modelviewer.ui.theme.SurfaceCardBorderActive
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DraggableContainer(
    state: ModelContainerState,
    modifier: Modifier = Modifier,
    onDrag: (Offset) -> Unit,
    onResize: (Float) -> Unit,
    onFocus: () -> Unit,
    onToggleInteraction: () -> Unit,
    onToggleLabels: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    val cornerRadius = 14.dp
    val borderColor = if (state.isInteractionMode) SurfaceCardBorderActive else SurfaceCardBorder
    val borderWidth = if (state.isInteractionMode) 2.dp else 1.dp

    Box(
        modifier = modifier
            .offset { IntOffset(state.position.x.roundToInt(), state.position.y.roundToInt()) }
            .size(state.size)
            .shadow(
                elevation = if (state.isInteractionMode) 16.dp else 6.dp,
                shape = RoundedCornerShape(cornerRadius)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
    ) {
        // 1. 3D Viewport Content
        content()

        // 2. Normal Mode Gesture Shield:
        // When NOT in interaction mode, this overlay intercepts touches:
        // - Exactly 1 finger: Drags the container smoothly across the screen.
        // - 2 fingers: Pinches to resize the container without changing its position.
        // - No position jumps or centroid teleportation.
        if (!state.isInteractionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.instanceId) {
                        awaitEachGesture {
                            // Focus on first touch down
                            awaitFirstDown(requireUnconsumed = false)
                            onFocus()

                            var prevPinchDistance = 0f

                            do {
                                val event = awaitPointerEvent()
                                val pressedChanges = event.changes.filter { it.pressed }

                                if (pressedChanges.size == 1) {
                                    // 1-FINGER DRAG
                                    val currentPointer = pressedChanges.first()
                                    if (currentPointer.positionChanged()) {
                                        val delta = currentPointer.position - currentPointer.previousPosition
                                        onDrag(delta)
                                        currentPointer.consume()
                                    }
                                    prevPinchDistance = 0f
                                } else if (pressedChanges.size >= 2) {
                                    // 2-FINGER PINCH-RESIZE (no position mutation)
                                    val p0 = pressedChanges[0].position
                                    val p1 = pressedChanges[1].position
                                    val distance = (p0 - p1).getDistance()

                                    if (prevPinchDistance > 0f && distance > 0f) {
                                        val zoomRatio = distance / prevPinchDistance
                                        if (abs(zoomRatio - 1f) > 0.001f) {
                                            onResize(zoomRatio)
                                        }
                                    }
                                    prevPinchDistance = distance
                                    pressedChanges.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            )
        }

        // 3. Action Buttons Overlay (top-right, highest z-index)
        ModelOverlayButtons(
            isInteractionMode = state.isInteractionMode,
            showLabels = state.showLabels,
            onToggleInteraction = onToggleInteraction,
            onToggleLabels = onToggleLabels,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

