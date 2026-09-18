package com.infusory.modelviewer.data.model

/**
 * Descriptor for bundled 3D assets available to load onto the canvas.
 */
data class ModelAsset(
    val id: String,
    val fileName: String,
    val displayName: String,
    val category: String = "Engineering"
) {
    val assetPath: String
        get() = "models/$fileName"

    companion object {
        val DEFAULT_MODELS = listOf(
            ModelAsset("model_1", "model_1.glb", "Mechanical Assembly"),
            ModelAsset("model_2", "model_2.glb", "Turbine Engine"),
            ModelAsset("model_3", "model_3.glb", "Piston Cylinder"),
            ModelAsset("model_4", "model_4.glb", "Gearbox Transmission"),
            ModelAsset("model_5", "model_5.glb", "Hydraulic Actuator")
        )
    }
}
