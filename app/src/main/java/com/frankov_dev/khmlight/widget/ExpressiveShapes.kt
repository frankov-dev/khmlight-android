package com.frankov_dev.khmlight.widget

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Designer-accurate cubic Bezier paths for all 5 Material 3 Expressive Badge shapes (B1-B5)
 * matching the reference design spec, normalized in [0..100] x [0..100] and scaled into any RectF.
 */
object ExpressiveShapes {

    private val unitRect = RectF(0f, 0f, 100f, 100f)

    fun buildPath(shape: BadgeShape, target: RectF): Path {
        val raw = when (shape) {
            BadgeShape.B1_SQUIRCLE -> buildSquircle100()
            BadgeShape.B2_WAVE -> buildWave100()
            BadgeShape.B3_BLOB -> buildBlob100()
            BadgeShape.B4_CLOUD -> buildCloud100()
            BadgeShape.B5_ORGANIC -> buildOrganic100()
        }
        val matrix = Matrix().apply {
            setRectToRect(unitRect, target, Matrix.ScaleToFit.FILL)
        }
        raw.transform(matrix)
        return raw
    }

    private fun buildSquircle100(): Path = Path().apply {
        moveTo(50f, 2f)
        cubicTo(85f, 2f, 98f, 14f, 98f, 50f)
        cubicTo(98f, 86f, 85f, 98f, 50f, 98f)
        cubicTo(15f, 98f, 2f, 86f, 2f, 50f)
        cubicTo(2f, 14f, 15f, 2f, 50f, 2f)
        close()
    }

    private fun buildWave100(): Path = Path().apply {
        // B2: Smooth symmetrical peanut/wave pinched at top-center (50,15) and bottom-center (50,85)
        moveTo(50f, 15f)
        cubicTo(60f, 15f, 65f, 2f, 76f, 2f)
        cubicTo(90f, 2f, 98f, 20f, 98f, 50f)
        cubicTo(98f, 80f, 90f, 98f, 76f, 98f)
        cubicTo(65f, 98f, 60f, 85f, 50f, 85f)
        cubicTo(40f, 85f, 35f, 98f, 24f, 98f)
        cubicTo(10f, 98f, 2f, 80f, 2f, 50f)
        cubicTo(2f, 20f, 10f, 2f, 24f, 2f)
        cubicTo(35f, 2f, 40f, 15f, 50f, 15f)
        close()
    }

    private fun buildBlob100(): Path = Path().apply {
        // B3: Asymmetrical organic blob — fuller rounded left half, gentle slope & lower-right lobe
        moveTo(35f, 2f)
        cubicTo(62f, 2f, 78f, 14f, 86f, 28f)
        cubicTo(92f, 39f, 98f, 55f, 98f, 72f)
        cubicTo(98f, 89f, 90f, 98f, 80f, 98f)
        cubicTo(71f, 98f, 65f, 91f, 55f, 91f)
        cubicTo(45f, 91f, 38f, 95f, 28f, 95f)
        cubicTo(11f, 95f, 2f, 76f, 2f, 48f)
        cubicTo(2f, 18f, 13f, 2f, 35f, 2f)
        close()
    }

    private fun buildCloud100(): Path = Path().apply {
        // B4: Fluffy 8-lobe cloud with outward convex cubic puffs and soft dimple valleys
        val lobes = 8
        val cx = 50f
        val cy = 50f
        val rValleyX = 38.5f
        val rValleyY = 36.5f
        val rPeakX = 51.5f
        val rPeakY = 51.5f

        val step = (2.0 * Math.PI) / lobes
        val startOffset = Math.PI / lobes

        for (i in 0 until lobes) {
            val a1 = i * step + startOffset
            val a2 = (i + 1) * step + startOffset
            val aMid1 = a1 + step * 0.30
            val aMid2 = a1 + step * 0.70

            val vx1 = cx + (rValleyX * cos(a1)).toFloat()
            val vy1 = cy + (rValleyY * sin(a1)).toFloat()
            val vx2 = cx + (rValleyX * cos(a2)).toFloat()
            val vy2 = cy + (rValleyY * sin(a2)).toFloat()

            val c1x = cx + (rPeakX * cos(aMid1)).toFloat()
            val c1y = cy + (rPeakY * sin(aMid1)).toFloat()
            val c2x = cx + (rPeakX * cos(aMid2)).toFloat()
            val c2y = cy + (rPeakY * sin(aMid2)).toFloat()

            if (i == 0) moveTo(vx1, vy1)
            cubicTo(c1x, c1y, c2x, c2y, vx2, vy2)
        }
        close()
    }

    private fun buildOrganic100(): Path = Path().apply {
        // B5: Elongated asymmetrical organic wave-pebble
        moveTo(24f, 2f)
        cubicTo(36f, 2f, 42f, 17f, 52f, 17f)
        cubicTo(62f, 17f, 68f, 8f, 78f, 8f)
        cubicTo(91f, 8f, 98f, 26f, 98f, 54f)
        cubicTo(98f, 82f, 88f, 98f, 72f, 98f)
        cubicTo(60f, 98f, 54f, 87f, 44f, 87f)
        cubicTo(34f, 87f, 30f, 93f, 22f, 93f)
        cubicTo(9f, 93f, 2f, 74f, 2f, 46f)
        cubicTo(2f, 18f, 10f, 2f, 24f, 2f)
        close()
    }
}
