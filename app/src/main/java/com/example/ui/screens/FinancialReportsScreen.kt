package com.example.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.FinancialReportSummary
import com.example.domain.model.MonthlySalesPoint
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

enum class ReportPeriod(val title: String) {
    TODAY("اليوم"),
    THIS_WEEK("هذا الأسبوع"),
    THIS_MONTH("هذا الشهر"),
    ALL_TIME("الكل")
}

@Composable
fun FinancialReportsScreen(
    viewModel: InvoiceViewModel,
    modifier: Modifier = Modifier
) {
    val invoices by viewModel.allInvoices.collectAsState()
    val receipts by viewModel.allReceipts.collectAsState()
    val config by viewModel.storeConfig.collectAsState()

    var selectedPeriod by remember { mutableStateOf(ReportPeriod.THIS_MONTH) }
    val moneyFormatter = remember { DecimalFormat("#,##0.##") }

    val todayCalendar = Calendar.getInstance()
    val todayFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    val todayString = todayFormat.format(todayCalendar.time)

    // Filter invoices by selected period
    val filteredInvoices = remember(invoices, selectedPeriod) {
        when (selectedPeriod) {
            ReportPeriod.TODAY -> invoices.filter { it.invoice.dateString == todayString }
            ReportPeriod.THIS_WEEK -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgoTimestamp = cal.timeInMillis
                invoices.filter { it.invoice.createdAtTimestamp >= weekAgoTimestamp }
            }
            ReportPeriod.THIS_MONTH -> {
                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH) + 1
                val prefix = String.format(Locale.US, "%04d/%02d", currentYear, currentMonth)
                invoices.filter { it.invoice.dateString.startsWith(prefix) }
            }
            ReportPeriod.ALL_TIME -> invoices
        }
    }

    // Filter receipts by selected period
    val filteredReceipts = remember(receipts, selectedPeriod) {
        when (selectedPeriod) {
            ReportPeriod.TODAY -> receipts.filter { it.dateString == todayString }
            ReportPeriod.THIS_WEEK -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = cal.timeInMillis
                receipts.filter { it.createdAtTimestamp >= weekAgo }
            }
            ReportPeriod.THIS_MONTH -> {
                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH) + 1
                val prefix = String.format(Locale.US, "%04d/%02d", currentYear, currentMonth)
                receipts.filter { it.dateString.startsWith(prefix) }
            }
            ReportPeriod.ALL_TIME -> receipts
        }
    }

    val totalSales = filteredInvoices.sumOf { it.invoice.grandTotal }
    val totalPaidInInvoices = filteredInvoices.sumOf { it.invoice.paidAmount }
    val totalRemainingDebt = filteredInvoices.sumOf { it.invoice.remainingAmount }
    val totalReceiptsIncome = filteredReceipts.sumOf { it.amount }
    val netCashInflow = totalPaidInInvoices + totalReceiptsIncome

    // Monthly data for the Compose Canvas chart (last 6 months)
    val monthlyData = remember(invoices) {
        val list = mutableListOf<MonthlySalesPoint>()
        val arabicMonths = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
        val cal = Calendar.getInstance()

        for (i in 5 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -i)
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH)
            val prefix = String.format(Locale.US, "%04d/%02d", y, m + 1)
            val monthTotal = invoices.filter { it.invoice.dateString.startsWith(prefix) }.sumOf { it.invoice.grandTotal }
            val label = arabicMonths.getOrElse(m) { "${m + 1}" }
            list.add(MonthlySalesPoint(label, monthTotal))
        }
        list
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeuBackground)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "التقارير والمؤشرات المالية",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeuTextPrimary
                )
                Text(
                    text = "ملخص حركة المبيعات، الصندوق، ورسم بياني تحليلي",
                    fontSize = 12.sp,
                    color = NeuTextSecondary
                )
            }
        }

        // Period Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DesignTokens.RadiusM))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(DesignTokens.RadiusM))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ReportPeriod.values().forEach { period ->
                val isSelected = selectedPeriod == period
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeuAccentBlue else Color.Transparent)
                        .clickable { selectedPeriod = period }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = period.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else NeuTextSecondary
                    )
                }
            }
        }

        // Main Totals Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "إجمالي المبيعات",
                amount = moneyFormatter.format(totalSales),
                currency = config?.currencySymbol ?: "ر.ي",
                color = Color(0xFF1E3A8A),
                iconText = "🧾",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "الواصل للصندوق",
                amount = moneyFormatter.format(netCashInflow),
                currency = config?.currencySymbol ?: "ر.ي",
                color = Color(0xFF059669),
                iconText = "💵",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "الآجل والديون",
                amount = moneyFormatter.format(totalRemainingDebt),
                currency = config?.currencySymbol ?: "ر.ي",
                color = Color(0xFFD97706),
                iconText = "⏳",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "سندات القبض",
                amount = moneyFormatter.format(totalReceiptsIncome),
                currency = config?.currencySymbol ?: "ر.ي",
                color = Color(0xFF2563EB),
                iconText = "📝",
                modifier = Modifier.weight(1f)
            )
        }

        // Summary details card
        NeuCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = 1.dp
        ) {
            Text(
                text = "إحصائيات حركة الفترة (${selectedPeriod.title})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = NeuTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            StatRow("عدد الفواتير الصادرة", "${filteredInvoices.size} فاتورة")
            Divider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)
            StatRow("فواتير نقدية", "${filteredInvoices.count { it.invoice.paymentType == "نقداً" }}")
            Divider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)
            StatRow("فواتير آجل (على الحساب)", "${filteredInvoices.count { it.invoice.paymentType == "أجل" }}")
            Divider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)
            StatRow("عدد سندات القبض", "${filteredReceipts.size} سند")
        }

        // Compose Canvas Sales Chart (Native Canvas Implementation without external heavy libraries)
        NeuCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp
        ) {
            Text(
                text = "المبيعات الشهرية (آخر 6 أشهر)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NeuTextPrimary
            )
            Text(
                text = "رسم بياني تفاعلي مبني بتقنية Jetpack Compose Canvas",
                fontSize = 11.sp,
                color = NeuTextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            MonthlySalesCanvasChart(
                points = monthlyData,
                currency = config?.currencySymbol ?: "ر.ي",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun MetricCard(
    title: String,
    amount: String,
    currency: String,
    color: Color,
    iconText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(DesignTokens.RadiusM),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.5.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.5.sp, color = NeuTextSecondary, fontWeight = FontWeight.Medium)
                Text(text = iconText, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = amount,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
            Text(
                text = currency,
                fontSize = 10.sp,
                color = NeuTextMuted
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.5.sp, color = NeuTextSecondary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
    }
}

/**
 * Pure Jetpack Compose Canvas Bar Chart.
 * Beautifully renders monthly sales bars, axes, and labels without external dependencies.
 */
@Composable
fun MonthlySalesCanvasChart(
    points: List<MonthlySalesPoint>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val maxVal = remember(points) {
        val peak = points.maxOfOrNull { it.amount } ?: 1.0
        if (peak <= 0.0) 1000.0 else peak * 1.15
    }

    val primaryColor = NeuAccentBlue
    val secondaryColor = Color(0xFF38BDF8)
    val gridColor = Color(0xFFE2E8F0)
    val textColorArgb = NeuTextSecondary.toArgb()

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val bottomMargin = 40.dp.toPx()
        val chartHeight = height - bottomMargin
        val barCount = points.size
        if (barCount == 0) return@Canvas

        val slotWidth = width / barCount
        val barWidth = slotWidth * 0.45f

        // Draw horizontal grid lines
        val gridLines = 3
        for (i in 0..gridLines) {
            val y = chartHeight * (i.toFloat() / gridLines)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw Bars and Labels
        val textPaint = Paint().apply {
            color = textColorArgb
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        points.forEachIndexed { index, point ->
            val fraction = (point.amount / maxVal).coerceIn(0.0, 1.0).toFloat()
            val barH = chartHeight * fraction
            val left = index * slotWidth + (slotWidth - barWidth) / 2f
            val top = chartHeight - barH

            // Bar Gradient
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor, secondaryColor),
                    startY = top,
                    endY = chartHeight
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, barH.coerceAtLeast(4f)),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // Month Label below bar
            drawContext.canvas.nativeCanvas.drawText(
                point.monthName,
                left + barWidth / 2f,
                height - 10f,
                textPaint
            )
        }
    }
}
