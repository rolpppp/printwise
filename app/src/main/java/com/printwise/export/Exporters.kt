package com.printwise.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.printwise.image.ImageLoader
import com.printwise.image.Upscaler
import com.printwise.layout.PageLayout
import com.printwise.model.MARGIN_IN
import com.printwise.model.PaperSize
import com.printwise.model.PrintPiece
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val POINTS_PER_INCH = 72f
private const val EMU_PER_INCH = 914_400f
private const val TWIPS_PER_INCH = 1440f

fun exportFileName(extension: String): String =
    "Printwise-${SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())}.$extension"

/**
 * Crops each placed piece once from the picture it came from ([sources] by sheet id), so a piece printed 10 times
 * is embedded once. Placements carry their own size, so a copy resized on its own still uses this one crop; the
 * crop is upscaled for the largest copy on the pages, so none of them gets left for the printer to stretch.
 */
private fun cropPieces(sources: Map<String, Bitmap>, pieces: List<PrintPiece>, layout: PageLayout): Map<String, Bitmap> {
    val tallest = layout.placements.groupBy { it.pieceId }.mapValues { (_, placed) -> placed.maxOf { it.heightIn } }
    return pieces.mapNotNull { piece ->
        val printHeightIn = tallest[piece.id] ?: return@mapNotNull null
        val source = sources[piece.sourceId] ?: return@mapNotNull null
        ImageLoader.crop(source, piece.box)?.let { piece.id to Upscaler.forPrint(it, printHeightIn) }
    }.toMap()
}

object PdfExporter {
    fun render(sources: Map<String, Bitmap>, pieces: List<PrintPiece>, layout: PageLayout): ByteArray {
        val crops = cropPieces(sources, pieces, layout)
        val widthPt = (layout.pageWidthIn * POINTS_PER_INCH).toInt()
        val heightPt = (layout.pageHeightIn * POINTS_PER_INCH).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val document = PdfDocument()
        try {
            for (pageIndex in 0 until maxOf(1, layout.pageCount)) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(widthPt, heightPt, pageIndex + 1).create())
                layout.placements.filter { it.page == pageIndex }.forEach { placed ->
                    val bitmap = crops[placed.pieceId] ?: return@forEach
                    page.canvas.drawBitmap(
                        bitmap, null,
                        RectF(
                            placed.xIn * POINTS_PER_INCH, placed.yIn * POINTS_PER_INCH,
                            (placed.xIn + placed.widthIn) * POINTS_PER_INCH, (placed.yIn + placed.heightIn) * POINTS_PER_INCH
                        ),
                        paint
                    )
                }
                document.finishPage(page)
            }
            return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
    }
}

object DocxExporter {
    fun render(sources: Map<String, Bitmap>, pieces: List<PrintPiece>, layout: PageLayout): ByteArray {
        val crops = cropPieces(sources, pieces, layout)
        val relIds = crops.keys.withIndex().associate { (index, id) -> id to "rIdImg${index + 1}" }
        val mediaNames = crops.keys.withIndex().associate { (index, id) -> id to "piece${index + 1}.png" }
        var drawingId = 1
        val body = StringBuilder()
        for (pageIndex in 0 until maxOf(1, layout.pageCount)) {
            body.append("<w:p>")
            if (pageIndex > 0) body.append("<w:pPr><w:pageBreakBefore/></w:pPr>")
            layout.placements.filter { it.page == pageIndex && it.pieceId in relIds }.forEach { placed ->
                val id = drawingId++
                val x = (placed.xIn * EMU_PER_INCH).toLong()
                val y = (placed.yIn * EMU_PER_INCH).toLong()
                val cx = (placed.widthIn * EMU_PER_INCH).toLong()
                val cy = (placed.heightIn * EMU_PER_INCH).toLong()
                body.append(
                    """<w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="$id" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1">""" +
                        """<wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="page"><wp:posOffset>$x</wp:posOffset></wp:positionH>""" +
                        """<wp:positionV relativeFrom="page"><wp:posOffset>$y</wp:posOffset></wp:positionV><wp:extent cx="$cx" cy="$cy"/>""" +
                        """<wp:effectExtent l="0" t="0" r="0" b="0"/><wp:wrapNone/><wp:docPr id="$id" name="Piece $id"/>""" +
                        """<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>""" +
                        """<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic>""" +
                        """<pic:nvPicPr><pic:cNvPr id="$id" name="${mediaNames[placed.pieceId]}"/><pic:cNvPicPr/></pic:nvPicPr>""" +
                        """<pic:blipFill><a:blip r:embed="${relIds[placed.pieceId]}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>""" +
                        """<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>""" +
                        """</pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r>"""
                )
            }
            body.append("</w:p>")
        }
        val pageW = (layout.pageWidthIn * TWIPS_PER_INCH).toInt()
        val pageH = (layout.pageHeightIn * TWIPS_PER_INCH).toInt()
        val margin = (MARGIN_IN * TWIPS_PER_INCH).toInt()
        val orient = if (layout.landscape) """ w:orient="landscape"""" else ""
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
            """<w:body>$body<w:sectPr><w:pgSz w:w="$pageW" w:h="$pageH"$orient/><w:pgMar w:top="$margin" w:right="$margin" w:bottom="$margin" w:left="$margin" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>"""
        val rels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        relIds.forEach { (pieceId, relId) ->
            rels.append("""<Relationship Id="$relId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${mediaNames[pieceId]}"/>""")
        }
        rels.append("</Relationships>")

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            fun text(name: String, content: String) = entry(name, content.toByteArray(Charsets.UTF_8))
            text(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
            )
            text(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
            )
            text("word/document.xml", document)
            text("word/_rels/document.xml.rels", rels.toString())
            crops.forEach { (pieceId, bitmap) ->
                entry("word/media/${mediaNames[pieceId]}", ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
            }
        }
        return output.toByteArray()
    }
}

object ShareExporter {
    /** Writes [bytes] to the app cache and returns a content Uri other apps can read. */
    fun shareableUri(context: Context, bytes: ByteArray, fileName: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun shareIntent(uri: Uri, mimeType: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        "Share print file"
    )
}

object PrintExporter {
    fun print(activity: Activity, pdf: ByteArray, pageCount: Int, paper: PaperSize, landscape: Boolean) {
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val media = when (paper) {
            PaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
            PaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
        }.let { if (landscape) it.asLandscape() else it.asPortrait() }
        val attributes = PrintAttributes.Builder()
            .setMediaSize(media)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        manager.print("Printwise", PdfPrintAdapter(pdf, pageCount), attributes)
    }

    private class PdfPrintAdapter(private val pdf: ByteArray, private val pageCount: Int) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("printwise.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageCount)
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) {
            try {
                FileOutputStream(destination.fileDescriptor).use { it.write(pdf) }
                if (cancellationSignal.isCanceled) callback.onWriteCancelled()
                else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (error: Exception) {
                callback.onWriteFailed(error.message)
            }
        }
    }
}
