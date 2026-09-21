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
            ModelAsset("model_1", "model_1.glb", "Airplane", "Aviation"),
            ModelAsset("model_2", "model_2.glb", "Antique Camera", "Photography"),
            ModelAsset("model_3", "model_3.glb", "Damaged Helmet", "Sci-Fi"),
            ModelAsset("model_4", "model_4.glb", "Lantern", "Antiques"),
            ModelAsset("model_5", "model_5.glb", "Mars Rover", "Space Exploration")
        )
    }
}
