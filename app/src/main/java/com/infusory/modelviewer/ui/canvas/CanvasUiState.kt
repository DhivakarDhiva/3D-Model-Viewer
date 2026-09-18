package com.infusory.modelviewer.ui.canvas

import com.infusory.modelviewer.data.model.ModelAsset
import com.infusory.modelviewer.data.model.ModelContainerState

/**
 * Top-level immutable state for the full-screen canvas.
 */
data class CanvasUiState(
    val activeModels: List<ModelContainerState> = emptyList(),
    val availableAssets: List<ModelAsset> = ModelAsset.DEFAULT_MODELS,
    val isPickerSheetVisible: Boolean = false,
    val topZIndex: Float = 1f,
    val fps: Int = 60
) {
    val canAddModel: Boolean
        get() = activeModels.size < 5
}
