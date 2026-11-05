package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InvoiceWithItems
import com.example.printer.PrinterConnectionState
import com.example.ui.components.InteractiveReceiptView
import com.example.ui.components.ReceiptBitmapHelper
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.example.ui.util.toEnglishDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceHistoryScreen(
    viewModel: InvoiceViewModel,
    onEditInvoice: (InvoiceWithItems) -> Unit,
    onNavigateToPrinterSetup: () -> Unit
) {
    val context = LocalContext.current
    val invoicesWithItems by viewModel.allInvoices.collectAsState()
    val storeConfigState by viewModel.storeConfig.collectAsState()
    val storeConfig = storeConfigState ?: com.example.data.local.StoreConfigEntity()
    val printerState by viewModel.printerState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPeriodFilter by remember { mutableStateOf("الكل") } // "الكل", "اليوم", "هذا الأسبوع", "هذا الشهر", "نقداً", "أجل"
    var selectedInvoiceForDetail by remember { mutableStateOf<InvoiceWithItems?>(null) }

    val formatter = remember { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)) }
    val todayDateStr = remember { java.text.SimpleDateFormat("yyyy/MM/dd", Locale.US).format(java.util.Date()) }

    val filteredList = remember(invoicesWithItems, searchQuery, selectedPeriodFilter) {
        val now = System.currentTimeMillis()
        val oneWeekMillis = 7L * 24 * 3600 * 1000
        val oneMonthMillis = 30L * 24 * 3600 * 1000

        invoicesWithItems.filter { item ->
            val inv = item.invoice
            val matchesQuery = if (searchQuery.isBlank()) true else {
                inv.customerName.contains(searchQuery, ignoreCase = true) ||
                inv.invoiceNumber.toString().contains(searchQuery) ||
                inv.paymentType.contains(searchQuery)
            }

            val matchesFilter = when (selectedPeriodFilter) {
                "اليوم" -> inv.dateString == todayDateStr || (now - inv.createdAtTimestamp < 24 * 3600 * 1000)
                "هذا الأسبوع" -> (now - inv.createdAtTimestamp) <= oneWeekMillis
                "هذا الشهر" -> (now - inv.createdAtTimestamp) <= oneMonthMillis
                "نقداً" -> inv.paymentType.contains("نقداً")
                "أجل" -> inv.paymentType.contains("أجل")
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    val totalSalesSum = remember(filteredList) { filteredList.sumOf { it.invoice.grandTotal } }
    val totalCashSum = remember(filteredList) { filteredList.filter { it.invoice.paymentType.contains("نقداً") }.sumOf { it.invoice.grandTotal } }
    val totalCreditSum = remember(filteredList) { filteredList.filter { it.invoice.paymentType.contains("أجل") }.sumOf { it.invoice.grandTotal } }

    Scaffold(
        containerColor = NeuBackground,
        topBar = {
            Surface(
                color = NeuSurfaceRaised,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سجل الفواتير (الأحدث أولاً)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NeuTextPrimary
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Sunken Neumorphic Search Bar (Compact)
            NeuInsetBox(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = NeuTextMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.5.sp, color = NeuTextPrimary),
                        cursorBrush = SolidColor(NeuAccentBlue),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("بحث باسم العميل أو رقم الفاتورة...", fontSize = 12.sp, color = NeuTextMuted)
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = NeuTextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Filter Chips Row (Compact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("الكل", "اليوم", "هذا الأسبوع", "هذا الشهر", "نقداً", "أجل").forEach { filterName ->
                    FilterChip(
                        selected = selectedPeriodFilter == filterName,
                        onClick = { selectedPeriodFilter = filterName },
                        label = { Text(filterName, fontSize = 9.sp, fontWeight = if (selectedPeriodFilter == filterName) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Stats KPI Dashboard (Compact)
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                elevation = 2.dp,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ملخص المبيعات ($selectedPeriodFilter):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuTextPrimary
                        )
                        NeuPillBadge(
                            text = "${filteredList.size} فاتورة",
                            fontSize = 10.sp,
                            textColor = NeuAccentBlue
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Total Card
                        Surface(
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            color = NeuInsetBg
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("الإجمالي الكلي", fontSize = 9.sp, color = NeuTextSecondary)
                                Text(
                                    "${formatter.format(totalSalesSum)} ${storeConfig.currencySymbol}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeuAccentBlue
                                )
                            }
                        }

                        // Cash Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = NeuInsetBg
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("نقداً", fontSize = 9.sp, color = NeuTextSecondary)
                                Text(
                                    formatter.format(totalCashSum),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeuSuccess
                                )
                            }
                        }

                        // Credit Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = NeuInsetBg
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("آجل", fontSize = 9.sp, color = NeuTextSecondary)
                                Text(
                                    formatter.format(totalCreditSum),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeuError
                                )
                            }
                        }
                    }
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد فواتير مسجلة في السجل حتى الآن.",
                        color = NeuTextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.invoice.invoiceId }) { item ->
                        val inv = item.invoice
                        NeuCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedInvoiceForDetail = item },
                            shape = RoundedCornerShape(11.dp),
                            elevation = 3.dp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NeuPillBadge(
                                        text = "#${inv.invoiceNumber}",
                                        fontSize = 10.sp,
                                        textColor = NeuAccentBlue
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = inv.customerName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = NeuTextPrimary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = "${formatter.format(inv.grandTotal)} ${storeConfig.currencySymbol}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    color = NeuAccentBlue
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = inv.dateString,
                                        fontSize = 10.sp,
                                        color = NeuTextSecondary
                                    )
                                    NeuPillBadge(
                                        text = inv.paymentType,
                                        textColor = if (inv.paymentType.contains("نقداً")) NeuSuccess else NeuError,
                                        backgroundColor = if (inv.paymentType.contains("نقداً")) NeuSuccess.copy(alpha = 0.12f) else NeuError.copy(alpha = 0.12f),
                                        fontSize = 8.5.sp
                                    )
                                    Text(
                                        text = "(${item.items.size} أصناف)",
                                        fontSize = 9.5.sp,
                                        color = NeuTextMuted
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. PDF Share Button
                                    NeuCircleButton(
                                        onClick = {
                                            val pdfUri = viewModel.getShareablePdfUriForInvoice(inv, item.items)
                                            if (pdfUri != null) {
                                                val shareIntent = com.example.ui.components.InvoicePdfHelper.createSharePdfIntent(
                                                    context = context,
                                                    pdfUri = pdfUri,
                                                    invoiceNumber = inv.invoiceNumber,
                                                    customerName = inv.customerName
                                                )
                                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة كملف PDF"))
                                            } else {
                                                Toast.makeText(context, "تعذر إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        size = 28.dp
                                    ) {
                                        Icon(AppIcons.Pdf, contentDescription = "مشاركة PDF", tint = NeuAccentBlue, modifier = Modifier.size(13.dp))
                                    }

                                    // 2. Edit Button
                                    NeuCircleButton(
                                        onClick = { onEditInvoice(item) },
                                        size = 28.dp
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = NeuTextPrimary, modifier = Modifier.size(13.dp))
                                    }

                                    // 3. Print Button
                                    NeuCircleButton(
                                        onClick = {
                                            try {
                                                val bitmap = ReceiptBitmapHelper.createReceiptBitmapFromItems(
                                                    context = context,
                                                    storeConfig = storeConfig,
                                                    invoiceNumber = inv.invoiceNumber,
                                                    dateString = inv.dateString,
                                                    customerName = inv.customerName,
                                                    paymentType = inv.paymentType,
                                                    items = item.items,
                                                    receivedAmount = inv.paidAmount
                                                )
                                                if (printerState is PrinterConnectionState.Connected) {
                                                    viewModel.printInvoiceAsBitmap(bitmap)
                                                } else {
                                                    Toast.makeText(context, "الرجاء الاتصال بالطابعة أولاً من شاشة الطابعة", Toast.LENGTH_SHORT).show()
                                                    onNavigateToPrinterSetup()
                                                }
                                            } catch (e: Throwable) {
                                                Toast.makeText(context, "خطأ في الطباعة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        size = 28.dp
                                    ) {
                                        Icon(AppIcons.Print, contentDescription = "طباعة", tint = NeuTextPrimary, modifier = Modifier.size(13.dp))
                                    }

                                    // 4. Delete Button
                                    NeuCircleButton(
                                        onClick = { viewModel.deleteInvoice(inv.invoiceId) },
                                        size = 28.dp
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = NeuError, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
    selectedInvoiceForDetail?.let { item ->
        val inv = item.invoice
        AlertDialog(
            onDismissRequest = { selectedInvoiceForDetail = null },
            containerColor = NeuSurface,
            title = {
                Text(
                    text = "تفاصيل فاتورة #${inv.invoiceNumber} - ${inv.customerName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NeuTextPrimary
                )
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuButton(
                        onClick = {
                            val pdfUri = viewModel.getShareablePdfUriForInvoice(inv, item.items)
                            if (pdfUri != null) {
                                val shareIntent = com.example.ui.components.InvoicePdfHelper.createSharePdfIntent(
                                    context = context,
                                    pdfUri = pdfUri,
                                    invoiceNumber = inv.invoiceNumber,
                                    customerName = inv.customerName
                                )
                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة كملف PDF"))
                            } else {
                                Toast.makeText(context, "تعذر إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                            }
                        },
                        isPrimary = true
                    ) {
                        Icon(AppIcons.Pdf, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    NeuButton(
                        onClick = {
                            onEditInvoice(item)
                            selectedInvoiceForDetail = null
                        },
                        isPrimary = false
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = NeuTextPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تعديل", color = NeuTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedInvoiceForDetail = null }) {
                    Text("إغلاق", color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(4.dp)
                ) {
                    val rows = item.items.map { itm ->
                        com.example.ui.viewmodels.ReceiptRow(
                            description = itm.description,
                            quantityStr = if (itm.quantity > 0) itm.quantity.toString() else "1",
                            unitPriceStr = if (itm.unitPrice > 0) itm.unitPrice.toString() else "",
                            totalAmountStr = if (itm.totalAmount > 0) itm.totalAmount.toString() else ""
                        )
                    }.ifEmpty { listOf(com.example.ui.viewmodels.ReceiptRow()) }

                    InteractiveReceiptView(
                        storeConfig = storeConfig,
                        invoiceNumber = inv.invoiceNumber,
                        onInvoiceNumberChange = {},
                        dateString = inv.dateString,
                        onDateStringChange = {},
                        customerName = inv.customerName,
                        onCustomerNameChange = {},
                        paymentType = inv.paymentType,
                        onPaymentTypeChange = {},
                        rows = rows,
                        onUpdateDescription = { _, _ -> },
                        onUpdateQuantity = { _, _ -> },
                        onUpdateTotalAmount = { _, _ -> },
                        onRemoveRow = {},
                        onAddManualRow = {},
                        receivedAmountStr = if (inv.paidAmount > 0) inv.paidAmount.toString() else if (inv.discount > 0) inv.discount.toString() else "",
                        isEditable = false
                    )
                }
            }
        )
    }
}
