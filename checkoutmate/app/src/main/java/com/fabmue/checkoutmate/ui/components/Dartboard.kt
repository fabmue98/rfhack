package com.fabmue.checkoutmate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.fabmue.checkoutmate.domain.Dart
import com.fabmue.checkoutmate.domain.Ring
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val BOARD_ORDER = intArrayOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

// Ring radii as fractions of the board radius.
private const val R_BULL = 0.037f
private const val R_OUTER_BULL = 0.094f
private const val R_TRIPLE_IN = 0.56f
private const val R_TRIPLE_OUT = 0.63f
private const val R_DOUBLE_IN = 0.94f

private val SingleA = Color(0xFF14241C)
private val SingleB = Color(0xFF0C1813)
private val RingRed = Color(0xFFC62828)
private val RingGreen = Color(0xFF2E7D32)
private val Separator = Color(0x33000000)
private val BullGreen = Color(0xFF2E7D32)
private val BullRed = Color(0xFFC62828)

private fun dartFor(dx: Float, dy: Float, boardR: Float): Dart {
    val dist = hypot(dx, dy)
    val frac = dist / boardR
    if (frac > 1f) return Dart.MISS
    if (frac <= R_BULL) return Dart.BULL
    if (frac <= R_OUTER_BULL) return Dart.OUTER_BULL
    val deg = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0
    val shifted = (deg - 270.0 + 9.0 + 360.0) % 360.0
    val idx = (shifted / 18.0).toInt().coerceIn(0, 19)
    val number = BOARD_ORDER[idx]
    val ring = when {
        frac <= R_TRIPLE_IN -> Ring.SINGLE
        frac <= R_TRIPLE_OUT -> Ring.TRIPLE
        frac <= R_DOUBLE_IN -> Ring.SINGLE
        else -> Ring.DOUBLE
    }
    return Dart(number, ring)
}

@Composable
fun Dartboard(
    onDart: (Dart) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val boardR = minOf(size.width, size.height) / 2f * 0.84f
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    onDart(dartFor(offset.x - cx, offset.y - cy, boardR))
                }
            }
    ) {
        val boardR = size.minDimension / 2f * 0.84f
        val center = Offset(size.width / 2f, size.height / 2f)

        fun ringStroke(radiusFrac: Float, thicknessFrac: Float, color: Color, start: Float) {
            val radius = boardR * radiusFrac
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = 18f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = boardR * thicknessFrac),
            )
        }

        // Single wedges (alternating shades)
        for (i in 0 until 20) {
            val start = ((270f + i * 18f) % 360f) - 9f
            drawArc(
                color = if (i % 2 == 0) SingleA else SingleB,
                startAngle = start,
                sweepAngle = 18f,
                useCenter = true,
                topLeft = Offset(center.x - boardR, center.y - boardR),
                size = Size(boardR * 2, boardR * 2),
            )
        }
        // Triple and double rings
        val tripleMid = (R_TRIPLE_IN + R_TRIPLE_OUT) / 2f
        val tripleThick = R_TRIPLE_OUT - R_TRIPLE_IN
        val doubleMid = (R_DOUBLE_IN + 1f) / 2f
        val doubleThick = 1f - R_DOUBLE_IN
        for (i in 0 until 20) {
            val start = ((270f + i * 18f) % 360f) - 9f
            val color = if (i % 2 == 0) RingRed else RingGreen
            ringStroke(tripleMid, tripleThick, color, start)
            ringStroke(doubleMid, doubleThick, color, start)
        }
        // Sector separators
        for (i in 0 until 20) {
            val boundary = Math.toRadians((270.0 + 9.0 + i * 18.0))
            val end = Offset(
                center.x + (cos(boundary) * boardR).toFloat(),
                center.y + (sin(boundary) * boardR).toFloat(),
            )
            drawLine(Separator, center, end, strokeWidth = 1.5f)
        }
        // Bull
        drawCircle(BullGreen, boardR * R_OUTER_BULL, center)
        drawCircle(BullRed, boardR * R_BULL, center)

        // Numbers around the rim
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = boardR * 0.12f
                isFakeBoldText = true
            }
            for (i in 0 until 20) {
                val deg = (270.0 + i * 18.0)
                val rad = Math.toRadians(deg)
                val r = boardR * 1.07f
                val x = center.x + (cos(rad) * r).toFloat()
                val y = center.y + (sin(rad) * r).toFloat() + paint.textSize * 0.35f
                canvas.nativeCanvas.drawText(BOARD_ORDER[i].toString(), x, y, paint)
            }
        }
    }
}
