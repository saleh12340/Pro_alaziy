package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.InvoiceItemEntity
import com.example.data.local.StoreConfigEntity
import com.example.ui.viewmodels.ReceiptRow
import com.example.ui.util.formatCleanNumber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object InvoicePdfHelper {

    /**
     * Generates a crystal-clear, true 4K Ultra-HD PDF document for an invoice (2160px native resolution).
     * Saved in the app's cache directory ready for instant sharing via WhatsApp/Email/Telegram without any blur.
     */
    fun createInvoicePdf(
        context: Context,
        storeConfig: StoreConfigEntity,
        invoiceNumber: Int,
        dateString: String,
        customerName: String,
        paymentType: String,
        rows: List<ReceiptRow>,
        receivedAmount: Double = 0.0
    ): File? {
        return try {
            // 1. Render true 4K Ultra-HD 2160px receipt bitmap
            val bitmap = ReceiptBitmapHelper.createReceiptBitmap(
                context = context,
                storeConfig = storeConfig,
                invoiceNumber = invoiceNumber,
                dateString = dateString,
                customerName = customerName,
                paymentType = paymentType,
                rows = rows,
                receivedAmount = receivedAmount,
                widthPx = 2160 // 4K Ultra-HD native rendering
            )

            // 2. Prepare 4K PDF Page Dimensions (1:1 with 2160px bitmap for zero quality loss)
            val pdfWidth = 2160
            val pdfHeight = bitmap.height

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pdfWidth, pdfHeight, 1).create()
            val page = document.startPage(pageInfo)

            val canvas: Canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                isDither = true
            }

            // Draw 4K bitmap at 1:1 scale
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            document.finishPage(page)

            // 3. Write PDF to cache directory
            val pdfDir = File(context.cacheDir, "invoices_pdf")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val cleanCustomer = customerName.replace(" ", "_").take(15).ifEmpty { "عميل" }
            val pdfFile = File(pdfDir, "فاتورة_${invoiceNumber}_$cleanCustomer.pdf")

            val outputStream = FileOutputStream(pdfFile)
            document.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            document.close()

            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates an invoice PDF directly from saved database items in 4K resolution.
     */
    fun createInvoicePdfFromItems(
        context: Context,
        storeConfig: StoreConfigEntity,
        invoiceNumber: Int,
        dateString: String,
        customerName: String,
        paymentType: String,
        items: List<InvoiceItemEntity>,
        receivedAmount: Double = 0.0
    ): File? {
        val rows = items.map { item ->
            ReceiptRow(
                description = item.description,
                quantityStr = if (item.quantity > 0) formatCleanNumber(item.quantity) else "1",
                unitPriceStr = if (item.unitPrice > 0) formatCleanNumber(item.unitPrice) else "",
                totalAmountStr = if (item.totalAmount > 0) formatCleanNumber(item.totalAmount) else ""
            )
        }.ifEmpty { listOf(ReceiptRow(description = "صنف", quantityStr = "1", unitPriceStr = "0", totalAmountStr = "0")) }

        return createInvoicePdf(
            context = context,
            storeConfig = storeConfig,
            invoiceNumber = invoiceNumber,
            dateString = dateString,
            customerName = customerName,
            paymentType = paymentType,
            rows = rows,
            receivedAmount = receivedAmount
        )
    }

    /**
     * Returns a content:// URI for sharing via Intent using FileProvider.
     */
    fun getShareablePdfUri(context: Context, pdfFile: File): Uri? {
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates an Android Share Intent with standard application/pdf mime type.
     */
    fun createSharePdfIntent(context: Context, pdfUri: Uri, invoiceNumber: Int, customerName: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, "فاتورة رقم #$invoiceNumber - $customerName")
            putExtra(Intent.EXTRA_TEXT, "مرفق فاتورة إلكترونية رسمية عالية الجودة 4K رقم #$invoiceNumber للعميل $customerName.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Saves the PDF permanently to the device's Downloads/Documents folder.
     */
    fun savePdfToDownloads(context: Context, pdfFile: File, fileName: String): Uri? {
        var fos: OutputStream? = null
        var fileUri: Uri? = null
        val finalName = "فاتورة_${fileName}_${System.currentTimeMillis()}.pdf"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Fatooraty")
                }
                val resolver = context.contentResolver
                fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (fileUri != null) {
                    fos = resolver.openOutputStream(fileUri)
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fatooratyDir = File(downloadsDir, "Fatooraty")
                if (!fatooratyDir.exists()) fatooratyDir.mkdirs()
                val destFile = File(fatooratyDir, finalName)
                fos = FileOutputStream(destFile)
                fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
            }

            if (fos != null) {
                pdfFile.inputStream().use { input ->
                    input.copyTo(fos!!)
                }
                fos!!.flush()
                fos!!.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return fileUri
    }

    /**
     * Data class representing a line item in a customer ledger statement.
     */
    data class CustomerStatementRow(
        val index: Int,
        val date: String,
        val type: String,
        val details: String,
        val debit: Double,
        val credit: Double,
        val runningBalance: Double
    )

    /**
     * Generates a high-quality Customer Account Statement PDF document.
     */
    fun createCustomerStatementPdf(
        context: Context,
        storeConfig: StoreConfigEntity,
        customerName: String,
        statementRows: List<CustomerStatementRow>,
        totalDebit: Double,
        totalCredit: Double,
        netBalance: Double
    ): File? {
        return try {
            val formatter = java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols(java.util.Locale.US))
            val currentDate = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US).format(java.util.Date())

            val pdfWidth = 1200
            val rowHeight = 44
            val baseHeight = 420
            val dynamicHeight = (baseHeight + (statementRows.size * rowHeight) + 120).coerceAtLeast(800)

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pdfWidth, dynamicHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Background
            canvas.drawColor(android.graphics.Color.WHITE)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            }

            val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                isFakeBoldText = true
            }

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(200, 210, 225)
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }

            val fillCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            // 1. Store Header Bar
            boldPaint.textSize = 34f
            boldPaint.textAlign = Paint.Align.CENTER
            boldPaint.color = android.graphics.Color.rgb(20, 50, 100)
            canvas.drawText(storeConfig.storeNameArabic.ifEmpty { "متجر الفواتير" }, 600f, 65f, boldPaint)

            textPaint.textSize = 20f
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = android.graphics.Color.rgb(100, 110, 125)
            val subText = "${storeConfig.storeSubtitle}  •  هاتف: ${storeConfig.phone1}  •  ${storeConfig.address}"
            canvas.drawText(subText, 600f, 100f, textPaint)

            // Header line
            strokePaint.strokeWidth = 2f
            strokePaint.color = android.graphics.Color.rgb(0, 114, 255)
            canvas.drawLine(60f, 120f, 1140f, 120f, strokePaint)

            // 2. Statement Title & Customer Info
            boldPaint.textSize = 26f
            boldPaint.color = android.graphics.Color.rgb(30, 41, 59)
            boldPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("كشف حساب العميل: $customerName", 1140f, 165f, boldPaint)

            textPaint.textSize = 19f
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = android.graphics.Color.rgb(70, 80, 95)
            canvas.drawText("تاريخ التقرير: $currentDate", 60f, 165f, textPaint)

            // 3. Three Summary KPI Cards
            val cardY = 190f
            val cardH = 80f
            val cardW = 340f

            // Card 1: Total Debit (Purchases)
            fillCardPaint.color = android.graphics.Color.rgb(254, 242, 242)
            canvas.drawRoundRect(800f, cardY, 1140f, cardY + cardH, 16f, 16f, fillCardPaint)
            strokePaint.color = android.graphics.Color.rgb(252, 165, 165)
            canvas.drawRoundRect(800f, cardY, 1140f, cardY + cardH, 16f, 16f, strokePaint)
            textPaint.textSize = 17f
            textPaint.color = android.graphics.Color.rgb(153, 27, 27)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("إجمالي المشتريات (مدين)", 970f, cardY + 30f, textPaint)
            boldPaint.textSize = 23f
            boldPaint.color = android.graphics.Color.rgb(185, 28, 28)
            canvas.drawText("${formatter.format(totalDebit)} ${storeConfig.currencySymbol}", 970f, cardY + 62f, boldPaint)

            // Card 2: Total Credit (Payments)
            fillCardPaint.color = android.graphics.Color.rgb(240, 253, 244)
            canvas.drawRoundRect(430f, cardY, 770f, cardY + cardH, 16f, 16f, fillCardPaint)
            strokePaint.color = android.graphics.Color.rgb(134, 239, 172)
            canvas.drawRoundRect(430f, cardY, 770f, cardY + cardH, 16f, 16f, strokePaint)
            textPaint.color = android.graphics.Color.rgb(22, 101, 52)
            canvas.drawText("إجمالي المسدد (دائن)", 600f, cardY + 30f, textPaint)
            boldPaint.color = android.graphics.Color.rgb(21, 128, 61)
            canvas.drawText("${formatter.format(totalCredit)} ${storeConfig.currencySymbol}", 600f, cardY + 62f, boldPaint)

            // Card 3: Net Balance
            fillCardPaint.color = android.graphics.Color.rgb(239, 246, 255)
            canvas.drawRoundRect(60f, cardY, 400f, cardY + cardH, 16f, 16f, fillCardPaint)
            strokePaint.color = android.graphics.Color.rgb(147, 197, 253)
            canvas.drawRoundRect(60f, cardY, 400f, cardY + cardH, 16f, 16f, strokePaint)
            textPaint.color = android.graphics.Color.rgb(30, 64, 175)
            canvas.drawText("الرصيد المتبقي (المطلوب)", 230f, cardY + 30f, textPaint)
            boldPaint.color = if (netBalance > 0) android.graphics.Color.rgb(220, 38, 38) else android.graphics.Color.rgb(37, 99, 235)
            canvas.drawText("${formatter.format(netBalance)} ${storeConfig.currencySymbol}", 230f, cardY + 62f, boldPaint)

            // 4. Table Header
            val tableY = 295f
            fillCardPaint.color = android.graphics.Color.rgb(241, 245, 249)
            canvas.drawRect(60f, tableY, 1140f, tableY + 45f, fillCardPaint)
            strokePaint.color = android.graphics.Color.rgb(203, 213, 225)
            strokePaint.strokeWidth = 1.2f
            canvas.drawRect(60f, tableY, 1140f, tableY + 45f, strokePaint)

            boldPaint.textSize = 18f
            boldPaint.color = android.graphics.Color.rgb(30, 41, 59)
            boldPaint.textAlign = Paint.Align.CENTER
            val headerTextY = tableY + 28f
            canvas.drawText("#", 1115f, headerTextY, boldPaint)
            canvas.drawText("التاريخ", 1010f, headerTextY, boldPaint)
            canvas.drawText("نوع الحركة", 870f, headerTextY, boldPaint)
            canvas.drawText("البيان والتفاصيل", 640f, headerTextY, boldPaint)
            canvas.drawText("مدين (+)", 430f, headerTextY, boldPaint)
            canvas.drawText("دائن (-)", 280f, headerTextY, boldPaint)
            canvas.drawText("الرصيد", 130f, headerTextY, boldPaint)

            // 5. Table Rows
            var currentY = tableY + 45f
            val maxRows = statementRows.size
            for (i in 0 until maxRows) {
                val row = statementRows[i]
                if (i % 2 == 1) {
                    fillCardPaint.color = android.graphics.Color.rgb(248, 250, 252)
                    canvas.drawRect(60f, currentY, 1140f, currentY + rowHeight, fillCardPaint)
                }
                canvas.drawRect(60f, currentY, 1140f, currentY + rowHeight, strokePaint)

                textPaint.textSize = 17f
                textPaint.textAlign = Paint.Align.CENTER
                val rowTextY = currentY + 28f

                // #
                textPaint.color = android.graphics.Color.rgb(100, 116, 139)
                canvas.drawText("${row.index}", 1115f, rowTextY, textPaint)

                // Date
                textPaint.color = android.graphics.Color.rgb(51, 65, 85)
                canvas.drawText(row.date, 1010f, rowTextY, textPaint)

                // Type
                val isDebit = row.debit > 0
                boldPaint.textSize = 16f
                boldPaint.color = if (isDebit) android.graphics.Color.rgb(220, 38, 38) else android.graphics.Color.rgb(22, 163, 74)
                canvas.drawText(row.type, 870f, rowTextY, boldPaint)

                // Details (Right-aligned inside details column)
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = android.graphics.Color.rgb(30, 41, 59)
                val cleanDetail = row.details.take(28)
                canvas.drawText(cleanDetail, 770f, rowTextY, textPaint)

                // Debit
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = if (row.debit > 0) android.graphics.Color.rgb(220, 38, 38) else android.graphics.Color.rgb(148, 163, 184)
                canvas.drawText(if (row.debit > 0) formatter.format(row.debit) else "-", 430f, rowTextY, textPaint)

                // Credit
                textPaint.color = if (row.credit > 0) android.graphics.Color.rgb(22, 163, 74) else android.graphics.Color.rgb(148, 163, 184)
                canvas.drawText(if (row.credit > 0) formatter.format(row.credit) else "-", 280f, rowTextY, textPaint)

                // Running Balance
                boldPaint.textSize = 17f
                boldPaint.color = android.graphics.Color.rgb(15, 23, 42)
                canvas.drawText(formatter.format(row.runningBalance), 130f, rowTextY, boldPaint)

                currentY += rowHeight
            }

            // 6. Footer
            val footerY = currentY + 50f
            textPaint.textSize = 17f
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = android.graphics.Color.rgb(148, 163, 184)
            canvas.drawText("شكراً لتعاملكم معنا  •  تم استخراج كشف الحساب آلياً  •  ${storeConfig.storeNameArabic}", 600f, footerY, textPaint)

            document.finishPage(page)

            // Save PDF file
            val pdfDir = File(context.cacheDir, "statements_pdf")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val cleanName = customerName.replace(" ", "_").take(15).ifEmpty { "عميل" }
            val pdfFile = File(pdfDir, "كشف_حساب_${cleanName}_${System.currentTimeMillis()}.pdf")

            val outputStream = FileOutputStream(pdfFile)
            document.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            document.close()

            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates an Android Share Intent for a Customer Statement PDF.
     */
    fun createShareStatementPdfIntent(context: Context, pdfUri: Uri, customerName: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, "كشف حساب العميل - $customerName")
            putExtra(Intent.EXTRA_TEXT, "مرفق كشف حساب تفصيلي رسمي للعميل $customerName يوضح كافة الفواتير وسندات القبض والرصيد النهائي المتبقي.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
