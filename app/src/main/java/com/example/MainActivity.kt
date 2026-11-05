package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.data.local.LegacyDataImporter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InvoiceWithItems
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NavigationScreen(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    HOME("الرئيسية", AppIcons.Store, AppIcons.Store),
    NEW_INVOICE("فاتورة", AppIcons.Receipt, AppIcons.Receipt),
    CUSTOMERS("العملاء", Icons.Default.Person, Icons.Default.Person),
    REPORTS("التقارير", AppIcons.BarChart, AppIcons.BarChart),
    MORE("المزيد", AppIcons.MoreHoriz, AppIcons.MoreHoriz),
    HISTORY("السجل", AppIcons.History, AppIcons.History),
    SETTINGS("الإعدادات", Icons.Default.Settings, Icons.Default.Settings),
    PRODUCTS("الأصناف", AppIcons.Category, AppIcons.Category),
    PRINTER("الطابعة", AppIcons.Print, AppIcons.Print)
}


@Composable
private fun HomeDashboardScreen(
    viewModel: InvoiceViewModel,
    onNewInvoice: () -> Unit,
    onCustomers: () -> Unit,
    onHistory: () -> Unit,
    onProducts: () -> Unit,
    onPrinter: () -> Unit,
    onSettings: () -> Unit,
    onOpenCustomer: (String) -> Unit,
    onQuickEntry: () -> Unit
) {
    val invoices by viewModel.allInvoices.collectAsState()
    val config by viewModel.storeConfig.collectAsState()
    val receipts by viewModel.allReceipts.collectAsState()
    val money = remember { DecimalFormat("#,##0.##") }
    val currency = config?.currencySymbol ?: "ر.ي"
    val debitTypes = remember { listOf("عليه", "قيد دين") }
    val customerRows = remember(invoices, receipts) {
        val names = (invoices.map { it.invoice.customerName.trim() } + receipts.map { it.customerName.trim() })
            .filter { it.isNotBlank() && it != "عميل نقدي" }.distinct()
        names.map { name ->
            val customerInvoices = invoices.filter { it.invoice.customerName.trim() == name }
            val customerReceipts = receipts.filter { it.customerName.trim() == name }
            val debits = customerReceipts.filter { it.isDebit || debitTypes.any { type -> it.paymentType.contains(type) } }.sumOf { it.amount }
            val credits = customerReceipts.filterNot { it.isDebit || debitTypes.any { type -> it.paymentType.contains(type) } }.sumOf { it.amount }
            val balance = customerInvoices.sumOf { it.invoice.grandTotal } + debits - credits
            Triple(name, customerInvoices.size + customerReceipts.size, balance)
        }.sortedByDescending { it.third }
    }
    val totalExports = remember(invoices) { invoices.sumOf { it.invoice.grandTotal } }
    val totalImports = remember(invoices, receipts) {
        invoices.sumOf { it.invoice.paidAmount } +
            receipts.filterNot { it.isDebit || debitTypes.any { type -> it.paymentType.contains(type) } }.sumOf { it.amount }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF173B68))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(AppIcons.Store, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            Text(
                (config?.storeNameArabic ?: "بقالة العزي").ifBlank { "بقالة العزي" },
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("حسابات العملاء", color = Color(0xFFDBEAFE), fontSize = 9.sp, maxLines = 1)
            TextButton(onClick = onCustomers, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                modifier = Modifier.height(25.dp)) {
                Text("الكل", color = Color.White, fontSize = 9.sp)
            }
        }

        if (customerRows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                Text("لا توجد حسابات عملاء بعد", color = NeuTextSecondary, fontSize = 11.sp)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(customerRows.size) { index ->
                    val (name, count, balance) = customerRows[index]
                    val owing = balance > 0.05
                    val balanced = kotlin.math.abs(balance) < 0.05
                    val tint = when { balanced -> Color(0xFF475569); owing -> Color(0xFFB91C1C); else -> Color(0xFF047857) }
                    val rowBg = when { balanced -> Color(0xFFF1F5F9); owing -> Color(0xFFFFF1F2); else -> Color(0xFFECFDF5) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(rowBg)
                            .border(1.dp, when { balanced -> Color(0xFFCBD5E1); owing -> Color(0xFFFCA5A5); else -> Color(0xFF86EFAC) }, RoundedCornerShape(5.dp))
                            .clickable { onOpenCustomer(name) }
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(name, modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = NeuTextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("$count حركة", fontSize = 8.sp, color = NeuTextSecondary, maxLines = 1)
                        Text(
                            if (balanced) "خالص" else "${money.format(kotlin.math.abs(balance))} ${if (owing) "عليه" else "له"}",
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = tint, maxLines = 1
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFE8EEF5))
                .border(width = 1.dp, color = Color(0xFFCBD5E1))
                .padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(5.dp))
                .background(Color(0xFFFFF1F2)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text("إجمالي الصادرات", fontSize = 8.sp, color = Color(0xFF9F1239), maxLines = 1)
                Text("${money.format(totalExports)} $currency", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF9F1239), maxLines = 1)
            }
            Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(5.dp))
                .background(Color(0xFFECFDF5)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text("إجمالي الواردات", fontSize = 8.sp, color = Color(0xFF047857), maxLines = 1)
                Text("${money.format(totalImports)} $currency", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF047857), maxLines = 1)
            }
        }
    }
}


@Composable
private fun QuickCustomerEntryScreen(
    viewModel: InvoiceViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (String) -> Unit
) {
    val invoices by viewModel.allInvoices.collectAsState()
    val receipts by viewModel.allReceipts.collectAsState()
    val names = remember(invoices, receipts) {
        (invoices.map { it.invoice.customerName.trim() } + receipts.map { it.customerName.trim() })
            .filter { it.isNotBlank() && it != "عميل نقدي" }.distinct().sorted()
    }
    var customer by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var suggestionsVisible by remember { mutableStateOf(false) }
    var pendingDebit by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf("") }
    val matches = remember(names, customer) {
        if (customer.isBlank()) emptyList() else names.filter { it.contains(customer.trim(), ignoreCase = true) }.take(6)
    }
    val existing = names.any { it.equals(customer.trim(), ignoreCase = true) }
    val config by viewModel.storeConfig.collectAsState()
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF173B68)).padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("إدخال عملية سريعة", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onBack, modifier = Modifier.size(27.dp)) { Icon(Icons.Default.Close, "إغلاق", tint = Color.White) }
        }
        OutlinedTextField(
            value = customer, onValueChange = { customer = it; suggestionsVisible = true; error = "" },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم العميل") },
            placeholder = { Text("اكتب اسم العميل") }, shape = RoundedCornerShape(8.dp)
        )
        if (suggestionsVisible && matches.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Color.White).border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(7.dp))) {
                matches.forEach { match ->
                    Text(match, Modifier.fillMaxWidth().clickable { customer = match; suggestionsVisible = false; error = "" }.padding(9.dp), fontSize = 12.sp, color = NeuTextPrimary)
                }
            }
        }
        if (existing) {
            TextButton(onClick = { onOpenCustomer(names.first { it.equals(customer.trim(), ignoreCase = true) }) }, contentPadding = PaddingValues(4.dp)) {
                Text("عرض عمليات هذا العميل كاملة", fontSize = 10.sp)
            }
        }
        OutlinedTextField(
            value = amount, onValueChange = { value -> amount = value.filter { it.isDigit() || it == '.' || it == ',' }; error = "" },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("المبلغ") }, placeholder = { Text("0") }, shape = RoundedCornerShape(8.dp)
        )
        OutlinedTextField(
            value = details, onValueChange = { details = it; error = "" },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), label = { Text("تفاصيل العملية") },
            placeholder = { Text("اكتب تفاصيل العملية") }, minLines = 3, maxLines = 5, shape = RoundedCornerShape(8.dp)
        )
        if (error.isNotBlank()) Text(error, color = Color(0xFFB91C1C), fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val value = amount.replace(",", "").toDoubleOrNull()
                error = when {
                    customer.trim().isBlank() -> "أدخل اسم العميل أولاً"
                    value == null || value <= 0.0 -> "أدخل مبلغاً صحيحاً أكبر من صفر"
                    details.trim().isBlank() -> "أدخل تفاصيل العملية"
                    else -> ""
                }
                if (error.isBlank()) pendingDebit = true
            }, modifier = Modifier.weight(1f).height(43.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))) {
                Text("عليه", fontWeight = FontWeight.Bold)
            }
            Button(onClick = {
                val value = amount.replace(",", "").toDoubleOrNull()
                error = when {
                    customer.trim().isBlank() -> "أدخل اسم العميل أولاً"
                    value == null || value <= 0.0 -> "أدخل مبلغاً صحيحاً أكبر من صفر"
                    details.trim().isBlank() -> "أدخل تفاصيل العملية"
                    else -> ""
                }
                if (error.isBlank()) pendingDebit = false
            }, modifier = Modifier.weight(1f).height(43.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))) {
                Text("له", fontWeight = FontWeight.Bold)
            }
        }
        Text("عليه: يضيف المبلغ إلى المديونية  •  له: يخصم المبلغ من المديونية", color = NeuTextSecondary, fontSize = 9.sp)
    }
    pendingDebit?.let { debit ->
        val value = amount.replace(",", "").toDoubleOrNull() ?: 0.0
        AlertDialog(
            onDismissRequest = { pendingDebit = null },
            title = { Text(if (existing) "تأكيد تسجيل العملية" else "العميل غير موجود") },
            text = { Text(if (existing) "سيتم تسجيل مبلغ " + DecimalFormat("#,##0.##").format(value) + " " + (config?.currencySymbol ?: "ر.ي") + (if (debit) " على العميل " else " للعميل ") + customer.trim() + ". هل تريد المتابعة؟" else "العميل «" + customer.trim() + "» غير موجود. هل تريد إضافته إلى قائمة العملاء وتسجيل العملية؟") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addCustomerReceipt(
                        customerName = customer.trim(), amount = value,
                        paymentType = if (debit) "قيد دين (عليه)" else "سند قبض/تسديد (له)",
                        notes = details.trim(), isDebit = debit
                    )
                    pendingDebit = null
                    onBack()
                }) { Text(if (existing) "تأكيد التسجيل" else "إضافة العميل والتسجيل") }
            },
            dismissButton = { TextButton(onClick = { pendingDebit = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun DashboardMetric(label: String, amount: String, currency: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)).padding(10.dp)
    ) {
        Text(label, color = Color(0xFFDBEAFE), fontSize = 12.sp)
        Text(amount, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(currency, color = Color(0xFFBFDBFE), fontSize = 11.sp)
    }
}

@Composable
private fun DashboardSmallMetric(label: String, value: String, modifier: Modifier = Modifier) {
    NeuCard(modifier = modifier, backgroundColor = NeuSurface, elevation = 1.dp, contentPadding = PaddingValues(10.dp)) {
        Text(value, color = NeuAccentBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(label, color = NeuTextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun DashboardAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 86.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = NeuSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = NeuTextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, color = NeuTextSecondary, fontSize = 10.sp, maxLines = 2)
            }
        }
    }
}


class InvoiceCenterActivity : ComponentActivity() {

    private val viewModel: InvoiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch(Dispatchers.IO) { LegacyDataImporter.importIfNeeded(applicationContext) }

        setContent {
            MyApplicationTheme {
                // Ensure native Arabic RTL layout
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavigationScreen.HOME) }
    var customerToOpen by remember { mutableStateOf<String?>(null) }
    var showQuickEntry by remember { mutableStateOf(false) }
    val bottomScreens = listOf(
        NavigationScreen.HOME,
        NavigationScreen.NEW_INVOICE,
        NavigationScreen.CUSTOMERS,
        NavigationScreen.REPORTS,
        NavigationScreen.MORE
    )
    val uiMessage by viewModel.uiEventMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }

    // Intercept hardware/system back button
    BackHandler {
        when (currentTab) {
            NavigationScreen.HISTORY, NavigationScreen.PRODUCTS, NavigationScreen.PRINTER, NavigationScreen.SETTINGS -> {
                currentTab = NavigationScreen.MORE
            }
            NavigationScreen.MORE, NavigationScreen.REPORTS, NavigationScreen.CUSTOMERS, NavigationScreen.NEW_INVOICE -> {
                currentTab = NavigationScreen.HOME
            }
            NavigationScreen.HOME -> {
                if (showQuickEntry) showQuickEntry = false else showExitDialog = true
            }
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUiMessage()
        }
    }

    Scaffold(
        containerColor = NeuBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (currentTab == NavigationScreen.HOME && !showQuickEntry) {
                FloatingActionButton(
                    onClick = { showQuickEntry = true },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                    modifier = Modifier.size(52.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "إدخال عملية سريعة", modifier = Modifier.size(26.dp)) }
            }
        },
        bottomBar = {
            // Floating Clean Navigation Bar (5 Primary Tabs)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color(0x14000000),
                        spotColor = Color(0x1E000000)
                    )
                    .clip(RoundedCornerShape(22.dp)),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomScreens.forEach { screen ->
                        val isSelected = currentTab == screen || (screen == NavigationScreen.MORE && currentTab in listOf(NavigationScreen.HISTORY, NavigationScreen.PRODUCTS, NavigationScreen.PRINTER, NavigationScreen.SETTINGS))
                        val itemModifier = if (isSelected) {
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFEFF6FF))
                                .border(
                                    1.dp,
                                    Color(0xFFBFDBFE),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { currentTab = screen }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        }

                        Column(
                            modifier = itemModifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label,
                                tint = if (isSelected) NeuAccentBlue else NeuTextSecondary,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = screen.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) NeuAccentBlue else NeuTextSecondary
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NeuBackground)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavigationScreen.HOME -> if (showQuickEntry) {
                    QuickCustomerEntryScreen(
                        viewModel = viewModel,
                        onBack = { showQuickEntry = false },
                        onOpenCustomer = { name ->
                            customerToOpen = name
                            showQuickEntry = false
                            currentTab = NavigationScreen.CUSTOMERS
                        }
                    )
                } else HomeDashboardScreen(
                    viewModel = viewModel,
                    onNewInvoice = { currentTab = NavigationScreen.NEW_INVOICE },
                    onCustomers = { currentTab = NavigationScreen.CUSTOMERS },
                    onHistory = { currentTab = NavigationScreen.HISTORY },
                    onProducts = { currentTab = NavigationScreen.PRODUCTS },
                    onPrinter = { currentTab = NavigationScreen.PRINTER },
                    onSettings = { currentTab = NavigationScreen.SETTINGS },
                    onOpenCustomer = { name ->
                        customerToOpen = name
                        currentTab = NavigationScreen.CUSTOMERS
                    },
                    onQuickEntry = { showQuickEntry = true }
                )
                NavigationScreen.NEW_INVOICE -> NewInvoiceScreen(
                    viewModel = viewModel,
                    onNavigateToPrinterSetup = { currentTab = NavigationScreen.PRINTER }
                )
                NavigationScreen.CUSTOMERS -> CustomersScreen(
                    viewModel = viewModel,
                    initialCustomerName = customerToOpen,
                    onEditInvoice = { inv ->
                        viewModel.loadInvoiceForEditing(inv)
                        currentTab = NavigationScreen.NEW_INVOICE
                    },
                    onNewInvoiceForCustomer = { customerName ->
                        viewModel.resetForm()
                        viewModel.customerName = customerName
                        viewModel.paymentType = "أجل"
                        currentTab = NavigationScreen.NEW_INVOICE
                    }
                )
                NavigationScreen.REPORTS -> FinancialReportsScreen(viewModel = viewModel)
                NavigationScreen.MORE -> MoreHubScreen(
                    viewModel = viewModel,
                    onNavigateToHistory = { currentTab = NavigationScreen.HISTORY },
                    onNavigateToProducts = { currentTab = NavigationScreen.PRODUCTS },
                    onNavigateToPrinter = { currentTab = NavigationScreen.PRINTER },
                    onNavigateToSettings = { currentTab = NavigationScreen.SETTINGS }
                )
                NavigationScreen.HISTORY -> InvoiceHistoryScreen(
                    viewModel = viewModel,
                    onEditInvoice = { inv ->
                        viewModel.loadInvoiceForEditing(inv)
                        currentTab = NavigationScreen.NEW_INVOICE
                    },
                    onNavigateToPrinterSetup = { currentTab = NavigationScreen.PRINTER }
                )
                NavigationScreen.PRODUCTS -> ProductsCatalogScreen(viewModel = viewModel)
                NavigationScreen.PRINTER -> PrinterSetupScreen(viewModel = viewModel)
                NavigationScreen.SETTINGS -> StoreSettingsScreen(viewModel = viewModel)
            }
        }
    }

    // Exit Confirmation Dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("تأكيد الخروج من التطبيق", fontWeight = FontWeight.Bold, color = NeuTextPrimary) },
            text = { Text("هل أنت متأكد من رغبتك في إغلاق التطبيق؟ سيتم حفظ جميع بياناتك بأمان.", color = NeuTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finishAffinity()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuError)
                ) {
                    Text("نعم، خروج", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false }) {
                    Text("إلغاء", color = NeuTextPrimary)
                }
            },
            containerColor = NeuSurface
        )
    }
}
