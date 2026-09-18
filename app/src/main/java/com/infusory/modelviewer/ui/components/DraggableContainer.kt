package com.infusory.modelviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.infusory.modelviewer.data.model.ModelContainerState
import com.infusory.modelviewer.ui.theme.SurfaceCard
import com.infusory.modelviewer.ui.theme.SurfaceCardBorder
import com.infusory.modelviewer.ui.theme.SurfaceCardBorderActive
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

    val containerModifier = if (!state.isInteractionMode) {
        // Normal Mode: Intercept pan and pinch gestures on the container level
        Modifier.pointerInput(state.instanceId) {
            detectTransformGestures { _, pan, zoom, _ ->
                onFocus()
                if (pan != Offset.Zero) {
                    onDrag(pan)
                }
                if (zoom != 1f) {
                    onResize(zoom)
                }
            }
        }
    } else {
        // Interaction Mode: Only intercept taps for focus; pass drags/pinches to 3D SceneView
        Modifier.pointerInput(state.instanceId) {
            detectTapGestures {
                onFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(state.position.x.roundToInt(), state.position.y.roundToInt()) }
            .size(state.size)
            .shadow(elevation = if (state.isInteractionMode) 16.dp else 8.dp, shape = RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(SurfaceCard)
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
            .then(containerModifier)
    ) {
        // 3D Scene content
        content()

        // 2D Labels layer
        if (state.showLabels && state.labels.isNotEmpty()) {
            PartLabelOverlay(labels = state.labels)
        }

        // Action Buttons Overlay (top-right, always visible)
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
