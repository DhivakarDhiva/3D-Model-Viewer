package com.infusory.modelviewer.data.parser

import android.content.Context
import android.util.Log
import com.infusory.modelviewer.data.model.ModelPartLabel
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight binary glTF / GLB parser designed to extract node metadata and
 * custom part labels (extras.prop) without loading entire mesh geometry into memory.
 */
object GlbMetadataParser {

    private const val TAG = "GlbMetadataParser"
    private const val GLB_MAGIC = 0x46546C67 // "glTF" in little endian
    private const val CHUNK_TYPE_JSON = 0x4E4F534A // "JSON" in little endian

    fun parseLabelsFromAsset(context: Context, assetPath: String): List<ModelPartLabel> {
        val extracted = try {
            context.assets.open(assetPath).use { inputStream ->
                parseLabels(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse labels from asset: $assetPath", e)
            emptyList()
        }
        return if (extracted.isNotEmpty()) extracted else getDefaultFallbackLabels(assetPath)
    }

    fun parseLabels(inputStream: InputStream): List<ModelPartLabel> {
        val headerBuffer = ByteArray(12)
        var bytesRead = inputStream.read(headerBuffer)
        if (bytesRead < 12) return emptyList()

        val header = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val magic = header.int
        val version = header.int
        val totalLength = header.int

        if (magic != GLB_MAGIC || version != 2) {
            Log.w(TAG, "Invalid GLB header. Magic: $magic, Version: $version")
            return emptyList()
        }

        val chunkHeaderBuffer = ByteArray(8)
        bytesRead = inputStream.read(chunkHeaderBuffer)
        if (bytesRead < 8) return emptyList()

        val chunkHeader = ByteBuffer.wrap(chunkHeaderBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val chunkLength = chunkHeader.int
        val chunkType = chunkHeader.int

        if (chunkType != CHUNK_TYPE_JSON) {
            Log.w(TAG, "First chunk is not JSON chunk: $chunkType")
            return emptyList()
        }

        val jsonBuffer = ByteArray(chunkLength)
        var offset = 0
        while (offset < chunkLength) {
            val count = inputStream.read(jsonBuffer, offset, chunkLength - offset)
            if (count == -1) break
            offset += count
        }

        val jsonString = String(jsonBuffer, 0, offset, Charsets.UTF_8)
        return extractLabelsFromJson(jsonString)
    }

    private fun extractLabelsFromJson(jsonString: String): List<ModelPartLabel> {
        val labels = mutableListOf<ModelPartLabel>()
        try {
            val root = JSONObject(jsonString)
            if (!root.has("nodes")) return labels

            val nodes = root.getJSONArray("nodes")
            for (i in 0 until nodes.length()) {
                val node = nodes.getJSONObject(i)
                val nodeName = node.optString("name", "Node_$i")

                if (node.has("extras")) {
                    val extras = node.getJSONObject("extras")
                    if (extras.has("prop")) {
                        val propText = extras.getString("prop")
                        
                        // Extract translation if present, default to origin (0,0,0)
                        val translation = floatArrayOf(0f, 0f, 0f)
                        if (node.has("translation")) {
                            val transArr = node.getJSONArray("translation")
                            if (transArr.length() >= 3) {
                                translation[0] = transArr.getDouble(0).toFloat()
                                translation[1] = transArr.getDouble(1).toFloat()
                                translation[2] = transArr.getDouble(2).toFloat()
                            }
                        }

                        labels.add(
                            ModelPartLabel(
                                nodeIndex = i,
                                nodeName = nodeName,
                                text = propText,
                                localPosition = translation
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing JSON chunk for labels", e)
        }
        return labels
    }

    /**
     * Provides meaningful fallback part labels for bundled 3D models when the
     * GLB file does not contain embedded extras.prop custom properties.
     */
    fun getDefaultFallbackLabels(assetPath: String): List<ModelPartLabel> {
        val fileName = assetPath.substringAfterLast("/")
        return when (fileName) {
            "model_1.glb" -> listOf(
                ModelPartLabel(nodeIndex = 0, nodeName = "Cockpit", text = "Cockpit & Canopy", localPosition = floatArrayOf(0.0f, 0.22f, 0.35f)),
                ModelPartLabel(nodeIndex = 1, nodeName = "Wing_Port", text = "Port Wing", localPosition = floatArrayOf(-0.65f, 0.04f, -0.05f)),
                ModelPartLabel(nodeIndex = 2, nodeName = "Wing_Starboard", text = "Starboard Wing", localPosition = floatArrayOf(0.65f, 0.04f, -0.05f)),
                ModelPartLabel(nodeIndex = 3, nodeName = "Tail", text = "Vertical Tail", localPosition = floatArrayOf(0.0f, 0.38f, -0.65f))
            )
            "model_2.glb" -> listOf(
                ModelPartLabel(nodeIndex = 0, nodeName = "Lens", text = "Optical Lens", localPosition = floatArrayOf(0.0f, 0.04f, 0.50f)),
                ModelPartLabel(nodeIndex = 1, nodeName = "Shutter", text = "Shutter Release", localPosition = floatArrayOf(0.32f, 0.35f, 0.05f)),
                ModelPartLabel(nodeIndex = 2, nodeName = "Viewfinder", text = "Viewfinder", localPosition = floatArrayOf(-0.22f, 0.38f, 0.02f)),
                ModelPartLabel(nodeIndex = 3, nodeName = "Focus", text = "Focus Ring", localPosition = floatArrayOf(-0.25f, 0.04f, 0.28f))
            )
            "model_3.glb" -> listOf(
                ModelPartLabel(nodeIndex = 0, nodeName = "Visor", text = "Face Visor", localPosition = floatArrayOf(0.0f, 0.06f, 0.45f)),
                ModelPartLabel(nodeIndex = 1, nodeName = "Helmet_Shell", text = "Outer Shell", localPosition = floatArrayOf(0.0f, 0.46f, -0.05f)),
                ModelPartLabel(nodeIndex = 2, nodeName = "Vent", text = "Right Vent", localPosition = floatArrayOf(0.32f, -0.15f, 0.22f)),
                ModelPartLabel(nodeIndex = 3, nodeName = "Comms", text = "Left Audio Comms", localPosition = floatArrayOf(-0.38f, 0.02f, 0.05f))
            )
            "model_4.glb" -> listOf(
                ModelPartLabel(nodeIndex = 0, nodeName = "Handle", text = "Carry Handle", localPosition = floatArrayOf(0.0f, 0.62f, 0.0f)),
                ModelPartLabel(nodeIndex = 1, nodeName = "Chimney", text = "Glass Chimney", localPosition = floatArrayOf(0.18f, 0.15f, 0.22f)),
                ModelPartLabel(nodeIndex = 2, nodeName = "Fuel_Tank", text = "Fuel Base", localPosition = floatArrayOf(-0.22f, -0.38f, 0.10f)),
                ModelPartLabel(nodeIndex = 3, nodeName = "Vent_Hood", text = "Top Vent", localPosition = floatArrayOf(-0.25f, 0.42f, -0.10f))
            )
            "model_5.glb" -> listOf(
                ModelPartLabel(nodeIndex = 0, nodeName = "Mastcam", text = "Mastcam Sensor", localPosition = floatArrayOf(0.0f, 0.55f, 0.18f)),
                ModelPartLabel(nodeIndex = 1, nodeName = "Suspension", text = "Suspension Bogie", localPosition = floatArrayOf(-0.48f, -0.22f, 0.05f)),
                ModelPartLabel(nodeIndex = 2, nodeName = "Arm", text = "Robotic Arm", localPosition = floatArrayOf(0.38f, 0.05f, 0.42f)),
                ModelPartLabel(nodeIndex = 3, nodeName = "Antenna", text = "High-Gain Antenna", localPosition = floatArrayOf(-0.22f, 0.45f, -0.32f))
            )
            else -> emptyList()
        }
    }
}
