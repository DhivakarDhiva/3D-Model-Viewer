package com.infusory.modelviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infusory.modelviewer.data.model.ModelPartLabel
import com.infusory.modelviewer.ui.theme.LabelConnectorLine
import com.infusory.modelviewer.ui.theme.LabelPillBackground
import com.infusory.modelviewer.ui.theme.LabelPillBorder
import kotlin.math.roundToInt

private data class ResolvedLabelPosition(
    val label: ModelPartLabel,
    val anchor: Offset,
    val badgeX: Float,
    val badgeY: Float,
    val badgeWidth: Float,
    val badgeHeight: Float,
    val isRightSide: Boolean
)

@Composable
fun PartLabelOverlay(
    labels: List<ModelPartLabel>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        val badgeHeightPx = with(density) { 26.dp.toPx() }
        val minVerticalGapPx = with(density) { 8.dp.toPx() }
        val marginPx = with(density) { 10.dp.toPx() }

        val resolvedPositions = remember(labels, containerWidth, containerHeight) {
            computeAntiCollisionLabelLayout(
                labels = labels,
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                badgeHeight = badgeHeightPx,
                minVerticalGap = minVerticalGapPx,
                margin = marginPx
            )
        }

        // 1. Draw connecting leader lines and anchor pins on 3D surface
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineWidthPx = 2.dp.toPx()
            val outerRadiusPx = 4.5.dp.toPx()
            val innerRadiusPx = 2.dp.toPx()

            resolvedPositions.forEach { item ->
                val anchor = item.anchor
                val badgeCenterY = item.badgeY + (item.badgeHeight * 0.5f)

                // Outer cyan anchor glow ring
                drawCircle(
                    color = LabelConnectorLine,
                    radius = outerRadiusPx,
                    center = anchor
                )
                // Inner white contrast dot
                drawCircle(
                    color = Color.White,
                    radius = innerRadiusPx,
                    center = anchor
                )

                // Leader line path from 3D pin to 2D badge
                val leaderPath = Path().apply {
                    moveTo(anchor.x, anchor.y)

                    if (item.isRightSide) {
                        val targetEdgeX = item.badgeX
                        val kneeX = (anchor.x + 20f).coerceAtMost(targetEdgeX - 10f)
                        lineTo(kneeX, badgeCenterY)
                        lineTo(targetEdgeX, badgeCenterY)
                    } else {
                        val targetEdgeX = item.badgeX + item.badgeWidth
                        val kneeX = (anchor.x - 20f).coerceAtLeast(targetEdgeX + 10f)
                        lineTo(kneeX, badgeCenterY)
                        lineTo(targetEdgeX, badgeCenterY)
                    }
                }

                drawPath(
                    path = leaderPath,
                    color = LabelConnectorLine,
                    style = Stroke(
                        width = lineWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // 2. Render 2D text badges with guaranteed non-overlapping positions
        resolvedPositions.forEach { item ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(item.badgeX.roundToInt(), item.badgeY.roundToInt()) }
                    .background(LabelPillBackground, RoundedCornerShape(6.dp))
                    .border(1.dp, LabelPillBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item.label.text,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Computes an anti-collision layout for all labels currently in front of the camera.
 * Guarantees zero vertical overlap between badges while maintaining clean, legible leader lines.
 */
private fun computeAntiCollisionLabelLayout(
    labels: List<ModelPartLabel>,
    containerWidth: Float,
    containerHeight: Float,
    badgeHeight: Float,
    minVerticalGap: Float,
    margin: Float
): List<ResolvedLabelPosition> {
    val visibleLabels = labels.filter { it.screenPosition != null }
    if (visibleLabels.isEmpty() || containerWidth <= 0f || containerHeight <= 0f) {
        return emptyList()
    }

    // Partition labels into Left and Right groups based on 3D anchor projection
    val centerX = containerWidth * 0.5f
    val (rawRight, rawLeft) = visibleLabels.partition { (it.screenPosition?.x ?: 0f) >= centerX }
    val (leftGroup, rightGroup) = balanceColumns(rawLeft, rawRight)

    val resolved = mutableListOf<ResolvedLabelPosition>()

    // Solve Left Column
    resolved.addAll(
        solveColumn(
            columnLabels = leftGroup,
            isRightSide = false,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            badgeHeight = badgeHeight,
            minVerticalGap = minVerticalGap,
            margin = margin
        )
    )

    // Solve Right Column
    resolved.addAll(
        solveColumn(
            columnLabels = rightGroup,
            isRightSide = true,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            badgeHeight = badgeHeight,
            minVerticalGap = minVerticalGap,
            margin = margin
        )
    )

    return resolved
}

/**
 * Balances labels if one side has all labels and the other side is empty,
 * preventing vertical overcrowding on one side of the container.
 */
private fun balanceColumns(
    left: List<ModelPartLabel>,
    right: List<ModelPartLabel>
): Pair<List<ModelPartLabel>, List<ModelPartLabel>> {
    if (left.isEmpty() && right.size >= 4) {
        val sortedByX = right.sortedBy { it.screenPosition?.x ?: 0f }
        val toShift = sortedByX.take(2)
        val remaining = sortedByX.drop(2)
        return Pair(toShift, remaining)
    }
    if (right.isEmpty() && left.size >= 4) {
        val sortedByX = left.sortedByDescending { it.screenPosition?.x ?: 0f }
        val toShift = sortedByX.take(2)
        val remaining = sortedByX.drop(2)
        return Pair(remaining, toShift)
    }
    return Pair(left, right)
}

/**
 * Resolves vertical overlap for labels in a single column using relaxation passes.
 */
private fun solveColumn(
    columnLabels: List<ModelPartLabel>,
    isRightSide: Boolean,
    containerWidth: Float,
    containerHeight: Float,
    badgeHeight: Float,
    minVerticalGap: Float,
    margin: Float
): List<ResolvedLabelPosition> {
    if (columnLabels.isEmpty()) return emptyList()

    // Sort by anchor Y so top-most 3D parts connect to top-most badges (avoids crossing lines)
    val sorted = columnLabels.sortedBy { it.screenPosition?.y ?: 0f }

    val badgeWidths = sorted.map { label ->
        (label.text.length * 6.8f + 26f).coerceIn(75f, 160f)
    }

    val n = sorted.size
    val positionsY = FloatArray(n) { i ->
        val anchorY = sorted[i].screenPosition!!.y
        (anchorY - badgeHeight * 0.5f).coerceIn(margin, containerHeight - badgeHeight - margin)
    }

    // Pass 1: Top-to-bottom push (guarantees every badge is at least minVerticalGap below previous badge)
    for (i in 1 until n) {
        val minAllowedY = positionsY[i - 1] + badgeHeight + minVerticalGap
        if (positionsY[i] < minAllowedY) {
            positionsY[i] = minAllowedY
        }
    }

    // Pass 2: Bottom-to-top push (if bottom-most badge was pushed beyond container bottom)
    val maxAllowedBottom = containerHeight - margin - badgeHeight
    if (n > 0 && positionsY[n - 1] > maxAllowedBottom) {
        positionsY[n - 1] = maxAllowedBottom
        for (i in n - 2 downTo 0) {
            val maxAllowedY = positionsY[i + 1] - badgeHeight - minVerticalGap
            if (positionsY[i] > maxAllowedY) {
                positionsY[i] = maxAllowedY
            }
        }
    }

    // Pass 3: Clamp top to margin and re-push downwards
    if (n > 0 && positionsY[0] < margin) {
        positionsY[0] = margin
        for (i in 1 until n) {
            val minAllowedY = positionsY[i - 1] + badgeHeight + minVerticalGap
            if (positionsY[i] < minAllowedY) {
                positionsY[i] = minAllowedY
            }
        }
    }

    return sorted.mapIndexed { i, label ->
        val anchor = label.screenPosition!!
        val bWidth = badgeWidths[i]
        val bY = positionsY[i]

        val bX = if (isRightSide) {
            val desiredX = anchor.x + 36f
            desiredX.coerceIn(margin + 20f, (containerWidth - bWidth - margin).coerceAtLeast(margin))
        } else {
            val desiredX = anchor.x - bWidth - 36f
            desiredX.coerceIn(margin, (containerWidth - bWidth - margin).coerceAtLeast(margin))
        }

        ResolvedLabelPosition(
            label = label,
            anchor = anchor,
            badgeX = bX,
            badgeY = bY,
            badgeWidth = bWidth,
            badgeHeight = badgeHeight,
            isRightSide = isRightSide
        )
    }
}
