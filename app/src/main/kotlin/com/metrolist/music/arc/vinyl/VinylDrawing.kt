package com.metrolist.music.arc.vinyl

import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt


internal const val LABEL_RATIO = 0.3f
internal const val ARM_OUTER = 0.95f
internal const val ARM_INNER = 0.52f

internal fun DrawScope.drawRecord(
    center: Offset,
    radius: Float,
    degrees: Float,
    surface: ImageBitmap?,
    grooves: ImageBitmap?,
    art: ImageBitmap?,
    isPlaying: Boolean,
) {
    val topLeft = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt())
    val discSize = IntSize((radius * 2f).roundToInt(), (radius * 2f).roundToInt())
    val labelRadius = radius * LABEL_RATIO

    drawCircle(Color.Black.copy(alpha = 0.45f), radius * 1.005f, center + Offset(0f, 6.dp.toPx()))
    if (surface == null) drawCircle(Color(0xFF0A0A0C), radius, center)

    rotate(degrees, center) {
        surface?.let {
            drawImage(it, dstOffset = topLeft, dstSize = discSize, filterQuality = FilterQuality.Medium)
        }
    }
    grooves?.let { drawImage(it, dstOffset = topLeft, dstSize = discSize, filterQuality = FilterQuality.Medium) }

    rotate(degrees, center) {
        drawCircle(Color(0xFFE4DDCF), labelRadius, center)
        art?.let { image ->
            val side = min(image.width, image.height)
            val label = Path().apply { addOval(Rect(center, labelRadius)) }
            clipPath(label) {
                drawImage(
                    image,
                    srcOffset = IntOffset((image.width - side) / 2, (image.height - side) / 2),
                    srcSize = IntSize(side, side),
                    dstOffset = IntOffset((center.x - labelRadius).roundToInt(), (center.y - labelRadius).roundToInt()),
                    dstSize = IntSize((labelRadius * 2f).roundToInt(), (labelRadius * 2f).roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
    drawCircle(Color(0xFF0D0C0B), radius * 0.016f, center)

    if (!isPlaying) {
        val glyph = labelRadius * 0.34f
        drawCircle(Color.Black.copy(alpha = 0.5f), glyph * 1.5f, center)
        val triangle =
            Path().apply {
                moveTo(center.x - glyph * 0.5f, center.y - glyph * 0.8f)
                lineTo(center.x + glyph * 0.85f, center.y)
                lineTo(center.x - glyph * 0.5f, center.y + glyph * 0.8f)
                close()
            }
        drawPath(triangle, Color.White)
    }
}

internal class TonearmGeometry(val pivot: Offset, val tip: Offset)

internal fun needleReach(progress: Float): Float = ARM_OUTER - (ARM_OUTER - ARM_INNER) * progress.coerceIn(0f, 1f)

/** [mirrored] puts the arm's pivot on the left, for the left-handed layout. */
internal fun tonearmGeometry(center: Offset, radius: Float, width: Float, progress: Float, mirrored: Boolean = false): TonearmGeometry {
    val side = if (mirrored) -1f else 1f
    val pivot = Offset(center.x + side * width * 0.17f, center.y - radius * 1.12f)
    val toPivot = pivot - center
    val distance = toPivot.getDistance().coerceAtLeast(1f)
    val armLength = distance * 0.88f
    val reach = radius * needleReach(progress)
    val along = (distance * distance + reach * reach - armLength * armLength) / (2f * distance)
    val across = sqrt((reach * reach - along * along).coerceAtLeast(0f))
    val ux = toPivot.x / distance
    val uy = toPivot.y / distance
    return TonearmGeometry(pivot, Offset(center.x + along * ux + side * uy * across, center.y + along * uy - side * ux * across))
}

internal fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val segment = end - start
    val lengthSquared = segment.x * segment.x + segment.y * segment.y
    if (lengthSquared == 0f) return (point - start).getDistance()
    val fromStart = point - start
    val along = ((fromStart.x * segment.x + fromStart.y * segment.y) / lengthSquared).coerceIn(0f, 1f)
    return (point - (start + segment * along)).getDistance()
}

internal fun DrawScope.drawTonearm(arm: TonearmGeometry) {
    val pivot = arm.pivot
    val tip = arm.tip
    val heading = atan2(tip.y - pivot.y, tip.x - pivot.x)
    val direction = Offset(cos(heading), sin(heading))

    val thin = 1.dp.toPx()
    drawLine(
        Color.Black.copy(alpha = 0.4f), pivot + Offset(thin * 3f, thin * 6f), tip + Offset(thin * 3f, thin * 6f),
        strokeWidth = thin * 5f, cap = StrokeCap.Round,
    )
    drawLine(Color(0xFF54504A), pivot, pivot - direction * (thin * 26f), strokeWidth = thin * 7f, cap = StrokeCap.Round)
    drawLine(Color(0xFFBDB8AF), pivot, tip, strokeWidth = thin * 3.2f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.75f), pivot, tip, strokeWidth = thin * 0.9f, cap = StrokeCap.Round)

    rotate(heading * 180f / PI.toFloat(), tip) {
        drawRect(
            Color(0xFFD9D4CA),
            topLeft = tip + Offset(-thin * 2f, -thin * 4.5f),
            size = Size(thin * 17f, thin * 9f),
        )
        drawRect(
            Color(0xFFFF7A1A),
            topLeft = tip + Offset(thin * 13f, -thin * 1.5f),
            size = Size(thin * 4f, thin * 3f),
        )
    }
    drawCircle(Color(0xFF26231F), thin * 13f, pivot)
    drawCircle(Color(0xFF9D988F), thin * 5.5f, pivot)
}
