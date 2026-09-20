package com.infusory.modelviewer.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.infusory.modelviewer.ui.components.DraggableContainer
import com.infusory.modelviewer.ui.components.ModelPickerSheet
import com.infusory.modelviewer.ui.components.SceneViewContainer
import com.infusory.modelviewer.ui.theme.CanvasBackground
import com.infusory.modelviewer.ui.theme.PrimaryAccent

@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
    ) {
        // Render each active model container with its unique instance key
        uiState.activeModels.forEach { modelState ->
            androidx.compose.runtime.key(modelState.instanceId) {
                DraggableContainer(
                    state = modelState,
                    modifier = Modifier.zIndex(modelState.zIndex),
                    onDrag = { delta -> viewModel.updatePosition(modelState.instanceId, delta) },
                    onResize = { zoom -> viewModel.updateSize(modelState.instanceId, zoom) },
                    onFocus = { viewModel.bringToFront(modelState.instanceId) },
                    onToggleInteraction = { viewModel.toggleInteractionMode(modelState.instanceId) },
                    onToggleLabels = { viewModel.toggleLabels(modelState.instanceId) },
                    onClose = { viewModel.removeModel(modelState.instanceId) }
                ) {
                    SceneViewContainer(
                        state = modelState,
                        onLabelsProjected = { updatedLabels ->
                            viewModel.updateProjectedLabels(modelState.instanceId, updatedLabels)
                        }
                    )
                }
            }
        }

        // Floating "Add Model" Action Button (bottom center)
        ExtendedFloatingActionButton(
            onClick = { viewModel.openModelPicker() },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(text = "Add Model (${uiState.activeModels.size}/5)") },
            containerColor = PrimaryAccent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .zIndex(uiState.topZIndex + 10f)
        )

        // Modal Bottom Sheet to choose from the 5 bundled models
        if (uiState.isPickerSheetVisible) {
            ModelPickerSheet(
                availableModels = uiState.availableAssets,
                currentCount = uiState.activeModels.size,
                onModelSelected = { asset -> viewModel.addModel(asset) },
                onDismiss = { viewModel.closeModelPicker() }
            )
        }
    }
}
