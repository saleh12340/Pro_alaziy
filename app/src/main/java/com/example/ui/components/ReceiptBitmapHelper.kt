package com.example.ui.components

import com.saleh.enezi.R

import android.content.Context
import android.graphics.*
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.local.CustomerReceiptEntity
import com.example.data.local.InvoiceItemEntity
import com.example.data.local.StoreConfigEntity
import com.example.ui.util.toEnglishDigits
import com.example.ui.util.toCleanDouble
import com.example.ui.util.formatCleanNumber
import com.example.ui.viewmodels.ReceiptRow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object ReceiptBitmapHelper {

    private val formatter = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

    /**
     * Builds a Paint configured with Amiri font styled to lean toward Ruq'ah handwriting:
     * - textScaleX: slightly elongates words horizontally (تطويل الكلمات قليلاً)
     * - textSkewX: subtle backward dynamic slant reminiscent of Ruq'ah cursive flow (يميل إلى خط الرقعة)
     * - isFakeBoldText: bolsters stroke density for ultra-crisp thermal print heads
     */
    private fun getAmiriPaint(
        context: Context,
        textSize: Float,
        isBold: Boolean = false,
        align: Paint.Align = Paint.Align.RIGHT,
        color: Int = Color.BLACK,
        extendWords: Boolean = true,
        leanRuqah: Boolean = true
    ): Paint {
        val amiriTypeface = ResourcesCompat.getFont(context, R.font.amiri) ?: Typeface.DEFAULT
        val tf = if (isBold) Typeface.create(amiriTypeface, Typeface.BOLD) else amiriTypeface
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = tf
            this.textAlign = align
            if (isBold) {
                this.isFakeBoldText = true
            }
            if (extendWords) {
                this.textScaleX = 1.07f
                this.letterSpacing = 0.02f
            }
            if (leanRuqah) {
                this.textSkewX = -0.12f
            }
        }
    }

    /**
     * Creates an ultra-clear, highly compressed, pure white (zero-background) thermal receipt.
     * Features:
     * - RTL Table Layout: [اسم الصنف]   [الكمية]   [القيمة]   [الإجمالية]
     * - Rows aligned tightly underneath each other to minimize paper waste
     * - Sequential Financial Totals: Grand Total -> Paid (الواصل) -> Remaining (المتبقي للعميل أو عليه)
     * - High-contrast text on white canvas with clean divider lines (no black background blocks)
     */
    fun createReceiptBitmap(
        context: Context,
        storeConfig: StoreConfigEntity,
        invoiceNumber: Int,
        dateString: String,
        customerName: String,
        paymentType: String,
        rows: List<ReceiptRow>,
        receivedAmount: Double = 0.0,
        widthPx: Int = 384 // 384 for 58mm, 576 for 80mm
    ): Bitmap {
        val baseWidth = 384f
        val scale = widthPx.toFloat() / baseWidth

        // Filter out empty rows
        val activeRows = rows.filterNot { it.isCompletelyEmpty }.ifEmpty {
            listOf(ReceiptRow(description = "صنف", quantityStr = "1", totalAmountStr = "0"))
        }

        val grandTotal = activeRows.sumOf { it.total }
        val diff = grandTotal - receivedAmount

        // Tight vertical rhythm to compress receipt and save paper
        val padding = 4f * scale
        val headerH = (if (storeConfig.storeSubtitle.isNotEmpty()) 56f else 46f) * scale
        val metaH = 34f * scale
        val tableHeaderH = 18f * scale
        val rowH = 17f * scale
        val tableH = activeRows.size * rowH
        val totalsH = 54f * scale
        val footerH = 32f * scale
        val totalHeight = (headerH + metaH + tableHeaderH + tableH + totalsH + footerH + (10f * scale)).toInt().coerceAtLeast(200)

        val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // 100% pure white canvas: zero dark or filled backgrounds
        canvas.drawColor(Color.WHITE)

        // Paints with Amiri font leaning to Ruq'ah
        val storeTitlePaint = getAmiriPaint(context, 19.5f * scale, isBold = true, align = Paint.Align.CENTER)
        val storeSubPaint = getAmiriPaint(context, 10f * scale, isBold = false, align = Paint.Align.CENTER)
        
        val headerLabelPaint = getAmiriPaint(context, 11f * scale, isBold = true, align = Paint.Align.RIGHT)
        val metaBoldPaint = getAmiriPaint(context, 11.5f * scale, isBold = true, align = Paint.Align.RIGHT)
        val metaRegularPaint = getAmiriPaint(context, 10f * scale, isBold = false, align = Paint.Align.LEFT)
        
        val tableHeaderPaint = getAmiriPaint(context, 11f * scale, isBold = true)
        val rowTextPaint = getAmiriPaint(context, 10.5f * scale, isBold = false)
        val rowBoldPaint = getAmiriPaint(context, 10.5f * scale, isBold = true)
        
        val totalLabelPaint = getAmiriPaint(context, 12f * scale, isBold = true, align = Paint.Align.RIGHT)
        val totalValuePaint = getAmiriPaint(context, 13f * scale, isBold = true, align = Paint.Align.LEFT)
        val paidLabelPaint = getAmiriPaint(context, 11.5f * scale, isBold = true, align = Paint.Align.RIGHT)
        val paidValuePaint = getAmiriPaint(context, 12f * scale, isBold = true, align = Paint.Align.LEFT)
        val remLabelPaint = getAmiriPaint(context, 11.5f * scale, isBold = true, align = Paint.Align.RIGHT)
        val remValuePaint = getAmiriPaint(context, 12.5f * scale, isBold = true, align = Paint.Align.LEFT)

        val footerPaint = getAmiriPaint(context, 9.5f * scale, isBold = false, align = Paint.Align.CENTER)

        // Line dividers
        val solidLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1.2f * scale
        }
        val thinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 0.8f * scale
        }
        val dottedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 0.8f * scale
            pathEffect = DashPathEffect(floatArrayOf(3f * scale, 3f * scale), 0f)
        }

        var currentY = padding + (2f * scale)

        // 1. STORE HEADER
        val storeName = storeConfig.storeNameArabic.ifEmpty { "متجرنا" }
        canvas.drawText(storeName, widthPx / 2f, currentY + (17f * scale), storeTitlePaint)
        currentY += 21f * scale

        if (storeConfig.storeSubtitle.isNotEmpty()) {
            canvas.drawText(storeConfig.storeSubtitle, widthPx / 2f, currentY + (10f * scale), storeSubPaint)
            currentY += 13f * scale
        }

        val phones = "هاتف: ${storeConfig.phone1.ifEmpty { "770000000" }}" +
                if (storeConfig.phone2.isNotEmpty()) " - ${storeConfig.phone2}" else ""
        canvas.drawText(phones, widthPx / 2f, currentY + (10f * scale), storeSubPaint)
        currentY += 14f * scale

        // Header Divider
        canvas.drawLine(padding, currentY, widthPx - padding, currentY, solidLinePaint)
        currentY += 4f * scale

        // 2. INVOICE META ROW
        // Right: Invoice #, Center: Payment Type, Left: Date
        metaBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("فاتورة: #${invoiceNumber.toString().toEnglishDigits()}", widthPx - padding, currentY + (12f * scale), metaBoldPaint)

        metaBoldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(paymentType, widthPx / 2f, currentY + (12f * scale), metaBoldPaint)

        metaRegularPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(dateString.toEnglishDigits(), padding, currentY + (12f * scale), metaRegularPaint)
        currentY += 15f * scale

        // Customer Name Row
        metaBoldPaint.textAlign = Paint.Align.RIGHT
        val custText = "العميل: ${customerName.ifEmpty { "عميل نقدي" }}"
        canvas.drawText(custText, widthPx - padding, currentY + (12f * scale), metaBoldPaint)
        currentY += 16f * scale

        // Divider before items table
        canvas.drawLine(padding, currentY, widthPx - padding, currentY, solidLinePaint)
        currentY += 2f * scale

        // 3. TABLE HEADER (RTL Columns: اسم الصنف | الكمية | القيمة | الإجمالية)
        // Coordinates:
        // Leftmost: الإجمالية (padding)
        // Next: القيمة (padding + 94f)
        // Next: الكمية (padding + 144f)
        // Rightmost: اسم الصنف (widthPx - padding)
        val xTotal = padding
        val xPrice = padding + (94f * scale)
        val xQty = padding + (144f * scale)
        val xDesc = widthPx - padding

        tableHeaderPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("اسم الصنف", xDesc, currentY + (13f * scale), tableHeaderPaint)

        tableHeaderPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("الكمية", xQty, currentY + (13f * scale), tableHeaderPaint)
        canvas.drawText("القيمة", xPrice, currentY + (13f * scale), tableHeaderPaint)

        tableHeaderPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الإجمالية", xTotal, currentY + (13f * scale), tableHeaderPaint)

        currentY += tableHeaderH
        canvas.drawLine(padding, currentY, widthPx - padding, currentY, thinLinePaint)
        currentY += 2f * scale

        // 4. ITEMS ROWS (تتابع صفوف مرتبة تحت تحت تحت)
        val maxDescChars = (26 * (if (scale > 1f) 1.5f else 1.0f)).toInt()

        activeRows.forEach { row ->
            val q = row.quantity
            val tot = row.total
            val unitVal = if (row.unitPrice > 0.0) row.unitPrice else if (tot > 0.0 && q > 0.0) tot / q else 0.0

            // اسم الصنف (Right aligned)
            rowTextPaint.textAlign = Paint.Align.RIGHT
            val desc = if (row.description.length > maxDescChars) {
                row.description.take(maxDescChars - 1) + ".."
            } else {
                row.description.ifEmpty { "صنف" }
            }
            canvas.drawText(desc, xDesc, currentY + (12.5f * scale), rowTextPaint)

            // الكمية (Center aligned)
            rowBoldPaint.textAlign = Paint.Align.CENTER
            val qStr = if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()
            canvas.drawText(qStr.toEnglishDigits(), xQty, currentY + (12.5f * scale), rowBoldPaint)

            // القيمة (سعر الواحدة) (Center aligned)
            rowTextPaint.textAlign = Paint.Align.CENTER
            val priceStr = if (unitVal > 0.0) formatter.format(unitVal).toEnglishDigits() else "-"
            canvas.drawText(priceStr, xPrice, currentY + (12.5f * scale), rowTextPaint)

            // الإجمالية (Left aligned)
            rowBoldPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(formatter.format(tot).toEnglishDigits(), xTotal, currentY + (12.5f * scale), rowBoldPaint)

            currentY += rowH
            canvas.drawLine(padding, currentY, widthPx - padding, currentY, dottedLinePaint)
            currentY += 1.5f * scale
        }

        currentY += 2f * scale
        canvas.drawLine(padding, currentY, widthPx - padding, currentY, solidLinePaint)
        currentY += 3f * scale

        // 5. FINANCIAL TOTALS BLOCK (الإجمالي العام -> الواصل -> المتبقي)
        val currency = storeConfig.currencySymbol

        // أ) الإجمالي العام
        totalLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الإجمالي العام:", widthPx - padding, currentY + (14f * scale), totalLabelPaint)

        totalValuePaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${formatter.format(grandTotal).toEnglishDigits()} $currency", padding, currentY + (14f * scale), totalValuePaint)
        currentY += 17f * scale

        // ب) المبلغ الواصل
        paidLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المبلغ الواصل:", widthPx - padding, currentY + (13f * scale), paidLabelPaint)

        paidValuePaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${formatter.format(receivedAmount).toEnglishDigits()} $currency", padding, currentY + (13f * scale), paidValuePaint)
        currentY += 16f * scale

        // ج) المتبقي للعميل أو عليه
        val (remLabel, remVal) = when {
            diff > 0.001 -> "المتبقي (عليه دين):" to "${formatter.format(diff).toEnglishDigits()} $currency"
            diff < -0.001 -> "المتبقي (له رصيد):" to "${formatter.format(-diff).toEnglishDigits()} $currency"
            else -> "المتبقي:" to "0.00 $currency (خالص)"
        }
        remLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(remLabel, widthPx - padding, currentY + (14f * scale), remLabelPaint)

        remValuePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(remVal, padding, currentY + (14f * scale), remValuePaint)
        currentY += 18f * scale

        canvas.drawLine(padding, currentY, widthPx - padding, currentY, solidLinePaint)
        currentY += 4f * scale

        // 6. SIGNATURES & CLOSING
        footerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("توقيع البائع: .........", widthPx - padding, currentY + (11f * scale), footerPaint)

        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("توقيع العميل: .........", padding, currentY + (11f * scale), footerPaint)
        currentY += 14f * scale

        footerPaint.textAlign = Paint.Align.CENTER
        val note = storeConfig.defaultDisclaimerNote.ifEmpty { "شكراً لزيارتكم - أهلاً وسهلاً" }
        canvas.drawText(note.take(45), widthPx / 2f, currentY + (10f * scale), footerPaint)

        return bitmap
    }

    /**
     * Helper to create receipt bitmap from invoice item entities.
     */
    fun createReceiptBitmapFromItems(
        context: Context,
        storeConfig: StoreConfigEntity,
        invoiceNumber: Int,
        dateString: String,
        customerName: String,
        paymentType: String,
        items: List<InvoiceItemEntity>,
        receivedAmount: Double = 0.0,
        widthPx: Int = storeConfig.printerWidthDots
    ): Bitmap {
        val rows = items.map { item ->
            ReceiptRow(
                description = item.description,
                quantityStr = if (item.quantity > 0) formatCleanNumber(item.quantity) else "1",
                unitPriceStr = if (item.unitPrice > 0) formatCleanNumber(item.unitPrice) else "",
                totalAmountStr = if (item.totalAmount > 0) formatCleanNumber(item.totalAmount) else ""
            )
        }.ifEmpty { listOf(ReceiptRow(description = "صنف", quantityStr = "1", unitPriceStr = "0", totalAmountStr = "0")) }

        return createReceiptBitmap(
            context = context,
            storeConfig = storeConfig,
            invoiceNumber = invoiceNumber,
            dateString = dateString,
            customerName = customerName,
            paymentType = paymentType,
            rows = rows,
            receivedAmount = receivedAmount,
            widthPx = widthPx
        )
    }

    /**
     * Thermal print voucher for a customer receipt (سند قبض نقدي) with pure white clarity and tight lines.
     */
    fun createVoucherBitmap(
        context: Context,
        storeConfig: StoreConfigEntity,
        receipt: CustomerReceiptEntity,
        widthPx: Int = storeConfig.printerWidthDots
    ): Bitmap {
        val baseWidth = 384f
        val scale = widthPx.toFloat() / baseWidth

        val padding = 4f * scale
        val totalHeight = (225f * scale).toInt()
        val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = getAmiriPaint(context, 19f * scale, isBold = true, align = Paint.Align.CENTER)
        val subPaint = getAmiriPaint(context, 10.5f * scale, isBold = false, align = Paint.Align.CENTER)
        val labelBoldPaint = getAmiriPaint(context, 11.5f * scale, isBold = true, align = Paint.Align.RIGHT)
        val amountBigPaint = getAmiriPaint(context, 16f * scale, isBold = true, align = Paint.Align.CENTER)
        val footerPaint = getAmiriPaint(context, 9.5f * scale, isBold = false)

        val solidLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1.2f * scale
        }
        val dottedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 0.8f * scale
            pathEffect = DashPathEffect(floatArrayOf(3f * scale, 3f * scale), 0f)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * scale
        }

        var currentY = padding + (2f * scale)

        // Header
        canvas.drawText(storeConfig.storeNameArabic.ifEmpty { "متجرنا" }, widthPx / 2f, currentY + (17f * scale), titlePaint)
        currentY += 21f * scale
        val voucherTitle = if (receipt.isDebit) "سند قيد مالي (على العميل)" else "سند قبض مالي (للعميل)"
        canvas.drawText("$voucherTitle #${receipt.receiptId.toString().toEnglishDigits()}", widthPx / 2f, currentY + (10f * scale), subPaint)
        currentY += 15f * scale

        canvas.drawLine(padding, currentY, widthPx - padding, currentY, solidLinePaint)
        currentY += 4f * scale

        // Meta info
        labelBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("التاريخ: ${receipt.dateString.toEnglishDigits()}", widthPx - padding, currentY + (13f * scale), labelBoldPaint)
        currentY += 16f * scale

        val partyLabel = if (receipt.isDebit) "قيد على حساب: ${receipt.customerName}" else "استلمنا من الأخ: ${receipt.customerName}"
        canvas.drawText(partyLabel, widthPx - padding, currentY + (13f * scale), labelBoldPaint)
        currentY += 17f * scale

        // Amount Box (Clear border, NO black background fill)
        val boxH = 30f * scale
        canvas.drawRect(padding, currentY, widthPx - padding, currentY + boxH, borderPaint)
        val amtPrefix = if (receipt.isDebit) "مبلغ مقيد عليه" else "مبلغ مقبوض له"
        val amtStr = "$amtPrefix: ${formatter.format(receipt.amount).toEnglishDigits()} ${storeConfig.currencySymbol}"
        canvas.drawText(amtStr, widthPx / 2f, currentY + (20f * scale), amountBigPaint)
        currentY += boxH + (8f * scale)

        // Notes & Type
        labelBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("نوع الحركة: ${receipt.paymentType}", widthPx - padding, currentY + (13f * scale), labelBoldPaint)
        currentY += 16f * scale

        if (receipt.notes.isNotEmpty()) {
            canvas.drawText("البيان: ${receipt.notes}", widthPx - padding, currentY + (13f * scale), labelBoldPaint)
            currentY += 16f * scale
        }

        canvas.drawLine(padding, currentY, widthPx - padding, currentY, solidLinePaint)
        currentY += 5f * scale

        // Signatures
        footerPaint.textAlign = Paint.Align.RIGHT
        val rightSig = if (receipt.isDebit) "المحاسب: ........." else "المستلم / الصندوق: ........."
        canvas.drawText(rightSig, widthPx - padding, currentY + (10f * scale), footerPaint)
        footerPaint.textAlign = Paint.Align.LEFT
        val leftSig = if (receipt.isDebit) "المستلم / العميل: ........." else "المسدد: ........."
        canvas.drawText(leftSig, padding, currentY + (10f * scale), footerPaint)

        return bitmap
    }

    /**
     * Thermal print for consolidated customer statement (كشف حساب العميل) with pure white clarity.
     */
    fun createCustomerStatementThermalBitmap(
        context: Context,
        storeConfig: StoreConfigEntity,
        customerName: String,
        statementRows: List<InvoicePdfHelper.CustomerStatementRow>,
        widthPx: Int = storeConfig.printerWidthDots
    ): Bitmap {
        val baseWidth = 384f
        val scale = widthPx.toFloat() / baseWidth

        val padding = 4f * scale
        val headerH = 50f * scale
        val rowH = 17f * scale
        val tableH = (statementRows.size + 1) * rowH
        val footerH = 46f * scale
        val totalH = (headerH + tableH + footerH + (12f * scale)).toInt().coerceAtLeast(200)

        val bitmap = Bitmap.createBitmap(widthPx, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = getAmiriPaint(context, 18.5f * scale, isBold = true, align = Paint.Align.CENTER)
        val subPaint = getAmiriPaint(context, 10.5f * scale, isBold = false, align = Paint.Align.CENTER)
        val tableHeaderPaint = getAmiriPaint(context, 10.5f * scale, isBold = true)
        val boldPaint = getAmiriPaint(context, 10f * scale, isBold = true)
        val regularPaint = getAmiriPaint(context, 9.5f * scale, isBold = false)
        val footerLabelPaint = getAmiriPaint(context, 12f * scale, isBold = true, align = Paint.Align.RIGHT)
        val footerValPaint = getAmiriPaint(context, 13f * scale, isBold = true, align = Paint.Align.LEFT)

        val solidLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1.2f * scale
        }
        val thinLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 0.8f * scale
        }
        val dottedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 0.8f * scale
            pathEffect = DashPathEffect(floatArrayOf(3f * scale, 3f * scale), 0f)
        }

        var currentY = padding + (2f * scale)

        // Header
        canvas.drawText(storeConfig.storeNameArabic.ifEmpty { "متجرنا" }, widthPx / 2f, currentY + (16f * scale), titlePaint)
        currentY += 20f * scale
        canvas.drawText("كشف حساب: $customerName", widthPx / 2f, currentY + (10f * scale), subPaint)
        currentY += 14f * scale

        canvas.drawLine(padding, currentY, widthPx - padding, currentY, solidLine)
        currentY += 3f * scale

        // Table Header (التاريخ | العملية | المبلغ | الرصيد)
        tableHeaderPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("التاريخ", widthPx - padding, currentY + (12f * scale), tableHeaderPaint)
        canvas.drawText("العملية", widthPx - padding - (54f * scale), currentY + (12f * scale), tableHeaderPaint)

        tableHeaderPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("المبلغ", padding + (92f * scale), currentY + (12f * scale), tableHeaderPaint)

        tableHeaderPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الرصيد", padding, currentY + (12f * scale), tableHeaderPaint)
        currentY += rowH

        canvas.drawLine(padding, currentY, widthPx - padding, currentY, thinLine)
        currentY += 2f * scale

        // Rows
        statementRows.forEach { r ->
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(r.date.takeLast(5).toEnglishDigits(), widthPx - padding, currentY + (12f * scale), regularPaint)

            boldPaint.textAlign = Paint.Align.RIGHT
            val desc = r.type.take(13)
            canvas.drawText(desc, widthPx - padding - (54f * scale), currentY + (12f * scale), boldPaint)

            val amt = if (r.debit > 0) r.debit else r.credit
            boldPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(formatter.format(amt).toEnglishDigits(), padding + (92f * scale), currentY + (12f * scale), boldPaint)

            boldPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(formatter.format(r.runningBalance).toEnglishDigits(), padding, currentY + (12f * scale), boldPaint)

            currentY += rowH
            canvas.drawLine(padding, currentY, widthPx - padding, currentY, dottedLine)
            currentY += 1.5f * scale
        }

        currentY += 3f * scale
        canvas.drawLine(padding, currentY, widthPx - padding, currentY, solidLine)
        currentY += 4f * scale

        // Final Balance
        val lastBal = statementRows.lastOrNull()?.runningBalance ?: 0.0
        val balLabel = if (lastBal > 0) "الرصيد المستحق (عليه):" else if (lastBal < 0) "الرصيد المتبقي (له):" else "الرصيد النهائي:"
        val balVal = "${formatter.format(Math.abs(lastBal)).toEnglishDigits()} ${storeConfig.currencySymbol}" + (if (lastBal == 0.0) " (خالص)" else "")

        canvas.drawText(balLabel, widthPx - padding, currentY + (14f * scale), footerLabelPaint)
        canvas.drawText(balVal, padding, currentY + (14f * scale), footerValPaint)

        return bitmap
    }
}
