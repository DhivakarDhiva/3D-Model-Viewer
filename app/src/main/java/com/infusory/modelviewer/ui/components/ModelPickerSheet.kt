package com.infusory.modelviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infusory.modelviewer.data.model.ModelAsset
import com.infusory.modelviewer.ui.theme.PrimaryAccent
import com.infusory.modelviewer.ui.theme.SurfaceCard
import com.infusory.modelviewer.ui.theme.TextMuted
import com.infusory.modelviewer.ui.theme.TextPrimary
import com.infusory.modelviewer.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    availableModels: List<ModelAsset>,
    currentCount: Int,
    onModelSelected: (ModelAsset) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select 3D Model to Add",
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Text(
                    text = "$currentCount / 5 on screen",
                    fontSize = 13.sp,
                    color = if (currentCount >= 5) Color(0xFFEF4444) else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            availableModels.forEach { model ->
                ModelItemRow(
                    model = model,
                    enabled = currentCount < 5,
                    onClick = { onModelSelected(model) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelItemRow(
    model: ModelAsset,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val rowAlpha = if (enabled) 1f else 0.4f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF222631))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ViewInAr,
            contentDescription = null,
            tint = PrimaryAccent.copy(alpha = rowAlpha),
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                color = TextPrimary.copy(alpha = rowAlpha),
                fontSize = 15.sp
            )
            Text(
                text = model.fileName,
                color = TextMuted.copy(alpha = rowAlpha),
                fontSize = 12.sp
            )
        }

        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add",
            tint = PrimaryAccent.copy(alpha = rowAlpha)
        )
    }
}
