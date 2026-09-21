package com.infusory.modelviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infusory.modelviewer.data.model.ModelPartLabel
import com.infusory.modelviewer.ui.theme.LabelConnectorLine
import com.infusory.modelviewer.ui.theme.LabelPillBackground
import com.infusory.modelviewer.ui.theme.LabelPillBorder
import kotlin.math.roundToInt

@Composable
fun PartLabelOverlay(
    labels: List<ModelPartLabel>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. Draw connector lines and anchor pins
        Canvas(modifier = Modifier.fillMaxSize()) {
            labels.forEach { label ->
                val anchor = label.screenPosition
                if (anchor != null) {
                    val isRightSide = anchor.x > size.width * 0.55f
                    val lineEndX = if (isRightSide) anchor.x - 26f else anchor.x + 26f
                    val lineEndY = anchor.y - 28f
                    val badgeTarget = Offset(lineEndX, lineEndY)

                    // Outer cyan ring pin on 3D part
                    drawCircle(
                        color = LabelConnectorLine,
                        radius = 4.5f,
                        center = anchor
                    )
                    // Inner white center dot for contrast
                    drawCircle(
                        color = Color.White,
                        radius = 2f,
                        center = anchor
                    )

                    // Connecting line to label pill
                    drawLine(
                        color = LabelConnectorLine,
                        start = anchor,
                        end = badgeTarget,
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 2. Render readable 2D text badges
        labels.forEach { label ->
            val anchor = label.screenPosition
            if (anchor != null) {
                val isRightSide = anchor.x > 150f
                val badgeX = if (isRightSide) {
                    (anchor.x - 110f).roundToInt().coerceAtLeast(8)
                } else {
                    (anchor.x + 26f).roundToInt().coerceAtLeast(8)
                }
                val badgeY = (anchor.y - 44f).roundToInt().coerceAtLeast(8)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(badgeX, badgeY) }
                        .background(LabelPillBackground, RoundedCornerShape(6.dp))
                        .border(1.dp, LabelPillBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label.text,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

