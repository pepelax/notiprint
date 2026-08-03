package ru.notiprint.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import ru.notiprint.data.PrintJob
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Creates a receipt-sized, monochrome-friendly layout without relying on printer code pages. */
object NotificationBitmapRenderer {
    // The printer's 384 dots already sit inside the paper's physical 5 mm side margins.
    // Keep only a tiny safety inset inside the printable area itself.
    private const val HORIZONTAL_MARGIN = 4
    private const val VERTICAL_MARGIN = 16
    private const val CONTENT_WIDTH = EscPosRasterEncoder.PRINT_WIDTH_DOTS - HORIZONTAL_MARGIN * 2

    fun render(job: PrintJob): Bitmap {
        val kindPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val timestampPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val timestamp = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()).format(Date(job.postedAt))
        val title = job.title.ifBlank { job.kind.title }
        val body = displayBody(job)
        val kindLayout = layout(job.kind.title, kindPaint)
        val timestampLayout = layout(timestamp, timestampPaint)
        val titleLayout = layout(title, titlePaint)
        val bodyLayout = if (body.isBlank()) null else layout(body, bodyPaint)
        val height = max(
            120,
            VERTICAL_MARGIN * 2 + kindLayout.height + 5 + timestampLayout.height + 14 + titleLayout.height +
                if (bodyLayout == null) 18 else 18 + bodyLayout.height + 12,
        )

        return Bitmap.createBitmap(
            EscPosRasterEncoder.PRINT_WIDTH_DOTS,
            height,
            Bitmap.Config.ARGB_8888,
        ).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            var top = VERTICAL_MARGIN.toFloat()

            top = canvas.drawLayout(kindLayout, top)
            top += 5
            top = canvas.drawLayout(timestampLayout, top)
            top += 14
            top = canvas.drawLayout(titleLayout, top)

            val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                strokeWidth = 1f
            }
            canvas.drawLine(
                HORIZONTAL_MARGIN.toFloat(),
                top + 7,
                (EscPosRasterEncoder.PRINT_WIDTH_DOTS - HORIZONTAL_MARGIN).toFloat(),
                top + 7,
                divider,
            )
            top += 18

            if (bodyLayout != null) {
                top = canvas.drawLayout(bodyLayout, top)
                top += 12
            }
        }
    }

    private fun layout(text: String, paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(5f, 1.05f)
            .build()

    private fun displayBody(job: PrintJob): String {
        val message = job.message.trim()
        if (job.kind != ru.notiprint.data.NotificationKind.MISSED_CALL) return message

        // Huawei's stock dialler provides strings such as "Звонок 0 с." as the body of a missed call.
        // It adds no useful information after the prominent missed-call heading and contact name.
        val normalized = message.lowercase(Locale.ROOT)
        return if (normalized.startsWith("звонок") || normalized.startsWith("call")) "" else message
    }

    private fun Canvas.drawLayout(layout: StaticLayout, top: Float): Float {
        save()
        translate(HORIZONTAL_MARGIN.toFloat(), top)
        layout.draw(this)
        restore()
        return top + layout.height
    }
}
