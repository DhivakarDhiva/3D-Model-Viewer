package com.infusory.modelviewer.data.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.util.UUID

/**
 * Encapsulates the complete layout, interaction mode, and visual state
 * of an individual 3D model container on the screen canvas.
 */
data class ModelContainerState(
    val instanceId: String = UUID.randomUUID().toString(),
    val asset: ModelAsset,
    val position: Offset = Offset(60f, 120f),
    val size: DpSize = DpSize(260.dp, 260.dp),
    val zIndex: Float = 0f,
    val isInteractionMode: Boolean = false,
    val showLabels: Boolean = false,
    val labels: List<ModelPartLabel> = emptyList(),
    val isLoading: Boolean = true
) {
    companion object {
        val MIN_SIZE: Dp = 140.dp
        val MAX_SIZE: Dp = 500.dp
    }
}
