package com.printwise.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Drawing size of the sample; [SampleSheet.render] scales it to whatever width is asked for. */
private const val SHEET_WIDTH = 1800f
private const val SHEET_HEIGHT = 2400f

/**
 * A birthday topper sheet drawn in code, for the guided tour: seven solid pieces on white, spaced apart like a
 * real printable so detection finds each one. Drawn rather than bundled, so there's no artwork licence to track.
 * At full size the tallest piece has enough pixels to print sharp at the default 3 in.
 */
object SampleSheet {
    fun render(width: Int = SHEET_WIDTH.toInt()): Bitmap {
        val scale = width / SHEET_WIDTH
        val bitmap = Bitmap.createBitmap(width, (SHEET_HEIGHT * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        banner(canvas)
        numberBadge(canvas)
        star(canvas, cx = 1290f, cy = 900f, outer = 300f, color = 0xFF3563F5.toInt())
        crown(canvas)
        heart(canvas)
        namePlaque(canvas)
        balloon(canvas)
        return bitmap
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun text(color: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /** Draws [value] centred on (cx, cy) by its glyph bounds, not the font's line box. */
    private fun Canvas.centredText(value: String, cx: Float, cy: Float, paint: Paint) {
        val bounds = android.graphics.Rect()
        paint.getTextBounds(value, 0, value.length, bounds)
        drawText(value, cx, cy - bounds.exactCenterY(), paint)
    }

    /** "Happy Birthday" on a ribbon with notched tails. */
    private fun banner(canvas: Canvas) {
        val pink = 0xFFE85D8E.toInt()
        val tails = Path().apply {
            moveTo(250f, 240f); lineTo(420f, 240f); lineTo(420f, 470f); lineTo(250f, 470f); lineTo(320f, 355f); close()
            moveTo(1550f, 240f); lineTo(1380f, 240f); lineTo(1380f, 470f); lineTo(1550f, 470f); lineTo(1480f, 355f); close()
        }
        canvas.drawPath(tails, fill(0xFFC23F70.toInt()))
        canvas.drawRoundRect(RectF(360f, 150f, 1440f, 420f), 48f, 48f, fill(pink))
        canvas.centredText("Happy Birthday", 900f, 285f, text(Color.WHITE, 128f))
    }

    /** A gold circle with a big "5". */
    private fun numberBadge(canvas: Canvas) {
        canvas.drawCircle(520f, 900f, 300f, fill(0xFFE9A23B.toInt()))
        canvas.drawCircle(520f, 900f, 250f, fill(0xFFF5C451.toInt()))
        canvas.centredText("5", 520f, 900f, text(Color.WHITE, 400f))
    }

    private fun star(canvas: Canvas, cx: Float, cy: Float, outer: Float, color: Int) {
        val inner = outer * 0.45f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = -PI / 2 + i * PI / 5
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, fill(color))
    }

    /** A three-point crown with a jewel on each point. */
    private fun crown(canvas: Canvas) {
        val orange = 0xFFFF914D.toInt()
        val path = Path().apply {
            moveTo(190f, 1760f)
            lineTo(190f, 1440f); lineTo(330f, 1580f)
            lineTo(460f, 1380f); lineTo(590f, 1580f)
            lineTo(730f, 1440f); lineTo(730f, 1760f)
            close()
        }
        canvas.drawPath(path, fill(orange))
        canvas.drawRect(190f, 1690f, 730f, 1760f, fill(0xFFE5732F.toInt()))
        listOf(190f to 1430f, 460f to 1370f, 730f to 1430f).forEach { (x, y) -> canvas.drawCircle(x, y, 34f, fill(orange)) }
    }

    private fun heart(canvas: Canvas) {
        val path = Path().apply {
            moveTo(1290f, 1810f)
            cubicTo(1060f, 1660f, 1050f, 1460f, 1180f, 1400f)
            cubicTo(1250f, 1370f, 1290f, 1420f, 1290f, 1470f)
            cubicTo(1290f, 1420f, 1330f, 1370f, 1400f, 1400f)
            cubicTo(1530f, 1460f, 1520f, 1660f, 1290f, 1810f)
            close()
        }
        canvas.drawPath(path, fill(0xFF20C9B0.toInt()))
    }

    /** A name plate, the kind of piece people retype for each party. */
    private fun namePlaque(canvas: Canvas) {
        canvas.drawRoundRect(RectF(170f, 1950f, 790f, 2230f), 140f, 140f, fill(0xFF8B5CF6.toInt()))
        canvas.centredText("Mia", 480f, 2090f, text(Color.WHITE, 170f))
    }

    /** A balloon with its string, all one connected shape so it is found as a single piece. */
    private fun balloon(canvas: Canvas) {
        val red = 0xFFE5484D.toInt()
        canvas.drawOval(RectF(1140f, 1900f, 1440f, 2250f), fill(red))
        canvas.drawPath(Path().apply { moveTo(1290f, 2240f); lineTo(1255f, 2290f); lineTo(1325f, 2290f); close() }, fill(red))
        val string = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9A3438.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(Path().apply { moveTo(1290f, 2285f); cubicTo(1255f, 2305f, 1325f, 2325f, 1290f, 2345f) }, string)
    }
}
