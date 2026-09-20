package com.infusory.modelviewer.ui.canvas

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infusory.modelviewer.data.model.ModelAsset
import com.infusory.modelviewer.data.model.ModelContainerState
import com.infusory.modelviewer.data.model.ModelPartLabel
import com.infusory.modelviewer.data.parser.GlbMetadataParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CanvasViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    private var zIndexCounter = 1f

    fun openModelPicker() {
        _uiState.update { it.copy(isPickerSheetVisible = true) }
    }

    fun closeModelPicker() {
        _uiState.update { it.copy(isPickerSheetVisible = false) }
    }

    fun addModel(asset: ModelAsset) {
        if (!_uiState.value.canAddModel) return

        val count = _uiState.value.activeModels.size
        // Calculate initial staggered offset so added models don't stack directly on top of each other
        val initialOffset = Offset(
            x = 40f + (count * 45f),
            y = 80f + (count * 55f)
        )
        zIndexCounter += 1f

        val newInstance = ModelContainerState(
            asset = asset,
            position = initialOffset,
            zIndex = zIndexCounter
        )

        _uiState.update { current ->
            current.copy(
                activeModels = current.activeModels + newInstance,
                isPickerSheetVisible = false,
                topZIndex = zIndexCounter
            )
        }

        // Asynchronously parse labels from glTF JSON extras chunk
        viewModelScope.launch(Dispatchers.IO) {
            val parsedLabels = GlbMetadataParser.parseLabelsFromAsset(
                context = getApplication(),
                assetPath = asset.assetPath
            )
            _uiState.update { current ->
                current.copy(
                    activeModels = current.activeModels.map { model ->
                        if (model.instanceId == newInstance.instanceId) {
                            model.copy(labels = parsedLabels, isLoading = false)
                        } else {
                            model
                        }
                    }
                )
            }
        }
    }

    fun removeModel(instanceId: String) {
        _uiState.update { current ->
            current.copy(
                activeModels = current.activeModels.filterNot { it.instanceId == instanceId }
            )
        }
    }

    fun bringToFront(instanceId: String) {
        val model = _uiState.value.activeModels.find { it.instanceId == instanceId } ?: return
        if (model.zIndex >= _uiState.value.topZIndex && _uiState.value.activeModels.size > 1) {
            return // Already on top
        }
        zIndexCounter += 1f
        _uiState.update { current ->
            current.copy(
                activeModels = current.activeModels.map {
                    if (it.instanceId == instanceId) it.copy(zIndex = zIndexCounter) else it
                },
                topZIndex = zIndexCounter
            )
        }
    }

    fun updatePosition(instanceId: String, delta: Offset) {
        _uiState.update { current ->
            current.copy(
                activeModels = current.activeModels.map { model ->
                    if (model.instanceId == instanceId) {
                        val newX = (model.position.x + delta.x).coerceAtLeast(0f)
                        val newY = (model.position.y + delta.y).coerceAtLeast(0f)
                        model.copy(position = Offset(newX, newY))
                    } else {
                        model
                    }
                }
            )
        }
    }

    fun updateSize(instanceId: String, zoomMultiplier: Float) {
        _uiState.update { current ->
            current.copy(
                activeModels = current.activeModels.map { model ->
                    if (model.instanceId == instanceId) {
                        val oldW = model.size.width
                        val oldH = model.size.height
                        val newW = (oldW * zoomMultiplier)
                            .coerceIn(ModelContainerState.MIN_SIZE, ModelContainerState.MAX_SIZE)
                        val newH = (oldH * zoomMultiplier)
                            .coerceIn(ModelContainerState.MIN_SIZE, ModelContainerState.MAX_SIZE)

                        // Resize only — keep container position completely stable without jumping
                        model.copy(
                            size = DpSize(newW, newH)
                        )
                    } else {
                        model
                    }
                }
            )
        }
    }

    fun toggleInteractionMode(instanceId: String) {
        _uiState.update { current ->
            current.copy(
                activeModels = current.activeModels.map { model ->
                    if (model.instanceId == instanceId) {
                        model.copy(isInteractionMode = !model.isInteractionMode)
                    } else {
                        model
                    }
                }
            )
        }
    }

    fun toggleLabels(instanceId: String) {
        _uiState.update { current ->
            current.copy(
                activeModels = current.activeModels.map { model ->
                    if (model.instanceId == instanceId) {
                        model.copy(showLabels = !model.showLabels)
                    } else {
                        model
                    }
                }
            )
        }
    }

    fun updateProjectedLabels(instanceId: String, updatedLabels: List<ModelPartLabel>) {
        _uiState.update { current ->
            current.copy(
                activeModels = current.activeModels.map { model ->
                    if (model.instanceId == instanceId) {
                        model.copy(labels = updatedLabels)
                    } else {
                        model
                    }
                }
            )
        }
    }
}
