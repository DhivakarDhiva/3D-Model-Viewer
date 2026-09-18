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
        return try {
            context.assets.open(assetPath).use { inputStream ->
                parseLabels(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse labels from asset: $assetPath", e)
            emptyList()
        }
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
}
