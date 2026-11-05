package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.StoreConfigEntity
import com.example.printer.PrinterConnectionState
import com.example.printer.ThermalImageConverter
import com.example.ui.components.InteractiveReceiptView
import com.example.ui.components.AutoSelectBasicTextField
import com.example.ui.components.ReceiptBitmapHelper
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.example.ui.util.NumberUtils
import com.example.ui.util.toEnglishDigits
import com.example.ui.util.toCleanDouble
import com.example.ui.util.formatCleanNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewInvoiceScreen(
    viewModel: InvoiceViewModel,
    onNavigateToPrinterSetup: () -> Unit
) {
    val context = LocalContext.current
    val storeConfigState by viewModel.storeConfig.collectAsState()
    val storeConfig = storeConfigState ?: StoreConfigEntity()
    val allProducts by viewModel.allProducts.collectAsState()
    val allInvoices by viewModel.allInvoices.collectAsState()
    val allReceipts by viewModel.allReceipts.collectAsState()
    val printerState by viewModel.printerState.collectAsState()

    var showProductPickerSheet by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showThermalPreviewDialog by remember { mutableStateOf(false) }
    var showClearInvoiceDialog by remember { mutableStateOf(false) }

    val formatter = DecimalFormat("#,##0.##")
    val customerSuggestions = remember(allInvoices, allReceipts, viewModel.customerName) {
        val q = viewModel.customerName.trim()
        val invNames = allInvoices.map { it.invoice.customerName.trim() }
        val recNames = allReceipts.map { it.customerName.trim() }
        (invNames + recNames)
            .asSequence()
            .filter { it.isNotBlank() && it != "عميل نقدي" && (q.isBlank() || it.contains(q, ignoreCase = true)) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(6)
            .toList()
    }

    Scaffold(
        containerColor = NeuBackground,
        topBar = {
            // Compressed Neumorphic Top Bar: smaller icons, raised higher
            Surface(
                color = NeuSurfaceRaised,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "فاتورة #${viewModel.invoiceNumber} - ${storeConfig.storeNameArabic.ifEmpty { "بقالة العزي" }}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = NeuTextPrimary
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Quick Product Catalog Pick
                        NeuCircleButton(
                            onClick = { showProductPickerSheet = true },
                            size = 28.dp
                        ) {
                            Icon(
                                AppIcons.Category,
                                contentDescription = "دليل الأصناف",
                                tint = NeuAccentBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // 2. Printer Status Indicator
                        NeuCircleButton(
                            onClick = onNavigateToPrinterSetup,
                            size = 28.dp
                        ) {
                            when (printerState) {
                                is PrinterConnectionState.Connected -> Icon(
                                    AppIcons.Print,
                                    contentDescription = "الطابعة متصلة",
                                    tint = NeuSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                is PrinterConnectionState.Connecting -> CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = NeuAccentBlue
                                )
                                else -> Icon(
                                    AppIcons.PrintDisabled,
                                    contentDescription = "غير متصل بالطابعة",
                                    tint = NeuError,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // 3. New Clean Operation / Reset Form (نظافة عملية جديدة)
                        NeuCircleButton(
                            onClick = {
                                if (viewModel.receiptRows.any { it.hasData } || viewModel.customerName.isNotBlank()) {
                                    showClearInvoiceDialog = true
                                } else {
                                    viewModel.resetForm()
                                }
                            },
                            size = 28.dp
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "عملية جديدة / تصفير",
                                tint = NeuTextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Neumorphic Bottom Action Card
            NeuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = 3.dp,
                contentPadding = PaddingValues(6.dp)
            ) {
                // Grand Total Display (Sunken Neumorphic Box with Discount field)
                NeuInsetBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "المبلغ الواصل:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeuTextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                modifier = Modifier
                                    .width(62.dp)
                                    .height(26.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = NeuSurfaceRaised
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicTextField(
                                        value = viewModel.receivedAmountStr,
                                        onValueChange = { viewModel.receivedAmountStr = it.toEnglishDigits() },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeuSuccess,
                                            textAlign = TextAlign.Center
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        cursorBrush = SolidColor(NeuAccentBlue),
                                        decorationBox = { inner ->
                                            if (viewModel.receivedAmountStr.isEmpty()) {
                                                Text("0", fontSize = 10.5.sp, color = NeuTextMuted, textAlign = TextAlign.Center)
                                            }
                                            inner()
                                        }
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (viewModel.getReceivedAmount() > 0) {
                                Text(
                                    text = "المتبقي: ${formatter.format((viewModel.calculateGrandTotal() - viewModel.getReceivedAmount()).coerceAtLeast(0.0))}",
                                    fontSize = 9.5.sp,
                                    color = NeuTextSecondary
                                )
                            }
                            Text(
                                text = "${formatter.format(viewModel.calculateGrandTotal())} ${storeConfig.currencySymbol}",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = NeuAccentBlue
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Neumorphic Action Buttons (Compact)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Save Button (Soft Raised)
                    NeuButton(
                        onClick = { viewModel.saveInvoiceToDatabase() },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        isPrimary = false
                    ) {
                        Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(15.dp), tint = NeuTextPrimary)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("حفظ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    }

                    // 2. Thermal Print Button (Vibrant #0072ff Glow)
                    NeuButton(
                        onClick = { showThermalPreviewDialog = true },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        isPrimary = true
                    ) {
                        Icon(AppIcons.Print, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("طباعة حرارية", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // 3. Save / Share Image
                    NeuButton(
                        onClick = { showPreviewDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        isPrimary = false
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = NeuAccentBlue)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("مشاركة", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 0. SMART VOICE INPUT BAR (AT TOP OF PAGE WITH TEXT WRAPPING)
            item {
                VoiceInvoiceInputBar(
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // 1. QUICK ITEM ENTRY RULER ROW (AT TOP OF PAGE)
            item {
                QuickItemRulerBar(
                    onAddItem = { name, qty, up, tot ->
                        viewModel.addQuickItem(name, qty, up, tot)
                    }
                )
            }

            // 2. MAIN DIRECT INPUT PAPER RECEIPT VIEW (DUAL COLUMN)
            item {
                InteractiveReceiptView(
                    storeConfig = storeConfig,
                    invoiceNumber = viewModel.invoiceNumber,
                    onInvoiceNumberChange = { viewModel.invoiceNumber = it },
                    dateString = viewModel.dateString,
                    onDateStringChange = { viewModel.dateString = it },
                    customerName = viewModel.customerName,
                    onCustomerNameChange = { viewModel.customerName = it },
                    paymentType = viewModel.paymentType,
                    onPaymentTypeChange = { viewModel.paymentType = it },
                    rows = viewModel.receiptRows,
                    onUpdateDescription = { idx, v -> viewModel.updateDescription(idx, v) },
                    onUpdateQuantity = { idx, v -> viewModel.updateQuantity(idx, v) },
                    onUpdateUnitPrice = { idx, v -> viewModel.updateUnitPrice(idx, v) },
                    onUpdateTotalAmount = { idx, v -> viewModel.updateTotalAmount(idx, v) },
                    onRemoveRow = { idx -> viewModel.removeRow(idx) },
                    onAddManualRow = { viewModel.addManualRow() },
                    customerSuggestions = customerSuggestions,
                    onCustomerSuggestionClick = { selected -> viewModel.customerName = selected },
                    receivedAmountStr = viewModel.receivedAmountStr,
                    onReceivedAmountChange = { viewModel.receivedAmountStr = it }
                )
            }
        }
    }

    // Quick Product Picker Modal
    if (showProductPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProductPickerSheet = false },
            containerColor = NeuSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .fillMaxHeight(0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختر صنفاً لإدراجه مباشرة في الفاتورة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NeuTextPrimary
                    )
                    IconButton(onClick = { showProductPickerSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = NeuTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                var searchFilter by remember { mutableStateOf("") }
                NeuInsetBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NeuTextMuted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = searchFilter,
                            onValueChange = { searchFilter = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary),
                            cursorBrush = SolidColor(NeuAccentBlue),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchFilter.isEmpty()) {
                                    Text("ابحث عن صنف...", fontSize = 13.sp, color = NeuTextMuted)
                                }
                                inner()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val availableProducts = remember(allProducts, searchFilter) {
                    if (searchFilter.isBlank()) allProducts
                    else allProducts.filter { it.name.contains(searchFilter, ignoreCase = true) }
                }

                if (availableProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (allProducts.isEmpty()) "لا توجد أصناف في الدليل بعد. أضف أصنافاً من تبويب 'الأصناف'." else "لم يتم العثور على نتائج.",
                            color = NeuTextMuted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableProducts) { product ->
                            NeuCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addCatalogProductToInvoice(product.name, product.defaultUnitPrice)
                                        showProductPickerSheet = false
                                    },
                                shape = RoundedCornerShape(14.dp),
                                elevation = 4.dp,
                                contentPadding = PaddingValues(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NeuTextPrimary)
                                        Text(product.category, fontSize = 11.sp, color = NeuTextSecondary)
                                    }
                                    Text(
                                        text = "${formatter.format(product.defaultUnitPrice)} ${storeConfig.currencySymbol}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = NeuAccentBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Exact thermal bitmap preview: the same monochrome 384-dot bitmap is sent to Bluetooth.
    if (showThermalPreviewDialog) {
        val thermalRowsKey = viewModel.receiptRows.map { "${it.description}|${it.quantityStr}|${it.totalAmountStr}" }
        val thermalBitmap = remember(
            viewModel.invoiceNumber,
            viewModel.dateString,
            viewModel.customerName,
            viewModel.paymentType,
            thermalRowsKey,
            viewModel.receivedAmountStr,
            storeConfig
        ) {
            val widthDots = storeConfig.printerWidthDots
            val receiptBitmap = ReceiptBitmapHelper.createReceiptBitmap(
                context = context,
                storeConfig = storeConfig,
                invoiceNumber = viewModel.invoiceNumber,
                dateString = viewModel.dateString,
                customerName = viewModel.customerName,
                paymentType = viewModel.paymentType,
                rows = viewModel.receiptRows,
                receivedAmount = viewModel.getReceivedAmount(),
                widthPx = widthDots
            )
            ThermalImageConverter.prepareForThermalPrinter(receiptBitmap, targetWidthDots = widthDots)
        }

        AlertDialog(
            onDismissRequest = { showThermalPreviewDialog = false },
            containerColor = NeuSurface,
            title = {
                Text(
                    "معاينة الطباعة الحرارية (${storeConfig.printerPaperWidth} مم)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NeuTextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "هذه صورة عالية الوضوح بعرض ${storeConfig.printerWidthDots} نقطة (ورق ${storeConfig.printerPaperWidth} مم)، وهي الصورة التي ستُرسل للطابعة.",
                        fontSize = 11.sp,
                        color = NeuTextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = thermalBitmap.asImageBitmap(),
                        contentDescription = "معاينة الصورة الحرارية النهائية",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(4.dp),
                        contentScale = ContentScale.FillWidth
                    )
                }
            },
            confirmButton = {
                NeuButton(
                    onClick = {
                        if (printerState is PrinterConnectionState.Connected) {
                            viewModel.printInvoiceAsBitmap(thermalBitmap, storeConfig.printerWidthDots)
                            showThermalPreviewDialog = false
                        } else {
                            Toast.makeText(context, "الرجاء الاتصال بالطابعة أولاً", Toast.LENGTH_SHORT).show()
                            showThermalPreviewDialog = false
                            onNavigateToPrinterSetup()
                        }
                    },
                    isPrimary = true
                ) {
                    Icon(AppIcons.Print, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("طباعة هذه الصورة", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showThermalPreviewDialog = false }) {
                    Text("رجوع للتعديل", color = NeuTextSecondary)
                }
            }
        )
    }

    // Invoice Image Preview & Share Dialog
    if (showPreviewDialog) {
        val invoiceBitmap = remember(viewModel.receiptRows, viewModel.receivedAmountStr, storeConfig) {
            ReceiptBitmapHelper.createReceiptBitmap(
                context = context,
                storeConfig = storeConfig,
                invoiceNumber = viewModel.invoiceNumber,
                dateString = viewModel.dateString,
                customerName = viewModel.customerName,
                paymentType = viewModel.paymentType,
                rows = viewModel.receiptRows,
                receivedAmount = viewModel.getReceivedAmount(),
                widthPx = 2160 // 4K Ultra-HD resolution
            )
        }

        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            containerColor = NeuSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeuBadge(size = 38.dp) {
                        Icon(AppIcons.Pdf, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            text = "تصدير ومشاركة الفاتورة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NeuTextPrimary
                        )
                        Text(
                            text = "مستند PDF عالي الجودة أو صورة PNG",
                            fontSize = 11.sp,
                            color = NeuTextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Share as PDF (Primary Action)
                    NeuButton(
                        onClick = {
                            val pdfUri = viewModel.getShareableInvoicePdfUri()
                            if (pdfUri != null) {
                                val shareIntent = com.example.ui.components.InvoicePdfHelper.createSharePdfIntent(
                                    context = context,
                                    pdfUri = pdfUri,
                                    invoiceNumber = viewModel.invoiceNumber,
                                    customerName = viewModel.customerName
                                )
                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة كملف PDF"))
                            } else {
                                Toast.makeText(context, "تعذر إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isPrimary = true
                    ) {
                        Icon(AppIcons.Pdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة كملف PDF احترافي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }

                    // 2. Secondary Row: Save PDF / Share Image / Save Image
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Save PDF to Downloads
                        NeuButton(
                            onClick = {
                                viewModel.saveInvoicePdfToDownloads()
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        ) {
                            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeuAccentBlue)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("حفظ PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                        }

                        // Share Image
                        NeuButton(
                            onClick = {
                                val uri = viewModel.getShareableImageUri(invoiceBitmap)
                                if (uri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة صورة الفاتورة"))
                                } else {
                                    Toast.makeText(context, "تعذر تجهيز الصورة للمشاركة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeuTextPrimary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("مشاركة صورة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                        }

                        // Save Image to Gallery
                        NeuButton(
                            onClick = {
                                viewModel.saveInvoiceImageToGallery(invoiceBitmap)
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        ) {
                            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeuTextPrimary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("حفظ صورة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPreviewDialog = false }) {
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
                    InteractiveReceiptView(
                        storeConfig = storeConfig,
                        invoiceNumber = viewModel.invoiceNumber,
                        onInvoiceNumberChange = {},
                        dateString = viewModel.dateString,
                        onDateStringChange = {},
                        customerName = viewModel.customerName,
                        onCustomerNameChange = {},
                        paymentType = viewModel.paymentType,
                        onPaymentTypeChange = {},
                        rows = viewModel.receiptRows.filterNot { it.isCompletelyEmpty }.ifEmpty {
                            listOf(com.example.ui.viewmodels.ReceiptRow())
                        },
                        onUpdateDescription = { _, _ -> },
                        onUpdateQuantity = { _, _ -> },
                        onUpdateTotalAmount = { _, _ -> },
                        onRemoveRow = {},
                        onAddManualRow = {},
                        isEditable = false
                    )
                }
            }
        )
    }

    // Dialog for "نظافة عملية جديدة" (Clear current invoice and start clean new operation)
    if (showClearInvoiceDialog) {
        AlertDialog(
            onDismissRequest = { showClearInvoiceDialog = false },
            containerColor = NeuSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("بدء عملية جديدة (تصفير الفاتورة)", fontWeight = FontWeight.Black, fontSize = 15.sp, color = NeuTextPrimary)
                }
            },
            text = {
                Text(
                    "هل ترغب في تنظيف وتصفير بنود الفاتورة الحالية والبدء بعملية بيع جديدة نظيفة؟",
                    fontSize = 13.sp,
                    color = NeuTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetForm()
                        showClearInvoiceDialog = false
                        Toast.makeText(context, "تم تجهيز فاتورة جديدة نظيفة", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuAccentBlue)
                ) {
                    Text("نعم، عملية جديدة", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearInvoiceDialog = false }) {
                    Text("إلغاء", color = NeuTextPrimary)
                }
            }
        )
    }
}

@Composable
fun QuickItemRulerBar(
    onAddItem: (name: String, quantity: String, unitPrice: String, total: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var itemName by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("1") }
    var unitPriceStr by remember { mutableStateOf("") }
    var totalAmountStr by remember { mutableStateOf("") }

    val submitItem = {
        val q = quantityStr.toCleanDouble().let { if (it > 0.0) it else 1.0 }
        val up = unitPriceStr.toCleanDouble()
        val tot = totalAmountStr.toCleanDouble()

        val finalUp = if (up > 0.0) up else if (tot > 0.0 && q > 0.0) tot / q else 0.0
        val finalTot = if (tot > 0.0) tot else if (up > 0.0) up * q else 0.0

        val cleanName = itemName.trim()
        val cleanQty = formatCleanNumber(q).ifBlank { "1" }
        val cleanUpStr = if (finalUp > 0.0) formatCleanNumber(finalUp) else ""
        val cleanTotStr = if (finalTot > 0.0) formatCleanNumber(finalTot) else "0"

        if (cleanName.isNotBlank() || finalTot > 0.0 || finalUp > 0.0) {
            onAddItem(cleanName, cleanQty, cleanUpStr, cleanTotStr)
            itemName = ""
            quantityStr = "1"
            unitPriceStr = ""
            totalAmountStr = ""
        }
    }

    // Single Horizontal Ruler Row + Full Width Add Button underneath (Compact & Clean)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = NeuSurfaceRaised,
        shadowElevation = 2.dp,
        border = BorderStroke(0.8.dp, NeuAccentBlue.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Natural Arabic RTL Input Order: التفاصيل (Right) | العدد | سعر الواحدة | القيمة الإجمالية (Left)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. المربع الأول (من اليمين): التفاصيل (اسم الصنف / البيان)
                SmartRulerInputBox(
                    value = itemName,
                    onValueChange = { itemName = it },
                    placeholder = "التفاصيل (الصنف)",
                    modifier = Modifier.weight(1.5f),
                    isNumeric = false,
                    align = TextAlign.Start,
                    imeAction = ImeAction.Next
                )

                // 2. المربع الثاني: العدد (الكمية)
                SmartRulerInputBox(
                    value = quantityStr,
                    onValueChange = { input ->
                        val clean = input.toEnglishDigits().replace(",", "").replace("٬", "")
                        quantityStr = clean
                        val q = clean.toCleanDouble().let { if (it > 0.0) it else 1.0 }
                        val up = unitPriceStr.toCleanDouble()
                        if (up > 0.0) {
                            totalAmountStr = formatCleanNumber(up * q)
                        } else {
                            val tot = totalAmountStr.toCleanDouble()
                            if (tot > 0.0 && q > 0.0) {
                                unitPriceStr = formatCleanNumber(tot / q)
                            }
                        }
                    },
                    placeholder = "العدد",
                    modifier = Modifier.weight(0.55f),
                    isNumeric = true,
                    align = TextAlign.Center,
                    imeAction = ImeAction.Next
                )

                // 3. المربع الثالث: سعر الواحدة (القيمة)
                SmartRulerInputBox(
                    value = unitPriceStr,
                    onValueChange = { input ->
                        val clean = input.toEnglishDigits().replace(",", "").replace("٬", "")
                        unitPriceStr = clean
                        val up = clean.toCleanDouble()
                        val q = quantityStr.toCleanDouble().let { if (it > 0.0) it else 1.0 }
                        if (up > 0.0) {
                            totalAmountStr = formatCleanNumber(up * q)
                        } else if (clean.isEmpty()) {
                            totalAmountStr = ""
                        }
                    },
                    placeholder = "سعر الواحدة",
                    modifier = Modifier.weight(0.85f),
                    isNumeric = true,
                    align = TextAlign.Center,
                    imeAction = ImeAction.Next,
                    textColor = NeuAccentBlue
                )

                // 4. المربع الرابع (إلى اليسار): القيمة الإجمالية
                SmartRulerInputBox(
                    value = totalAmountStr,
                    onValueChange = { input ->
                        val clean = input.toEnglishDigits().replace(",", "").replace("٬", "")
                        totalAmountStr = clean
                        val tot = clean.toCleanDouble()
                        val q = quantityStr.toCleanDouble().let { if (it > 0.0) it else 1.0 }
                        if (tot > 0.0 && q > 0.0) {
                            unitPriceStr = formatCleanNumber(tot / q)
                        } else if (clean.isEmpty()) {
                            unitPriceStr = ""
                        }
                    },
                    placeholder = "الإجمالي",
                    modifier = Modifier.weight(0.95f),
                    isNumeric = true,
                    align = TextAlign.Center,
                    imeAction = ImeAction.Done,
                    onDone = { submitItem() },
                    textColor = Color(0xFF0F172A)
                )
            }

            // Full-width Add Button underneath the input boxes spanning the entire width
            NeuButton(
                onClick = { submitItem() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(31.dp),
                shape = RoundedCornerShape(7.dp),
                isPrimary = true
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة الصنف",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "إضافة الصنف إلى الفاتورة",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

@Composable
private fun SmartRulerInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = false,
    align: TextAlign = TextAlign.Start,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    textColor: Color = Color(0xFF0F172A)
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.height(31.dp),
        shape = RoundedCornerShape(7.dp),
        color = if (isFocused) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
        border = BorderStroke(
            width = if (isFocused) 1.2.dp else 0.8.dp,
            color = if (isFocused) NeuAccentBlue else Color(0xFFCBD5E1)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            contentAlignment = when (align) {
                TextAlign.Start -> Alignment.CenterStart
                TextAlign.End -> Alignment.CenterEnd
                else -> Alignment.Center
            }
        ) {
            AutoSelectBasicTextField(
                value = value,
                onValueChange = onValueChange,
                onFocusChange = { isFocused = it },
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) NeuAccentBlue else textColor,
                    textAlign = align
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isNumeric) KeyboardType.Decimal else KeyboardType.Text,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onDone() }
                ),
                singleLine = true,
                placeholder = placeholder,
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun VoiceInvoiceInputBar(
    viewModel: InvoiceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) ?: emptyList()
            if (spokenMatches.isNotEmpty()) {
                viewModel.processVoiceInputCandidates(spokenMatches)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                isListening = true
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ar-SA", "ar-YE", "ar-EG", "ar"))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "انطق: [القيمة] [الصنف] [الكمية] (مثال: 4000 كرتون عصير 2 أو 1000 تونة 2)")
                }
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                isListening = false
                Toast.makeText(context, "محرك البحث الصوتي غير متوفر على هذا الجهاز، يمكنك الكتابة في الشريط مباشرة", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "يرجى منح إذن المايكروفون لاستخدام الإدخال الصوتي", Toast.LENGTH_SHORT).show()
        }
    }

    fun startVoiceListening() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                isListening = true
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ar-SA", "ar-YE", "ar-EG", "ar"))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "انطق: [القيمة] [الصنف] [الكمية] (مثال: 4000 كرتون عصير 2 أو 1000 تونة 2)")
                }
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                isListening = false
                Toast.makeText(context, "محرك البحث الصوتي غير متوفر على هذا الجهاز، يمكنك الكتابة في الشريط مباشرة", Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    NeuCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        elevation = 2.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Main Top Voice Bar Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Voice / Mic Action Button
                NeuButton(
                    onClick = { startVoiceListening() },
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    isPrimary = !isListening
                ) {
                    Icon(
                        AppIcons.Mic,
                        contentDescription = "بدء التسجيل الصوتي",
                        tint = if (isListening) NeuError else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isListening) "استماع..." else "صوتي",
                        color = if (isListening) NeuError else Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Interactive / Editable Text Container with Wrapping
                NeuInsetBox(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = viewModel.voiceInputText,
                            onValueChange = { viewModel.updateVoiceInputText(it) },
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeuTextPrimary,
                                textAlign = TextAlign.Start
                            ),
                            cursorBrush = SolidColor(NeuAccentBlue),
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Text
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.submitVoiceBarText() }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (viewModel.voiceInputText.isBlank()) {
                            Text(
                                text = "انطق: [القيمة] [الصنف] [الكمية] (مثال: 4000 كرتون عصير 2)",
                                fontSize = 10.5.sp,
                                color = NeuTextMuted,
                                softWrap = true,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Manual submit / parse button if user typed or edited text
                if (viewModel.voiceInputText.isNotBlank()) {
                    NeuCircleButton(
                        onClick = { viewModel.submitVoiceBarText() },
                        size = 32.dp
                    ) {
                        Icon(
                            AppIcons.Add,
                            contentDescription = "تحليل وإضافة الفاتورة",
                            tint = NeuSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Actions: Undo and Clear
                if (viewModel.canUndoVoiceInput) {
                    NeuCircleButton(
                        onClick = { viewModel.undoLastVoiceInput() },
                        size = 30.dp
                    ) {
                        Icon(
                            AppIcons.Undo,
                            contentDescription = "تراجع عن الإدخال الصوتي",
                            tint = NeuError,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (viewModel.voiceInputText.isNotBlank() || viewModel.lastSpokenVoiceText.isNotBlank()) {
                    NeuCircleButton(
                        onClick = { viewModel.clearVoiceBar() },
                        size = 28.dp
                    ) {
                        Icon(
                            AppIcons.Clear,
                            contentDescription = "مسح شريط الصوت",
                            tint = NeuTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Parsed Badges Row (Shows recognized items preview chips)
            if (viewModel.lastParsedVoiceItems.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تم استخراجه وإدراجه:",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeuSuccess
                    )
                    viewModel.lastParsedVoiceItems.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = NeuSuccess.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, NeuSuccess.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "${item.description} (العدد: ${item.quantity} × ${if (item.totalAmount.isNotEmpty()) item.totalAmount else "-"})",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeuSuccess,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
