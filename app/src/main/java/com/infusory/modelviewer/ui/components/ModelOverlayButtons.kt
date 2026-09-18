package com.infusory.modelviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LabelOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.infusory.modelviewer.ui.theme.DangerRed
import com.infusory.modelviewer.ui.theme.PrimaryAccent
import com.infusory.modelviewer.ui.theme.TextMuted
import com.infusory.modelviewer.ui.theme.TextPrimary

@Composable
fun ModelOverlayButtons(
    isInteractionMode: Boolean,
    showLabels: Boolean,
    onToggleInteraction: () -> Unit,
    onToggleLabels: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Interaction Mode Toggle Button
        ActionButton(
            icon = if (isInteractionMode) Icons.Default.RotateRight else Icons.Default.PanTool,
            contentDescription = if (isInteractionMode) "3D Rotation Mode Active" else "Container Drag Mode Active",
            isActive = isInteractionMode,
            activeColor = PrimaryAccent,
            onClick = onToggleInteraction
        )

        // 2. Label Toggle Button
        ActionButton(
            icon = if (showLabels) Icons.Default.Label else Icons.Default.LabelOff,
            contentDescription = if (showLabels) "Hide Labels" else "Show Labels",
            isActive = showLabels,
            activeColor = Color(0xFF38BDF8),
            onClick = onToggleLabels
        )

        // 3. Close Button
        ActionButton(
            icon = Icons.Default.Close,
            contentDescription = "Close Model and Free Memory",
            isActive = false,
            activeColor = DangerRed,
            customBackgroundColor = Color(0x66EF4444),
            customIconColor = Color.White,
            onClick = onClose
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    activeColor: Color,
    customBackgroundColor: Color? = null,
    customIconColor: Color? = null,
    onClick: () -> Unit
) {
    val bgColor = customBackgroundColor ?: if (isActive) activeColor else Color(0xCC1F2937)
    val iconColor = customIconColor ?: if (isActive) Color.White else TextPrimary

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(17.dp)
        )
    }
}
