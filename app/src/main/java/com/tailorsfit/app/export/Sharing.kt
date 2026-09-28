package com.tailorsfit.app.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object Sharing {
    fun exportDir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    fun safeFileName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "pattern" }

    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(send, "Share pattern").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to share with", Toast.LENGTH_LONG).show()
        }
    }

    /** Sends a ready PDF to the Android print system (Wi-Fi printers, "Save as PDF" ...). */
    fun print(context: Context, file: File, jobName: String, paper: PaperSize) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val media = when (paper) {
            PaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
            PaperSize.A3 -> PrintAttributes.MediaSize.ISO_A3
            PaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
            PaperSize.FULL -> null
        }
        val attrs = PrintAttributes.Builder().apply { media?.let { setMediaSize(it) } }
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        manager.print(jobName, PdfFilePrintAdapter(file, jobName), attrs)
    }
}

private class PdfFilePrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(Sharing.safeFileName(name) + ".pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?,
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination!!.fileDescriptor).use { output -> input.copyTo(output) }
            }
            if (cancellationSignal?.isCanceled == true) callback?.onWriteCancelled()
            else callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}
