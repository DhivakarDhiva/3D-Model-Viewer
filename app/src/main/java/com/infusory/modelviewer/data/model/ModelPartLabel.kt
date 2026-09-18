package com.infusory.modelviewer.data.model

import androidx.compose.ui.geometry.Offset

/**
 * Represents a dynamic 3D part annotation extracted from GLB metadata extras.prop
 */
data class ModelPartLabel(
    val nodeIndex: Int,
    val nodeName: String,
    val text: String,
    val localPosition: FloatArray, // [x, y, z] anchor within local model coordinates
    val screenPosition: Offset? = null // Real-time projected (x, y) coordinates on screen
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ModelPartLabel
        return nodeIndex == other.nodeIndex &&
                nodeName == other.nodeName &&
                text == other.text &&
                localPosition.contentEquals(other.localPosition) &&
                screenPosition == other.screenPosition
    }

    override fun hashCode(): Int {
        var result = nodeIndex
        result = 31 * result + nodeName.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + localPosition.contentHashCode()
        result = 31 * result + (screenPosition?.hashCode() ?: 0)
        return result
    }
}
