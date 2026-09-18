package com.infusory.modelviewer.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.utils.Manipulator
import com.infusory.modelviewer.data.model.ModelContainerState
import com.infusory.modelviewer.data.model.ModelPartLabel
import com.infusory.modelviewer.ui.theme.PrimaryAccent
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.collision.Vector3
import io.github.sceneview.node.ModelNode

@Composable
fun SceneViewContainer(
    state: ModelContainerState,
    onLabelsProjected: (List<ModelPartLabel>) -> Unit,
    modifier: Modifier = Modifier
) {
    var isModelLoaded by remember { mutableStateOf(false) }
    var defaultManipulator by remember { mutableStateOf<Manipulator?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                createConfiguredSceneView(
                    context = ctx,
                    state = state,
                    onModelReady = { isModelLoaded = true },
                    onManipulatorCaptured = { defaultManipulator = it },
                    onLabelsProjected = onLabelsProjected
                )
            },
            update = { sceneView ->
                // Enable camera manipulation gestures ONLY during Interaction Mode
                // In Normal Mode, cameraManipulator is set to null so the container can be dragged/resized
                val activeManipulator = defaultManipulator ?: sceneView.cameraManipulator
                if (defaultManipulator == null && activeManipulator != null) {
                    defaultManipulator = activeManipulator
                }
                sceneView.cameraManipulator = if (state.isInteractionMode) defaultManipulator else null
            }
        )

        if (!isModelLoaded) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = PrimaryAccent,
                strokeWidth = 2.dp
            )
        }
    }
}

private fun createConfiguredSceneView(
    context: Context,
    state: ModelContainerState,
    onModelReady: () -> Unit,
    onManipulatorCaptured: (Manipulator?) -> Unit,
    onLabelsProjected: (List<ModelPartLabel>) -> Unit
): SceneView {
    val sceneView = SceneView(context)
    
    // Crucial for SurfaceView inside Compose: renders above parent background
    sceneView.setZOrderMediaOverlay(true)
    
    // Save camera manipulator for interaction mode
    onManipulatorCaptured(sceneView.cameraManipulator)

    // Position camera aimed at model center
    sceneView.cameraNode.position = Float3(0f, 0f, 2.8f)
    sceneView.cameraNode.lookAt(Float3(0f, 0f, 0f))

    // Ensure main directional light is active and bright
    sceneView.mainLightNode?.apply {
        intensity = 100_000f
    }

    // Transparent background clear
    sceneView.renderer.clearOptions = sceneView.renderer.clearOptions.apply {
        clear = true
    }

    var activeModelNode: ModelNode? = null

    // Asynchronously load the GLB asset using SceneView ModelLoader
    sceneView.modelLoader.loadModelInstanceAsync(
        fileLocation = state.asset.assetPath
    ) { modelInstance ->
        if (modelInstance != null) {
            val node = ModelNode(modelInstance = modelInstance).apply {
                centerOrigin(Float3(0f, 0f, 0f))
                scaleToUnitCube(1.2f)
            }
            sceneView.addChildNode(node)
            activeModelNode = node
        }
        onModelReady()
    }

    // Frame update hook: projects 3D node world coordinates to 2D container screen coordinates
    sceneView.onFrame = { _ ->
        if (state.showLabels && state.labels.isNotEmpty() && activeModelNode != null) {
            val cameraNode = sceneView.cameraNode
            val updatedLabels = state.labels.map { label ->
                val localPos = Float3(
                    x = label.localPosition[0],
                    y = label.localPosition[1],
                    z = label.localPosition[2]
                )
                val worldPos = activeModelNode?.getWorldPosition(localPos) ?: localPos

                // Project 3D world coordinate through camera to 2D viewport coordinates
                val screenPoint = cameraNode.worldToScreenPoint(
                    Vector3(worldPos.x, worldPos.y, worldPos.z)
                )

                // Only show label if the point is in front of the camera (depth > 0)
                if (screenPoint.z > 0f) {
                    label.copy(screenPosition = Offset(screenPoint.x, screenPoint.y))
                } else {
                    label.copy(screenPosition = null)
                }
            }
            onLabelsProjected(updatedLabels)
        }
    }

    return sceneView
}
