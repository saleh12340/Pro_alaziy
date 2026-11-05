package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerReceiptEntity
import com.example.data.local.InvoiceWithItems
import com.example.data.local.StoreConfigEntity
import com.example.ui.components.InvoicePdfHelper
import com.example.ui.theme.*
import com.example.ui.util.toEnglishDigits
import com.example.ui.util.toEnglishString
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class CustomerTx(
    val date: String,
    val title: String,
    val details: String,
    val amount: Double,
    val isDebit: Boolean,
    val id: Long,
    val rawType: String,
    val invoiceWithItems: InvoiceWithItems? = null,
    val receipt: CustomerReceiptEntity? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAccountStatementScreen(
    customerName: String,
    viewModel: InvoiceViewModel,
    onBack: () -> Unit,
    onEditInvoice: ((InvoiceWithItems) -> Unit)? = null,
    onNewInvoiceForCustomer: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val invoices by viewModel.allInvoices.collectAsState()
    val allReceipts by viewModel.allReceipts.collectAsState()
    val storeConfigState by viewModel.storeConfig.collectAsState()
    val config = storeConfigState ?: StoreConfigEntity()

    val formatter = remember { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)) }

    BackHandler {
        onBack()
    }

    var customerPhone by remember(customerName) {
        mutableStateOf(viewModel.getCustomerPhone(customerName))
    }
    var showEditPhoneDialog by remember { mutableStateOf(false) }

    // Normalize Yemeni mobile numbers for direct WhatsApp account linking.
    fun whatsappPhone(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("00")) digits = digits.drop(2)
        return when {
            digits.startsWith("967") -> digits
            digits.length == 9 && digits.startsWith("7") -> "967${digits}"
            digits.length == 10 && digits.startsWith("07") -> "967${digits.drop(1)}"
            digits.startsWith("0") && digits.length > 9 -> "967${digits.drop(1)}"
            else -> digits
        }
    }

    val customerInvoices = remember(invoices, customerName) {
        invoices.filter { it.invoice.customerName.trim() == customerName.trim() }
    }

    val customerReceipts = remember(allReceipts, customerName) {
        allReceipts.filter { it.customerName.trim() == customerName.trim() }
    }

    // Dialog state for adding transaction
    var showAddTxDialog by remember { mutableStateOf(false) }
    var txDirectionChoice by remember { mutableStateOf("له") } // "له" (سند قبض/تسديد) أو "عليه" (قيد دين/صرف)
    var txAmountStr by remember { mutableStateOf("") }
    var txTypeChoice by remember { mutableStateOf("سند قبض نقدي") }
    var txNotes by remember { mutableStateOf("") }

    // Multi-Selection State
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedTxKeys = remember { mutableStateListOf<String>() }

    // Expanded rows (to view details)
    val expandedTxKeys = remember { mutableStateListOf<String>() }

    // Dialogs state for edit/delete
    var showEditReceiptDialog by remember { mutableStateOf<CustomerReceiptEntity?>(null) }
    var showDeleteReceiptConfirmId by remember { mutableStateOf<Long?>(null) }
    var showDeleteInvoiceConfirmId by remember { mutableStateOf<Long?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val totalInvoices = customerInvoices.sumOf { it.invoice.grandTotal }
    val totalDebitReceipts = customerReceipts.filter { it.isDebit || it.paymentType.contains("عليه") || it.paymentType.contains("قيد دين") }.sumOf { it.amount }
    val totalCreditReceipts = customerReceipts.filter { !(it.isDebit || it.paymentType.contains("عليه") || it.paymentType.contains("قيد دين")) }.sumOf { it.amount }
    val totalDebits = totalInvoices + totalDebitReceipts
    val totalCredits = totalCreditReceipts
    val remainingBalance = totalDebits - totalCredits

    // Build transactions chronologically for running balance, then display newest first
    val transactionsWithBalance = remember(customerInvoices, customerReceipts) {
        val allTxs = mutableListOf<CustomerTx>()
        customerInvoices.forEach { inv ->
            allTxs.add(
                CustomerTx(
                    date = inv.invoice.dateString,
                    title = "فاتورة #${inv.invoice.invoiceNumber.toEnglishString()} (${inv.invoice.paymentType})",
                    details = "${inv.items.size.toEnglishString()} أصناف",
                    amount = inv.invoice.grandTotal,
                    isDebit = true,
                    id = inv.invoice.invoiceId,
                    rawType = "invoice",
                    invoiceWithItems = inv
                )
            )
        }
        customerReceipts.forEach { rec ->
            val isTxDebit = rec.isDebit || rec.paymentType.contains("عليه") || rec.paymentType.contains("قيد دين") || rec.paymentType.contains("مدين")
            allTxs.add(
                CustomerTx(
                    date = rec.dateString,
                    title = rec.paymentType,
                    details = rec.notes.ifEmpty { if (isTxDebit) "قيد دين / على العميل" else "سند قبض / تسديد للعميل" },
                    amount = rec.amount,
                    isDebit = isTxDebit,
                    id = rec.receiptId,
                    rawType = if (isTxDebit) "receipt_debit" else "receipt_credit",
                    receipt = rec
                )
            )
        }
        val chronological = allTxs.sortedWith(compareBy<CustomerTx> { it.date }.thenBy { it.id })

        var runningBal = 0.0
        val withBal = chronological.map { tx ->
            if (tx.isDebit) runningBal += tx.amount else runningBal -= tx.amount
            tx to runningBal
        }

        withBal.reversed()
    }

    fun getStatementRowsFor(txsWithBal: List<Pair<CustomerTx, Double>>): List<InvoicePdfHelper.CustomerStatementRow> {
        return txsWithBal.mapIndexed { idx, (tx, bal) ->
            InvoicePdfHelper.CustomerStatementRow(
                index = idx + 1,
                date = tx.date,
                type = when {
                    tx.rawType == "invoice" -> "فاتورة"
                    tx.isDebit -> "قيد دين (عليه)"
                    else -> "سند قبض (له)"
                },
                details = tx.details,
                debit = if (tx.isDebit) tx.amount else 0.0,
                credit = if (!tx.isDebit) tx.amount else 0.0,
                runningBalance = bal
            )
        }
    }

    Scaffold(
        containerColor = NeuBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ================================================================
            // 1. ELEGANT NAVY HEADER (رأس الشاشة الداكن والملفت)
            // ================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NavyPrimary,
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Top Bar: Back button + Title + Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "كشف حساب العميل",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFDBEAFE),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = customerName,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Header Action Icons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Print Thermal Statement
                            IconButton(
                                onClick = {
                                    val rows = getStatementRowsFor(transactionsWithBalance)
                                    viewModel.printCustomerStatementSummary(customerName, rows)
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    AppIcons.Print,
                                    contentDescription = "طباعة حرارية",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Share Statement PDF
                            IconButton(
                                onClick = {
                                    val rows = getStatementRowsFor(transactionsWithBalance)
                                    val uri = viewModel.getShareableCustomerStatementPdf(
                                        customerName = customerName,
                                        statementRows = rows,
                                        totalDebit = totalDebits,
                                        totalCredit = totalCredits,
                                        netBalance = remainingBalance
                                    )
                                    if (uri != null) {
                                        val shareIntent = InvoicePdfHelper.createShareStatementPdfIntent(context, uri, customerName)
                                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة كشف حساب $customerName"))
                                    } else {
                                        Toast.makeText(context, "تعذر تجهيز ملف كشف الحساب", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    AppIcons.Pdf,
                                    contentDescription = "تصدير PDF",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Share WhatsApp
                            IconButton(
                                onClick = {
                                    val textMsg = "مرحباً يا $customerName،\nكشف حسابك لدى ${config.storeNameArabic.ifEmpty { "متجرنا" }}:\n" +
                                            "• إجمالي المطلوب (عليه): ${formatter.format(totalDebits)} ${config.currencySymbol}\n" +
                                            "• إجمالي المسدد (له): ${formatter.format(totalCredits)} ${config.currencySymbol}\n" +
                                            "• الرصيد الحالي: ${formatter.format(if (remainingBalance < 0) -remainingBalance else remainingBalance)} ${config.currencySymbol} (${if (remainingBalance > 0) "عليه" else if (remainingBalance < 0) "له" else "خالص"})\n" +
                                            "شكراً لتعاملكم معنا."
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, textMsg)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "مشاركة الحساب"))
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "مشاركة نصية",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // Customer Contact Row & Compact Outstanding Balance Highlight
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Phone & WhatsApp Direct Contact
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable {
                                if (customerPhone.isNotBlank()) {
                                    val cleanPhone = whatsappPhone(customerPhone)
                                    val textMsg = "مرحباً يا $customerName،\nكشف حسابك لدى ${config.storeNameArabic.ifEmpty { "متجرنا" }}:\n" +
                                            "• إجمالي المطلوب (عليه): ${formatter.format(totalDebits)} ${config.currencySymbol}\n" +
                                            "• إجمالي المسدد (له): ${formatter.format(totalCredits)} ${config.currencySymbol}\n" +
                                            "• الرصيد الحالي: ${formatter.format(if (remainingBalance < 0) -remainingBalance else remainingBalance)} ${config.currencySymbol} (${if (remainingBalance > 0) "عليه" else if (remainingBalance < 0) "له" else "خالص"})\n" +
                                            "شكراً لتعاملكم معنا."
                                    val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(textMsg)}")
                                    val waIntent = Intent(Intent.ACTION_VIEW, waUri)
                                    try {
                                        context.startActivity(waIntent)
                                    } catch (e: Exception) {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone"))
                                        context.startActivity(dialIntent)
                                    }
                                } else {
                                    showEditPhoneDialog = true
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF25D366)), // WhatsApp Green
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "مراسلة واتساب",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = if (customerPhone.isNotBlank()) "$customerPhone (واتساب)" else "إضافة هاتف...",
                                fontSize = 11.5.sp,
                                color = if (customerPhone.isNotBlank()) Color(0xFF86EFAC) else Color(0xFFBFDBFE),
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "تعديل الهاتف",
                                tint = Color(0xFFBFDBFE),
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { showEditPhoneDialog = true }
                            )
                        }

                        // Compact Balance Highlight Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when {
                                    remainingBalance > 0 -> Color(0xFFEF4444) // Debt red
                                    remainingBalance < 0 -> Color(0xFF10B981) // Credit green
                                    else -> Color(0xFF64748B) // Settled
                                }
                            ) {
                                Text(
                                    text = when {
                                        remainingBalance > 0 -> "عليه: ${formatter.format(remainingBalance)} ${config.currencySymbol}"
                                        remainingBalance < 0 -> "له: ${formatter.format(-remainingBalance)} ${config.currencySymbol}"
                                        else -> "خالص الحساب"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ================================================================
            // 2. FIXED TRANSACTION INPUT DOCK (مربعات الإدخال الثابتة أعلى الشاشة - غير منبثقة)
            // ================================================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = BorderStroke(
                    1.dp,
                    if (txDirectionChoice == "له") Color(0xFFA7F3D0) else Color(0xFFFECACA)
                ),
                shadowElevation = 1.5.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1: Direction Segmented Switch (له vs عليه) + Quick "New Invoice" button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segmented Tab: للعميل (له) vs على العميل (عليه)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // له (للعميل)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (txDirectionChoice == "له") Color(0xFF059669) else Color.Transparent,
                                modifier = Modifier.clickable {
                                    txDirectionChoice = "له"
                                    txTypeChoice = "سند قبض نقدي"
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (txDirectionChoice == "له") Color.White else Color(0xFF64748B),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "للعميل (له / دائن)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (txDirectionChoice == "له") Color.White else Color(0xFF475569)
                                    )
                                }
                            }

                            // عليه (على العميل)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (txDirectionChoice == "عليه") Color(0xFFDC2626) else Color.Transparent,
                                modifier = Modifier.clickable {
                                    txDirectionChoice = "عليه"
                                    txTypeChoice = "قيد دين / رصيد سابق"
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (txDirectionChoice == "عليه") Color.White else Color(0xFF64748B),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "على العميل (عليه / مدين)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (txDirectionChoice == "عليه") Color.White else Color(0xFF475569)
                                    )
                                }
                            }
                        }

                        // Button: New Invoice for Customer (فاتورة جديدة للعميل)
                        Button(
                            onClick = {
                                if (onNewInvoiceForCustomer != null) {
                                    onNewInvoiceForCustomer(customerName)
                                } else {
                                    viewModel.resetForm()
                                    viewModel.customerName = customerName
                                    viewModel.paymentType = "أجل"
                                    Toast.makeText(context, "تم تجهيز فاتورة جديدة للعميل $customerName", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(7.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(AppIcons.Receipt, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Text("فاتورة جديدة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Single Row for Amount + Notes + Quick Type Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Amount Input Box
                        Surface(
                            modifier = Modifier
                                .weight(1.1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (txDirectionChoice == "له") Color(0xFFA7F3D0) else Color(0xFFFECACA))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = txAmountStr,
                                    onValueChange = { txAmountStr = it.toEnglishDigits() },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (txDirectionChoice == "له") Color(0xFF059669) else Color(0xFFDC2626)
                                    ),
                                    cursorBrush = SolidColor(if (txDirectionChoice == "له") Color(0xFF059669) else Color(0xFFDC2626)),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (txAmountStr.isEmpty()) {
                                            Text(text = "المبلغ...", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        }
                                        inner()
                                    }
                                )
                                Text(
                                    text = config.currencySymbol,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (txDirectionChoice == "له") Color(0xFF059669) else Color(0xFFDC2626)
                                )
                            }
                        }

                        // 2. Notes Input Box
                        Surface(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            BasicTextField(
                                value = txNotes,
                                onValueChange = { txNotes = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.sp, color = NeuTextPrimary),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp, vertical = 7.dp),
                                decorationBox = { inner ->
                                    if (txNotes.isEmpty()) {
                                        Text(text = "البيان / ملاحظات...", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                    inner()
                                }
                            )
                        }

                        // 3. Quick Full Debt Button (if remaining balance > 0)
                        if (remainingBalance > 0 && txDirectionChoice == "له") {
                            Surface(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clickable {
                                        txTypeChoice = "تسديد كامل"
                                        txAmountStr = remainingBalance.toInt().toString()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFECFDF5),
                                border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(12.dp))
                                    Text("كامل الدين", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                                }
                            }
                        }
                    }

                    // Row 3: Mini Choice Chips for Operation Type
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val quickTypes = if (txDirectionChoice == "له") {
                            listOf("سند قبض نقدي", "تسديد جزئي", "حوالة بنكية")
                        } else {
                            listOf("قيد دين", "بضاعة بالأجل", "رصيد سابق")
                        }
                        quickTypes.forEach { type ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (txTypeChoice == type) (if (txDirectionChoice == "له") Color(0xFFDCFCE7) else Color(0xFFFEE2E2)) else Color(0xFFF1F5F9),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (txTypeChoice == type) (if (txDirectionChoice == "له") Color(0xFF059669) else Color(0xFFDC2626)) else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .height(26.dp)
                                    .clickable { txTypeChoice = type }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = type,
                                        fontSize = 9.sp,
                                        fontWeight = if (txTypeChoice == type) FontWeight.Bold else FontWeight.Medium,
                                        color = if (txTypeChoice == type) (if (txDirectionChoice == "له") Color(0xFF065F46) else Color(0xFF991B1B)) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    // Row 4: Full-Width Save Button (زر الإضافة في صف كامل)
                    Button(
                        onClick = {
                            val amt = txAmountStr.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                val isDebit = txDirectionChoice == "عليه"
                                viewModel.addCustomerReceipt(
                                    customerName = customerName,
                                    amount = amt,
                                    paymentType = txTypeChoice,
                                    notes = txNotes,
                                    isDebit = isDebit
                                )
                                txAmountStr = ""
                                txNotes = ""
                                val msg = if (isDebit) "تم قيد دين بمبلغ ${formatter.format(amt)} على $customerName"
                                else "تم تسجيل سند قبض بمبلغ ${formatter.format(amt)} لصالح $customerName"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "يرجى كتابة مبلغ صحيح أكبر من صفر", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (txDirectionChoice == "له") Color(0xFF059669) else Color(0xFFDC2626)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(AppIcons.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text(
                                text = if (txDirectionChoice == "له") "حفظ وتسجيل دفعة للعميل (له)" else "حفظ وتسجيل قيد على العميل (عليه)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // ================================================================
            // 3. TRANSACTIONS LIST HEADER & ACTIONS
            // ================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سجل العمليات والفواتير:",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeuTextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Multi-select toggle
                    TextButton(
                        onClick = {
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) selectedTxKeys.clear()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isMultiSelectMode) "إلغاء التحديد" else "تحديد متعدد",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMultiSelectMode) Color(0xFFDC2626) else NavyPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEFF6FF)
                    ) {
                        Text(
                            text = "${transactionsWithBalance.size.toEnglishString()} حركة",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Multi-Select Action Bar (when selecting items)
            AnimatedVisibility(visible = isMultiSelectMode && selectedTxKeys.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تم تحديد ${selectedTxKeys.size.toEnglishString()} حركة",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Print Selected
                            IconButton(
                                onClick = {
                                    val selectedTxs = transactionsWithBalance.filter { "${it.first.rawType}_${it.first.id}" in selectedTxKeys }
                                    val rows = getStatementRowsFor(selectedTxs)
                                    viewModel.printCustomerStatementSummary(customerName, rows)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(AppIcons.Print, contentDescription = "طباعة المحدد", tint = NavyPrimary, modifier = Modifier.size(16.dp))
                            }

                            // Delete Selected
                            IconButton(
                                onClick = { showBatchDeleteConfirm = true },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف المحدد", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ================================================================
            // 4. TRANSACTIONS LIST (سجل العمليات المحسن والمميز لونياً)
            // ================================================================
            if (transactionsWithBalance.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Receipt, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(26.dp))
                        }
                        Text(
                            text = "لا توجد حركات أو فواتير مسجلة لهذا العميل",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuTextSecondary
                        )
                        Text(
                            text = "اضغط على (إضافة عملية جديدة) لتسجيل دفعة أو (فاتورة جديدة)",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(transactionsWithBalance, key = { "${it.first.rawType}_${it.first.id}" }) { (tx, runBal) ->
                        val itemKey = "${tx.rawType}_${tx.id}"
                        val isSelected = itemKey in selectedTxKeys
                        val isExpanded = itemKey in expandedTxKeys

                        val isInvoice = tx.rawType == "invoice"

                        val cardBg = if (tx.isDebit) Color(0xFFFFF1F2) else Color(0xFFECFDF3)
                        val borderColor = when {
                            isSelected -> NavyPrimary
                            tx.isDebit -> Color(0xFFEF4444)
                            else -> Color(0xFF22C55E)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isMultiSelectMode) {
                                        if (isSelected) selectedTxKeys.remove(itemKey) else selectedTxKeys.add(itemKey)
                                    } else {
                                        if (isExpanded) expandedTxKeys.remove(itemKey) else expandedTxKeys.add(itemKey)
                                    }
                                },
                            shape = RoundedCornerShape(5.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderColor),
                            shadowElevation = if (isSelected) 2.dp else 0.5.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Right-to-left ruler row: date/time, amount, details; balance stays at far left.
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (isMultiSelectMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) selectedTxKeys.add(itemKey) else selectedTxKeys.remove(itemKey)
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // 1. Date & Time (التاريخ والوقت في الجهة اليمنى)
                                        Text(
                                            text = tx.date,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = NeuTextSecondary,
                                            maxLines = 1
                                        )

                                        // 2. Amount (المبلغ بلون أحمر إذا كان عليه، وأخضر إذا كان له)
                                        Text(
                                            text = "${formatter.format(tx.amount)} ${config.currencySymbol}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (tx.isDebit) Color(0xFFDC2626) else Color(0xFF059669),
                                            maxLines = 1
                                        )

                                        // 3. Details (التفاصيل بجانب المبلغ)
                                        Text(
                                            text = tx.details.ifEmpty { tx.title },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeuTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Left: Balance (الرصيد في جهة اليسار)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFF1F5F9),
                                        modifier = Modifier.padding(start = 6.dp)
                                    ) {
                                        Text(
                                            text = "الرصيد: ${formatter.format(if (runBal < 0) -runBal else runBal)} ${if (runBal > 0) "عليه" else if (runBal < 0) "له" else "خالص"}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                runBal > 0 -> Color(0xFFDC2626)
                                                runBal < 0 -> Color(0xFF059669)
                                                else -> Color(0xFF64748B)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                // Expanded Details Section (when clicked)
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isInvoice && tx.invoiceWithItems != null) {
                                            Text(
                                                text = "تفاصيل الأصناف المباعة:",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeuTextPrimary
                                            )
                                            tx.invoiceWithItems.items.forEachIndexed { i, itm ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "${i + 1}. ${itm.description} (${itm.quantity} × ${formatter.format(itm.unitPrice)})",
                                                        fontSize = 10.sp,
                                                        color = NeuTextSecondary
                                                    )
                                                    Text(
                                                        text = "${formatter.format(itm.totalAmount)} ${config.currencySymbol}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = NeuTextPrimary
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                if (onEditInvoice != null) {
                                                    TextButton(
                                                        onClick = { onEditInvoice(tx.invoiceWithItems) },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(12.dp))
                                                        Spacer(Modifier.width(2.dp))
                                                        Text("تعديل الفاتورة", fontSize = 10.5.sp, color = NavyPrimary)
                                                    }
                                                }
                                                TextButton(
                                                    onClick = { showDeleteInvoiceConfirmId = tx.id },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(12.dp))
                                                    Spacer(Modifier.width(2.dp))
                                                    Text("حذف", fontSize = 10.5.sp, color = Color(0xFFDC2626))
                                                }
                                            }
                                        } else if (tx.receipt != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "ملاحظات: ${tx.receipt.notes.ifBlank { "لا توجد ملاحظات إضافية" }}",
                                                    fontSize = 10.sp,
                                                    color = NeuTextSecondary
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    TextButton(
                                                        onClick = { viewModel.printCustomerReceiptVoucher(tx.receipt) },
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                                    ) {
                                                        Icon(AppIcons.Print, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(12.dp))
                                                        Spacer(Modifier.width(2.dp))
                                                        Text("طباعة", fontSize = 10.5.sp, color = NavyPrimary)
                                                    }
                                                    TextButton(
                                                        onClick = { showEditReceiptDialog = tx.receipt },
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(12.dp))
                                                        Spacer(Modifier.width(2.dp))
                                                        Text("تعديل", fontSize = 10.5.sp, color = NavyPrimary)
                                                    }
                                                    TextButton(
                                                        onClick = { showDeleteReceiptConfirmId = tx.id },
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(12.dp))
                                                        Spacer(Modifier.width(2.dp))
                                                        Text("حذف", fontSize = 10.5.sp, color = Color(0xFFDC2626))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
    }

    // ================================================================
    // DIALOG: ADD NEW TRANSACTION (إضافة عملية جديدة: للعميل أو عليه)
    // ================================================================
    if (showAddTxDialog) {
        val isForCustomer = txDirectionChoice == "له"
        val activeColor = if (isForCustomer) Color(0xFF059669) else Color(0xFFDC2626)
        val activeBg = if (isForCustomer) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)

        AlertDialog(
            onDismissRequest = { showAddTxDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(activeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isForCustomer) AppIcons.Payment else Icons.Default.Add,
                            contentDescription = null,
                            tint = activeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isForCustomer) "تسجيل دفعة / سند قبض (للعميل)" else "تسجيل قيد دين / مديونية (على العميل)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = NeuTextPrimary
                        )
                        Text(
                            text = "العميل: $customerName",
                            fontSize = 11.sp,
                            color = NeuTextSecondary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Direction Tabs: للعميل (له / دائن) vs على العميل (عليه / مدين)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Option 1: للعميل (له / دائن)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        txDirectionChoice = "له"
                                        txTypeChoice = "سند قبض نقدي"
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isForCustomer) Color(0xFF059669) else Color.Transparent,
                                shadowElevation = if (isForCustomer) 1.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isForCustomer) Color.White else Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "للعميل (له / دائن)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = if (isForCustomer) Color.White else Color(0xFF475569)
                                    )
                                }
                            }

                            // Option 2: على العميل (عليه / مدين)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        txDirectionChoice = "عليه"
                                        txTypeChoice = "قيد دين / رصيد سابق"
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (!isForCustomer) Color(0xFFDC2626) else Color.Transparent,
                                shadowElevation = if (!isForCustomer) 1.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (!isForCustomer) Color.White else Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "على العميل (عليه / مدين)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = if (!isForCustomer) Color.White else Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Transaction Type Chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isForCustomer) "نوع سند الدفعة:" else "نوع قيد المديونية:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuTextPrimary
                        )

                        val chipsList = if (isForCustomer) {
                            listOf("سند قبض نقدي", "تسديد جزئي", "تسديد كامل", "دفعة مقدمة")
                        } else {
                            listOf("قيد دين / رصيد سابق", "مديونية إضافية", "بضاعة بالأجل", "مصاريف / خدمات")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chipsList.take(2).forEach { type ->
                                FilterChip(
                                    selected = txTypeChoice == type,
                                    onClick = {
                                        txTypeChoice = type
                                        if (type == "تسديد كامل" && remainingBalance > 0) {
                                            txAmountStr = remainingBalance.toInt().toString()
                                        }
                                    },
                                    label = { Text(type, fontSize = 10.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = activeColor,
                                        selectedLabelColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chipsList.drop(2).forEach { type ->
                                FilterChip(
                                    selected = txTypeChoice == type,
                                    onClick = {
                                        txTypeChoice = type
                                        if (type == "تسديد كامل" && remainingBalance > 0) {
                                            txAmountStr = remainingBalance.toInt().toString()
                                        }
                                    },
                                    label = { Text(type, fontSize = 10.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = activeColor,
                                        selectedLabelColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // 3. Fast Auto-Fill Full Debt Button (زر سريع لملء كامل الدين بضغطة واحدة)
                    if (remainingBalance > 0 && isForCustomer) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    txTypeChoice = "تسديد كامل"
                                    txAmountStr = remainingBalance.toInt().toString()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "زر سريع: ملء كامل الدين تلقائياً",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF065F46)
                                    )
                                }
                                Text(
                                    text = "${formatter.format(remainingBalance)} ${config.currencySymbol}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF059669)
                                )
                            }
                        }
                    }

                    // 4. Amount Input Field
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isForCustomer) "المبلغ الواصل المسدد (${config.currencySymbol}) *:" else "المبلغ المطلوب قيده عليه (${config.currencySymbol}) *:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeuTextPrimary
                            )
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (isForCustomer) Color(0xFFA7F3D0) else Color(0xFFFECACA))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = txAmountStr,
                                    onValueChange = { txAmountStr = it.toEnglishDigits() },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = activeColor
                                    ),
                                    cursorBrush = SolidColor(activeColor),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (txAmountStr.isEmpty()) {
                                            Text(
                                                text = if (isForCustomer) "أدخل المبلغ المستلم من العميل..." else "أدخل مبلغ الدين الإضافي...",
                                                fontSize = 12.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        inner()
                                    }
                                )
                                Text(
                                    text = config.currencySymbol,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeColor
                                )
                            }
                        }
                    }

                    // 5. Notes / Statement Field
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "بيان / ملاحظات العملية:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuTextPrimary
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            BasicTextField(
                                value = txNotes,
                                onValueChange = { txNotes = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 12.sp, color = NeuTextPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                decorationBox = { inner ->
                                    if (txNotes.isEmpty()) {
                                        Text(
                                            text = if (isForCustomer) "مثال: تسديد نقدي بيد العميل / حوالة..." else "مثال: رصيد سابق من الدفتر / صرف بضاعة...",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = txAmountStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            val isDebit = txDirectionChoice == "عليه"
                            viewModel.addCustomerReceipt(
                                customerName = customerName,
                                amount = amt,
                                paymentType = txTypeChoice,
                                notes = txNotes,
                                isDebit = isDebit
                            )
                            showAddTxDialog = false
                            val msg = if (isDebit) "تم قيد دين بمبلغ ${formatter.format(amt)} على $customerName بنجاح"
                            else "تم تسجيل سند قبض بمبلغ ${formatter.format(amt)} لصالح $customerName بنجاح"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "يرجى كتابة مبلغ صحيح أكبر من صفر", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activeColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = if (isForCustomer) AppIcons.Payment else Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isForCustomer) "حفظ كسند قبض (له)" else "حفظ كقيد دين (عليه)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAddTxDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("إلغاء", color = NeuTextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    // ================================================================
    // DIALOG: EDIT CUSTOMER PHONE
    // ================================================================
    if (showEditPhoneDialog) {
        var newPhone by remember { mutableStateOf(customerPhone) }
        AlertDialog(
            onDismissRequest = { showEditPhoneDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = { Text("تعديل رقم هاتف العميل", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NeuTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("أدخل رقم هاتف $customerName:", fontSize = 12.sp, color = NeuTextSecondary)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        BasicTextField(
                            value = newPhone,
                            onValueChange = { newPhone = it.toEnglishDigits() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCustomerPhone(customerName, newPhone)
                        customerPhone = newPhone
                        showEditPhoneDialog = false
                        Toast.makeText(context, "تم حفظ رقم الهاتف بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text("حفظ", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditPhoneDialog = false }) {
                    Text("إلغاء", color = NeuTextSecondary)
                }
            }
        )
    }

    // ================================================================
    // DIALOG: EDIT RECEIPT
    // ================================================================
    showEditReceiptDialog?.let { receiptToEdit ->
        var editAmtStr by remember { mutableStateOf(receiptToEdit.amount.toString().replace(".0", "")) }
        var editTypeChoice by remember { mutableStateOf(receiptToEdit.paymentType) }
        var editNotes by remember { mutableStateOf(receiptToEdit.notes) }
        var editIsDebit by remember { mutableStateOf(receiptToEdit.isDebit) }
        val editColor = if (!editIsDebit) Color(0xFF059669) else Color(0xFFDC2626)

        AlertDialog(
            onDismissRequest = { showEditReceiptDialog = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = if (!editIsDebit) "تعديل سند القبض (له)" else "تعديل قيد الدين (عليه)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NeuTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Direction Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = !editIsDebit,
                            onClick = { editIsDebit = false },
                            label = { Text("للعميل (له / دائن)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF059669),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = editIsDebit,
                            onClick = { editIsDebit = true },
                            label = { Text("على العميل (عليه / مدين)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFDC2626),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("المبلغ:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (!editIsDebit) Color(0xFFA7F3D0) else Color(0xFFFECACA))
                    ) {
                        BasicTextField(
                            value = editAmtStr,
                            onValueChange = { editAmtStr = it.toEnglishDigits() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = editColor),
                            modifier = Modifier.padding(9.dp)
                        )
                    }

                    Text("البيان / نوع الحركة:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        BasicTextField(
                            value = editTypeChoice,
                            onValueChange = { editTypeChoice = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, color = NeuTextPrimary),
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Text("ملاحظات إضافية:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        BasicTextField(
                            value = editNotes,
                            onValueChange = { editNotes = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, color = NeuTextPrimary),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = editAmtStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            viewModel.updateCustomerReceipt(
                                receiptToEdit.copy(
                                    amount = amt,
                                    paymentType = editTypeChoice,
                                    notes = editNotes,
                                    isDebit = editIsDebit
                                )
                            )
                            showEditReceiptDialog = null
                            Toast.makeText(context, "تم تحديث العملية بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = editColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("حفظ التعديل", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditReceiptDialog = null }, shape = RoundedCornerShape(8.dp)) {
                    Text("إلغاء", color = NeuTextSecondary)
                }
            }
        )
    }

    // ================================================================
    // DELETE CONFIRMATION DIALOGS
    // ================================================================
    showDeleteReceiptConfirmId?.let { receiptId ->
        AlertDialog(
            onDismissRequest = { showDeleteReceiptConfirmId = null },
            containerColor = Color.White,
            title = { Text("تأكيد حذف السند", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFDC2626)) },
            text = { Text("هل أنت متأكد من حذف هذا السند؟ سيتم تعديل رصيد العميل فوراً.", fontSize = 12.5.sp, color = NeuTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomerReceipt(receiptId)
                        showDeleteReceiptConfirmId = null
                        Toast.makeText(context, "تم حذف السند", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("نعم، حذف", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteReceiptConfirmId = null }) {
                    Text("إلغاء", color = NeuTextSecondary)
                }
            }
        )
    }

    showDeleteInvoiceConfirmId?.let { invoiceId ->
        AlertDialog(
            onDismissRequest = { showDeleteInvoiceConfirmId = null },
            containerColor = Color.White,
            title = { Text("تأكيد حذف الفاتورة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFDC2626)) },
            text = { Text("هل أنت متأكد من حذف هذه الفاتورة وكافة أصنافها؟", fontSize = 12.5.sp, color = NeuTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInvoice(invoiceId)
                        showDeleteInvoiceConfirmId = null
                        Toast.makeText(context, "تم حذف الفاتورة", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("نعم، حذف", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteInvoiceConfirmId = null }) {
                    Text("إلغاء", color = NeuTextSecondary)
                }
            }
        )
    }

    if (showBatchDeleteConfirm) {
        val invoiceCount = selectedTxKeys.count { it.startsWith("invoice_") }
        val receiptCount = selectedTxKeys.count { it.startsWith("receipt_") }

        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            containerColor = Color.White,
            title = { Text("تأكيد الحذف الجماعي", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFDC2626)) },
            text = {
                Text(
                    "هل أنت متأكد من حذف ($invoiceCount فاتورة و $receiptCount سند) المحددة؟ لا يمكن التراجع عن هذه العملية.",
                    fontSize = 12.5.sp,
                    color = NeuTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val invoiceIds = selectedTxKeys.filter { it.startsWith("invoice_") }.map { it.removePrefix("invoice_").toLong() }
                        val receiptIds = selectedTxKeys.filter { it.startsWith("receipt_") }.map { it.removePrefix("receipt_").toLong() }

                        if (invoiceIds.isNotEmpty()) viewModel.deleteMultipleInvoices(invoiceIds)
                        if (receiptIds.isNotEmpty()) viewModel.deleteMultipleReceipts(receiptIds)

                        selectedTxKeys.clear()
                        isMultiSelectMode = false
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("نعم، حذف الكل", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("إلغاء", color = NeuTextSecondary)
                }
            }
        )
    }
}
