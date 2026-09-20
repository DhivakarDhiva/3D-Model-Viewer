package com.infusory.modelviewer.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    val latestState by rememberUpdatedState(state)

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                createConfiguredSceneView(
                    context = ctx,
                    stateProvider = { latestState },
                    onModelReady = { isModelLoaded = true },
                    onManipulatorCaptured = { defaultManipulator = it },
                    onLabelsProjected = onLabelsProjected
                )
            },
            update = { sceneView ->
                // Enable camera manipulation gestures ONLY during Interaction Mode
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
    stateProvider: () -> ModelContainerState,
    onModelReady: () -> Unit,
    onManipulatorCaptured: (Manipulator?) -> Unit,
    onLabelsProjected: (List<ModelPartLabel>) -> Unit
): SceneView {
    val initialState = stateProvider()
    val sceneView = SceneView(context)
    
    // Crucial for SurfaceView inside Compose: renders above parent background
    sceneView.setZOrderMediaOverlay(true)
    
    val cameraHomePos = Float3(0f, 0f, 2.6f)
    val cameraTargetPos = Float3(0f, 0f, 0f)

    // Build camera manipulator synchronized with initial camera position to prevent jump on touch
    val manipulator = Manipulator.Builder()
        .orbitHomePosition(cameraHomePos.x, cameraHomePos.y, cameraHomePos.z)
        .targetPosition(cameraTargetPos.x, cameraTargetPos.y, cameraTargetPos.z)
        .orbitSpeed(0.005f, 0.005f)
        .zoomSpeed(0.05f)
        .build(Manipulator.Mode.ORBIT)

    sceneView.cameraManipulator = manipulator
    onManipulatorCaptured(manipulator)

    // Position camera aimed directly at scene origin (0, 0, 0)
    sceneView.cameraNode.position = cameraHomePos
    sceneView.cameraNode.lookAt(cameraTargetPos)

    // Main directional light
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
        fileLocation = initialState.asset.assetPath
    ) { modelInstance ->
        if (modelInstance != null) {
            val node = ModelNode(modelInstance = modelInstance).apply {
                // Scale model to comfortably fill the container unit cube
                scaleToUnitCube(1.2f)

                // Perfectly center the 3D model:
                // Shift node position by negative scaled bounding box center so the mesh is centered at (0, 0, 0)
                val cx = center.x * scale.x
                val cy = center.y * scale.y
                val cz = center.z * scale.z
                position = Float3(-cx, -cy, -cz)
            }
            sceneView.addChildNode(node)
            activeModelNode = node
        }
        onModelReady()
    }

    // Frame update hook: projects 3D node world coordinates to 2D container screen coordinates
    sceneView.onFrame = { _ ->
        val currentState = stateProvider()
        if (currentState.showLabels && currentState.labels.isNotEmpty() && activeModelNode != null) {
            val cameraNode = sceneView.cameraNode
            val updatedLabels = currentState.labels.map { label ->
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

