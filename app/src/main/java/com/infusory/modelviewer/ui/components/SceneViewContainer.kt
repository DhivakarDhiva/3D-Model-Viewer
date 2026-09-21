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
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.times
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode

@Composable
fun SceneViewContainer(
    state: ModelContainerState,
    isTop: Boolean = false,
    onLabelsProjected: ((List<ModelPartLabel>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isModelLoaded by remember { mutableStateOf(false) }
    var defaultManipulator by remember { mutableStateOf<Manipulator?>(null) }
    val latestState by rememberUpdatedState(state)
    var projectedLabels by remember { mutableStateOf<List<ModelPartLabel>>(emptyList()) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                createConfiguredSceneView(
                    context = ctx,
                    stateProvider = { latestState },
                    onModelReady = { isModelLoaded = true },
                    onManipulatorCaptured = { defaultManipulator = it },
                    onLabelsProjected = { labels ->
                        projectedLabels = labels
                        onLabelsProjected?.invoke(labels)
                    }
                )
            },
            update = { sceneView ->
                // Enable camera manipulation gestures ONLY during Interaction Mode
                val activeManipulator = defaultManipulator ?: sceneView.cameraManipulator
                if (defaultManipulator == null && activeManipulator != null) {
                    defaultManipulator = activeManipulator
                }
                sceneView.cameraManipulator = if (state.isInteractionMode) defaultManipulator else null

                // Dynamically sync SurfaceView native Z-order in SurfaceFlinger
                updateSurfaceZOrder(sceneView, isTop = isTop, zIndex = state.zIndex)
            }
        )

        // 2D Part Labels Overlay: rendered directly on top of 3D SceneView
        if (state.showLabels && projectedLabels.isNotEmpty()) {
            PartLabelOverlay(labels = projectedLabels)
        }

        if (!isModelLoaded) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = PrimaryAccent,
                strokeWidth = 2.dp
            )
        }
    }
}

/**
 * Ensures the underlying hardware SurfaceView in SurfaceFlinger respects the active container's
 * z-ordering.
 */
private fun updateSurfaceZOrder(surfaceView: SceneView, isTop: Boolean, zIndex: Float) {
    // 1. Reorder in parent ViewGroup (AndroidViewHolder inside androidViewsHandler)
    try {
        val holder = surfaceView.parent as? android.view.View
        val parentViewGroup = holder?.parent as? android.view.ViewGroup
        if (isTop && holder != null && parentViewGroup != null) {
            val count = parentViewGroup.childCount
            if (count > 1 && parentViewGroup.getChildAt(count - 1) !== holder) {
                parentViewGroup.bringChildToFront(holder)
            }
        }
        surfaceView.bringToFront()
        holder?.z = zIndex
        surfaceView.z = zIndex
    } catch (_: Throwable) {
        // Suppress any hierarchy access issues
    }

    // 2. Set SurfaceView sub-layer in SurfaceFlinger:
    var appliedReflection = false
    try {
        val targetLayer = -100 + zIndex.toInt().coerceIn(1, 95)
        val field = android.view.SurfaceView::class.java.getDeclaredField("mRequestedSubLayer")
        field.isAccessible = true
        val currentLayer = field.getInt(surfaceView)
        if (currentLayer != targetLayer) {
            field.setInt(surfaceView, targetLayer)
            surfaceView.requestLayout()
            surfaceView.invalidate()
        }
        appliedReflection = true
    } catch (_: Throwable) {
        appliedReflection = false
    }

    // 3. Fallback using official setZOrderMediaOverlay API:
    if (!appliedReflection) {
        try {
            surfaceView.setZOrderMediaOverlay(isTop)
            surfaceView.requestLayout()
            surfaceView.invalidate()
        } catch (_: Throwable) {
            // Suppress fallback errors
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
            val updatedLabels = currentState.labels.map { label ->
                val targetNode = activeModelNode?.nodes?.firstOrNull { node ->
                    (node as? ModelNode.ChildNode)?.name == label.nodeName
                } ?: activeModelNode?.nodes?.getOrNull(label.nodeIndex)

                val localPos = Float3(
                    label.localPosition.getOrElse(0) { 0f },
                    label.localPosition.getOrElse(1) { 0f },
                    label.localPosition.getOrElse(2) { 0f }
                )

                val worldPos = targetNode?.worldPosition
                    ?: activeModelNode?.getWorldPosition(localPos)
                    ?: localPos

                val screenOffset = projectWorldToScreen(sceneView, worldPos)
                label.copy(screenPosition = screenOffset)
            }
            onLabelsProjected(updatedLabels)
        }
    }

    return sceneView
}

/**
 * Projects a 3D world coordinate to 2D container pixel coordinates.
 * Verifies that the point is strictly in front of the camera before applying
 * projection and perspective divide.
 */
private fun projectWorldToScreen(
    sceneView: SceneView,
    worldPos: Float3
): Offset? {
    val cameraNode = sceneView.cameraNode
    val viewportWidth = sceneView.width.toFloat()
    val viewportHeight = sceneView.height.toFloat()
    if (viewportWidth <= 0f || viewportHeight <= 0f) return null

    // Transform from world space to clip space: viewProj = cullingProjection * view
    val viewProj = cameraNode.cullingProjectionTransform * cameraNode.viewTransform
    val clip = viewProj * Float4(worldPos.x, worldPos.y, worldPos.z, 1.0f)

    // clip.w > 0 indicates point is strictly in front of the camera
    if (clip.w <= 0.02f) return null

    val ndcX = clip.x / clip.w
    val ndcY = clip.y / clip.w

    val screenX = (ndcX + 1.0f) * 0.5f * viewportWidth
    val screenY = (1.0f - ndcY) * 0.5f * viewportHeight

    // Keep point if within container visible area plus a small margin
    if (screenX < -50f || screenX > viewportWidth + 50f || screenY < -50f || screenY > viewportHeight + 50f) {
        return null
    }

    return Offset(screenX, screenY)
}


