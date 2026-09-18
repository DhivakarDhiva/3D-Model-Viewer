package com.infusory.modelviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
        modifier = Modifier
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
        // When NOT in interaction mode, this transparent overlay intercepts 100% of touches.
        // It captures 1-finger drag and 2-finger pinch cleanly, preventing native SurfaceView from stealing touches!
        if (!state.isInteractionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.instanceId) {
                        detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                            onFocus()
                            if (pan != Offset.Zero) {
                                onDrag(pan)
                            }
                            if (abs(zoom - 1f) > 0.0005f) {
                                onResize(zoom)
                            }
                        }
                    }
            )
        }

        // 3. 2D Part Labels Overlay
        if (state.showLabels && state.labels.isNotEmpty()) {
            PartLabelOverlay(labels = state.labels)
        }

        // 4. Action Buttons Overlay (top-right, highest z-index)
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
