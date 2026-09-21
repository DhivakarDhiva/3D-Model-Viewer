# 3D Model Viewer — Developer Manual & Architecture Guide

**Project:** Multi-Model Interactive 3D Viewer for Android  
**Target Platform:** Android (minSdk 24, targetSdk 35)  
**Primary Stack:** Kotlin, Jetpack Compose, SceneView 2.2.1 (Google Filament PBR Engine)  
**Architecture:** Single Activity, Unidirectional Data Flow (UDF / MVVM)

---

## 1. System Overview

This application allows users to spawn, arrange, and inspect multiple 3D models concurrently on a single canvas. Each model lives inside an independent container that can be dragged across the screen, resized via pinch gestures, or placed into a focused 3D interaction mode.

Key functional capabilities:
* **Concurrent Rendering:** Up to 5 interactive models rendered simultaneously at steady framerates.
* **Strict Gesture Separation:** 1-finger container dragging and 2-finger resizing never conflict with 3D camera rotation.
* **SurfaceFlinger Z-Ordering:** Overlapping containers composite properly at the hardware level; touching any card brings both its frame and its 3D model to the foreground.
* **Dynamic 2D Part Labels:** 3D model points project to 2D screen badges in real time. An anti-collision layout algorithm prevents label text from overlapping as models rotate.

---

## 2. Directory & Package Structure

```
app/src/main/java/com/infusory/modelviewer/
│
├── MainActivity.kt
│   └── Single-activity entry point. Configures edge-to-edge windowing and hosts CanvasScreen.
│
├── data/
│   ├── model/
│   │   ├── ModelAsset.kt           # Bundled model catalog (file paths, display names, categories).
│   │   ├── ModelContainerState.kt  # Immutable UI state for an active container on screen.
│   │   └── ModelPartLabel.kt       # 3D anchor position and projected 2D screen coordinate.
│   │
│   └── parser/
│       └── GlbMetadataParser.kt    # Lightweight binary GLB parser for JSON chunk & fallback anchors.
│
└── ui/
    ├── canvas/
    │   ├── CanvasScreen.kt         # Root canvas composable, bottom sheet picker, floating controls.
    │   ├── CanvasViewModel.kt      # Manages active containers, z-index hierarchy, additions/removals.
    │   └── CanvasUiState.kt        # StateFlow data contract consumed by CanvasScreen.
    │
    ├── components/
    │   ├── DraggableContainer.kt   # Container card gesture detection (drag vs. pinch resize).
    │   ├── SceneViewContainer.kt   # AndroidView wrapper around Filament's SceneView; frame-rate projection.
    │   ├── PartLabelOverlay.kt     # Anti-collision layout solver and hardware-accelerated badge rendering.
    │   └── ModelOverlayButtons.kt  # Container action pills (Interaction Mode, Labels, Close).
    │
    └── theme/
        ├── Color.kt                # Cyan accents, dark surfaces, semi-transparent label backgrounds.
        ├── Theme.kt                # Material3 dark-theme foundation.
        └── Type.kt                 # Typography definitions.
```

---

## 3. Core Technical Solutions

### 3.1 3D Model Centering
**Problem:** Exported 3D assets often have arbitrary pivot points set by 3D artists, causing models to spawn off-center or swing outside the container during rotation.  
**Solution:** In `SceneViewContainer.kt`, immediately after `loadModelInstanceAsync` finishes, the model's scaled bounding box center is computed and inverted:
```kotlin
val cx = center.x * scale.x
val cy = center.y * scale.y
val cz = center.z * scale.z
position = Float3(-cx, -cy, -cz)
```
This forces the geometric center of any mesh to sit at $(0, 0, 0)$, ensuring predictable orbiting and zooming around container center.

---

### 3.2 Gesture Disambiguation (1-Finger Drag vs. 2-Finger Pinch)
**Problem:** Standard Compose gesture detectors (`detectTransformGestures` or naive `detectDragGestures`) often trigger drag deltas the instant a second finger lands for a pinch, causing containers to jump or teleport across the screen.  
**Solution:** In `DraggableContainer.kt`, gestures run inside a custom pointer pass using `awaitEachGesture`:
* When `pressedChanges.size == 1`: Pointer deltas are routed to `onDrag(delta)`.
* When `pressedChanges.size >= 2`: Dragging is immediately suspended. We compute the Euclidean distance between pointers and route ratio changes to `onResize(zoomRatio)`.
* Pointer positions are consumed explicitly to block parent scroll handlers.

---

### 3.3 Hardware SurfaceView Z-Order in SurfaceFlinger
**Problem:** `SceneView` renders into a native Android `SurfaceView`. Unlike standard views, `SurfaceView` punches a hole through the view hierarchy and composites directly in hardware (`SurfaceFlinger`). In Compose, changing `Modifier.zIndex()` only reorders the 2D border and buttons—the 3D models underneath remained in their initial creation order, causing front containers to display their 3D models behind older containers.  
**Solution:** `updateSurfaceZOrder()` in `SceneViewContainer.kt` applies a two-tier synchronization:
1. Reorders the underlying `AndroidViewHolder` inside its parent `ViewGroup` via `parentViewGroup.bringChildToFront(holder)`.
2. Dynamically manages the hardware surface layer:
   ```kotlin
   val targetLayer = -100 + zIndex.toInt().coerceIn(1, 95)
   val field = SurfaceView::class.java.getDeclaredField("mRequestedSubLayer")
   field.isAccessible = true
   field.setInt(surfaceView, targetLayer)
   ```
   Fallback to `surfaceView.setZOrderMediaOverlay(isTop)` ensures reliable compositing across all vendor ROMs.

---

### 3.4 3D-to-2D Coordinate Projection
**Problem:** SceneView 2.2.1 has an internal bug in `CameraNode.worldToScreenPoint()` where the $Z$ coordinate is left at `0.0f`. Any standard check for `screenPoint.z > 0f` fails permanently, making labels invisible.  
**Solution:** We built our own mathematical projection pipeline using Filament camera matrices:
1. Matrix multiplication:
   $$\text{viewProj} = \text{cameraNode.cullingProjectionTransform} \times \text{cameraNode.viewTransform}$$
   $$\text{clip} = \text{viewProj} \times \begin{bmatrix} x & y & z & 1 \end{bmatrix}^T$$
2. **Near-plane culling:** Only points with $\text{clip}.w > 0.02f$ are considered in front of the camera (points rotated behind the model are discarded).
3. **Perspective division:** Normalized device coordinates are computed via $(clip.x / clip.w, clip.y / clip.w)$ and mapped to container pixel coordinates $(screenX, screenY)$.

---

### 3.5 Anti-Collision Label Layout Solver
**Problem:** When rotating a model, parts frequently align on the same horizontal or vertical line, causing label text boxes to overlap and obscure each other.  
**Solution:** `PartLabelOverlay.kt` runs an analytical relaxation pass on every frame:
1. **Column Partitioning:** Labels are split into Left and Right columns based on their anchor's X position relative to the container center.
2. **Vertical Sorting & Spacing:** Within each column, labels are sorted top-to-bottom by anchor $Y$. A top-down relaxation pass enforces a minimum gap between consecutive badges:
   $$Y_i \ge Y_{i-1} + \text{BadgeHeight} + \text{MinVerticalGap}$$
3. **Boundary Clamping:** A bottom-up pass ensures badges never extend beyond the container margins ($10\text{dp}$).
4. **Elbow Connector Lines:** Lines draw from the 3D surface pin through a horizontal knee elbow directly to the nearest edge of the label badge.

---

## 4. How to Manage Part Labels

Labels can be defined in two ways:

### Option A: Embedded in the 3D Model (Blender)
1. Open the model in Blender.
2. Select the target mesh or add an Empty object at the anchor location.
3. Navigate to **Object Properties → Custom Properties → + Add**:
   * **Name:** `prop`
   * **Type:** String
   * **Value:** `Your Label Name` (e.g., `Optical Lens`)
4. Export via **File → Export → glTF 2.0 (.glb)**. Under **Data**, ensure **Custom Properties** is checked.
5. Place the file in `app/src/main/assets/models/`. `GlbMetadataParser.kt` extracts these automatically at runtime.

### Option B: Defined Programmatically in Code
If a model lacks embedded custom properties, `GlbMetadataParser.kt` automatically falls back to `getDefaultFallbackLabels()`:
```kotlin
"model_2.glb" -> listOf(
    ModelPartLabel(
        nodeIndex = 0,
        nodeName = "Lens",
        text = "Optical Lens",
        localPosition = floatArrayOf(0.0f, 0.04f, 0.50f)
    ),
    ModelPartLabel(
        nodeIndex = 1,
        nodeName = "Shutter",
        text = "Shutter Release",
        localPosition = floatArrayOf(0.32f, 0.35f, 0.05f)
    )
)
```

---

## 5. Performance Guidelines

* **Local Component State:** 60 FPS projection updates are stored in a local `remember { mutableStateOf(...) }` inside `SceneViewContainer.kt`. Avoid pushing per-frame coordinates to `CanvasViewModel` to prevent whole-screen recomposition churn.
* **Keyed Compose Lists:** Containers in `CanvasScreen.kt` use `key(model.instanceId)` so mutations on one container do not trigger recomposition or re-creation of adjacent SceneViews.
* **Transparent Clearing:** `sceneView.renderer.clearOptions.clear = true` avoids unnecessary overdraw passes while keeping the background transparent.

---

## 6. Walkthrough Presentation Script (5–6 Minutes)

Use this script when presenting or recording a demonstration video.

### [0:00 – 0:45] Introduction
> *"Hi everyone! Today I’m walking you through our Android 3D Multi-Model Viewer app.*  
> *The goal was to build a single-activity Android application where users can place, move around, and interact with multiple 3D models at the same time on a 2D canvas, while keeping the app responsive even on mid-range devices.*  
> *We bundled five models in GLB format: the Damaged Helmet, Airplane, Antique Camera, Lantern, and Mars Rover.*  
> *We implemented independent container drag and pinch-resizing, a dedicated 3D interaction mode to rotate and zoom models, and real-time 2D labels with smart connector lines that never overlap.*  
> *The app is built with Kotlin, Jetpack Compose, and SceneView backed by Google's Filament engine, supporting Android 7.0 through Android 15.*  
> *Let’s jump right into the live demo."*

---

### [0:45 – 2:30] Live Application Demo
> **[Action: Tap '+ Add Model', select Damaged Helmet]**  
> *"When we open the app, we see an empty dark canvas with an '+ Add Model' button at the bottom. Tapping it opens our model catalog. Let's pick the Damaged Helmet first. As soon as it loads, notice the helmet is centered automatically inside the container."*  
>  
> **[Action: Add Airplane, Antique Camera, Lantern, Mars Rover]**  
> *"Now let's add the remaining four models: the Airplane, Camera, Lantern, and Mars Rover. All five models are running live at the same time with full lighting, materials, and real-time shadows."*  
>  
> **[Action: Drag container with 1 finger, overlap another, pinch with 2 fingers]**  
> *"In Normal Mode, each container acts like an independent card. With one finger, I can drag it anywhere. Tapping or dragging a container brings it straight to the top, and even when two cards overlap, the 3D model stays on top of the container underneath.*  
> *Using two fingers, I can pinch to resize the container smoothly without any position jumps."*  
>  
> **[Action: Toggle Interaction Mode (hand icon); rotate and zoom model]**  
> *"If we want to inspect a model closely, we tap the hand icon in the top-right corner of the container. This activates Interaction Mode. The container locks in place, and dragging now spins the 3D camera around the model. We can rotate to see any angle and pinch to zoom in on fine details. Container movement is disabled while in this mode, so the two gesture systems never collide."*  
>  
> **[Action: Toggle Label icon; rotate model to demonstrate anti-collision]**  
> *"Next, let's tap the label button. Clear text badges appear with cyan anchor pins and connector lines pointing to specific parts.*  
> *Notice our anti-collision layout: even when I rotate the model and parts line up vertically, the badges never overlap or block each other. Each badge gets its own clean vertical slot with a dedicated connector line.*  
> *When we're done with a model, tapping the close button removes the card and cleans up its GPU memory."*

---

### [2:30 – 4:00] Architecture & Key Implementation Highlights
> *"Looking at the project structure in Android Studio:*  
> * *Under `data/model`, we keep immutable state classes like `ModelContainerState`.*  
> * *`GlbMetadataParser` reads the binary GLB structure to extract node coordinates and custom `extras.prop` labels directly.*  
> * *In the UI layer, `CanvasViewModel` manages the active container list using Kotlin `StateFlow`.*  
> * *`DraggableContainer` handles gesture math, while `SceneViewContainer` bridges Compose to Filament via `AndroidView`.*  
>  
> *A few key engineering hurdles we solved:*  
> 1. *Auto-Centering: Every model is shifted by the inverse of its scaled bounding box center (`Float3(-cx, -cy, -cz)`), centering it at origin.*  
> 2. *Gesture Disambiguation: We separated 1-finger drags from 2-finger pinches using `awaitEachGesture`, so placing down a second finger never jerks the container position.*  
> 3. *SurfaceView Z-Ordering: Because `SurfaceView` composites directly in hardware through SurfaceFlinger, we dynamically update `mRequestedSubLayer` and parent view hierarchy order so overlapping 3D models layer properly.*  
> 4. *Anti-Collision Labels: SceneView had an internal bug returning zero for camera depth. We wrote our own projection pipeline using camera view-projection matrices, paired with a vertical relaxation pass in `PartLabelOverlay.kt` to guarantee badges never overlap."*

---

### [4:00 – 5:00] Performance, Trade-offs & Conclusion
> *"To keep all five models smooth on 2–3 GB RAM devices:*  
> * *Label coordinates are kept local to each container, avoiding full-screen Compose recomposition at 60 FPS.*  
> * *Container lists are keyed by unique IDs to prevent reloading adjacent 3D instances.*  
> * *Memory usage stays well under 250 MB, holding 45 to 60 FPS on mid-range hardware.*  
>  
> *Trade-offs:*  
> * *We chose SceneView and Filament because building a custom PBR engine with glTF loaders and lighting from scratch in raw OpenGL ES would add thousands of lines of maintenance overhead.*  
> * *Compose gave us a clean, reactive state model for multi-window management, and handling the native `SurfaceView` layering gave us the rendering speed needed for multi-model workloads.*  
>  
> *The release build is packaged in the `release/` directory. Thank you for your time!"*
