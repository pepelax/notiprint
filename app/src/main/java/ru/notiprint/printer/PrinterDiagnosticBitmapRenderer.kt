package ru.notiprint.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * A deliberately dense test chart. All dark areas are drawn as exact black pixels,
 * so it helps distinguish a printer/head issue from thin antialiased text strokes.
 */
object PrinterDiagnosticBitmapRenderer {
    // Match notification receipts: the printer itself already has paper-side margins.
    private const val LEFT = 4
    private const val RIGHT = EscPosRasterEncoder.PRINT_WIDTH_DOTS - LEFT
    private const val WIDTH = RIGHT - LEFT

    fun render(): Bitmap = Bitmap.createBitmap(
        EscPosRasterEncoder.PRINT_WIDTH_DOTS,
        610,
        Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 22f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 16f
        }
        val solidPaint = Paint().apply { color = Color.BLACK }

        var top = 24
        canvas.drawText("ПРОВЕРКА КАЧЕСТВА ПЕЧАТИ", LEFT.toFloat(), top.toFloat(), titlePaint)
        top += 24
        canvas.drawText("Плотные зоны и тест термоголовки", LEFT.toFloat(), top.toFloat(), labelPaint)
        top += 22

        canvas.drawText("Короткая заливка 100%", LEFT.toFloat(), top.toFloat(), labelPaint)
        top += 8
        canvas.drawRect(LEFT.toFloat(), top.toFloat(), RIGHT.toFloat(), (top + 18).toFloat(), solidPaint)
        top += 44

        canvas.drawText("Чёрные блоки с белыми промежутками", LEFT.toFloat(), top.toFloat(), labelPaint)
        top += 8
        drawSeparatedBlocks(canvas, top, solidPaint)
        top += 48

        canvas.drawText("Шахматное поле 50%", LEFT.toFloat(), top.toFloat(), labelPaint)
        top += 8
        drawCheckerboard(canvas, top, cell = 8, paint = solidPaint)
        top += 82

        canvas.drawText("Плотность 75%", LEFT.toFloat(), top.toFloat(), labelPaint)
        top += 8
        drawDotPattern(canvas, top, blackPixels = 3, paint = solidPaint)
        top += 82

        canvas.drawText("Плотность 25%", LEFT.toFloat(), top.toFloat(), labelPaint)
        top += 8
        drawDotPattern(canvas, top, blackPixels = 1, paint = solidPaint)
        top += 88

        canvas.drawText("Вертикальные линии: пропуски хорошо заметны", LEFT.toFloat(), top.toFloat(), labelPaint)
        top += 8
        drawVerticalLines(canvas, top, solidPaint)
    }

    private fun drawSeparatedBlocks(canvas: Canvas, top: Int, paint: Paint) {
        val blockWidth = 18
        val gap = 6
        var left = LEFT
        while (left + blockWidth <= RIGHT) {
            canvas.drawRect(left.toFloat(), top.toFloat(), (left + blockWidth).toFloat(), (top + 32).toFloat(), paint)
            left += blockWidth + gap
        }
    }

    private fun drawCheckerboard(canvas: Canvas, top: Int, cell: Int, paint: Paint) {
        val rows = 8
        val columns = WIDTH / cell
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if ((row + column) % 2 == 0) {
                    val left = LEFT + column * cell
                    val y = top + row * cell
                    canvas.drawRect(left.toFloat(), y.toFloat(), (left + cell).toFloat(), (y + cell).toFloat(), paint)
                }
            }
        }
    }

    private fun drawDotPattern(canvas: Canvas, top: Int, blackPixels: Int, paint: Paint) {
        val rows = 32
        for (row in 0 until rows) {
            for (column in 0 until WIDTH) {
                if ((column + row * 2) % 4 < blackPixels) {
                    canvas.drawPoint((LEFT + column).toFloat(), (top + row).toFloat(), paint)
                }
            }
        }
    }

    private fun drawVerticalLines(canvas: Canvas, top: Int, paint: Paint) {
        var x = LEFT
        while (x < RIGHT) {
            canvas.drawRect(x.toFloat(), top.toFloat(), (x + 2).toFloat(), (top + 52).toFloat(), paint)
            x += 6
        }
    }
}
