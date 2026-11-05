package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
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
import com.example.data.backup.BackupRestoreResult
import com.example.data.local.StoreConfigEntity
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreSettingsScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    val configState by viewModel.storeConfig.collectAsState()
    val config = configState ?: StoreConfigEntity()

    var storeNameArabic by remember(config) { mutableStateOf(config.storeNameArabic) }
    var storeNameEnglish by remember(config) { mutableStateOf(config.storeNameEnglish) }
    var storeSubtitle by remember(config) { mutableStateOf(config.storeSubtitle) }
    var logoText by remember(config) { mutableStateOf(config.logoText) }
    var address by remember(config) { mutableStateOf(config.address) }
    var phone1 by remember(config) { mutableStateOf(config.phone1) }
    var phone2 by remember(config) { mutableStateOf(config.phone2) }
    var currencySymbol by remember(config) { mutableStateOf(config.currencySymbol) }
    var nextInvoiceNumberStr by remember(config) { mutableStateOf(config.nextInvoiceNumber.toString()) }
    var defaultDisclaimerNote by remember(config) { mutableStateOf(config.defaultDisclaimerNote) }

    // Dialog & file picker states for Backup / Restore
    var backupJsonToExport by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showSafetyRestoreConfirm by remember { mutableStateOf(false) }
    var restoreJsonData by remember { mutableStateOf<String?>(null) }
    var isOperatingBackup by remember { mutableStateOf(false) }

    // File saver launcher for JSON backup
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null && backupJsonToExport != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(backupJsonToExport!!.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "تم حفظ النسخة الاحتياطية بنجاح في ملف", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ أثناء حفظ الملف: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // File picker launcher for Restore
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
                }
                if (!jsonString.isNullOrBlank()) {
                    restoreJsonData = jsonString
                    showRestoreConfirmDialog = true
                } else {
                    Toast.makeText(context, "الملف المحدد فارغ!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "فشل في قراءة الملف: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إعدادات المتجر وترويسة الفاتورة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NeuTextPrimary
                    )
                }
            }
        },
        floatingActionButton = {
            NeuButton(
                onClick = {
                    val updated = StoreConfigEntity(
                        id = 1,
                        storeNameArabic = storeNameArabic,
                        storeNameEnglish = storeNameEnglish,
                        storeSubtitle = storeSubtitle,
                        logoText = logoText,
                        address = address,
                        phone1 = phone1,
                        phone2 = phone2,
                        currencySymbol = currencySymbol,
                        nextInvoiceNumber = nextInvoiceNumberStr.toIntOrNull() ?: 1001,
                        defaultDisclaimerNote = defaultDisclaimerNote
                    )
                    viewModel.updateStoreConfig(updated)
                },
                shape = RoundedCornerShape(20.dp),
                isPrimary = true
            ) {
                Icon(AppIcons.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("حفظ الإعدادات", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. BACKUP & RESTORE CARD (النسخ الاحتياطي والاستعادة)
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                elevation = 2.dp,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIconBox(size = 30.dp, shape = RoundedCornerShape(7.dp)) {
                        Icon(AppIcons.Backup, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "النسخ الاحتياطي واستعادة البيانات",
                            fontWeight = FontWeight.Bold,
                            color = NeuTextPrimary,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "حفظ ومزامنة الفواتير وقائمة الأصناف",
                            fontSize = 10.5.sp,
                            color = NeuTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Share / Export JSON
                    NeuButton(
                        onClick = {
                            isOperatingBackup = true
                            viewModel.createBackupJson { json ->
                                isOperatingBackup = false
                                backupJsonToExport = json

                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية - بقالة العزي")
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "مشاركة النسخة الاحتياطية (واتساب / درايف / ملف)")
                                context.startActivity(shareIntent)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        enabled = !isOperatingBackup,
                        shape = RoundedCornerShape(7.dp),
                        isPrimary = true
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة نسخة", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Save as file in storage
                    NeuButton(
                        onClick = {
                            isOperatingBackup = true
                            viewModel.createBackupJson { json ->
                                isOperatingBackup = false
                                backupJsonToExport = json
                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportFileLauncher.launch("Fatooraty_Backup_$dateStr.json")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        enabled = !isOperatingBackup,
                        shape = RoundedCornerShape(7.dp),
                        isPrimary = false
                    ) {
                        Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeuTextPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ كملف", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                NeuButton(
                    onClick = { showSafetyRestoreConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    shape = RoundedCornerShape(7.dp),
                    isPrimary = false
                ) {
                    Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeuAccentBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استرجاع نسخة الأمان السابقة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuAccentBlue)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Restore button
                NeuButton(
                    onClick = {
                        importFileLauncher.launch("application/json")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    shape = RoundedCornerShape(7.dp),
                    isPrimary = false
                ) {
                    Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeuAccentBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استعادة نسخة احتياطية من ملف (JSON)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuAccentBlue)
                }
            }

            // 2. STORE NAME & LOGO
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                elevation = 2.dp,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIconBox(size = 26.dp, shape = RoundedCornerShape(6.dp)) {
                        Icon(AppIcons.Store, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "اسم المحل والتاجر",
                        fontWeight = FontWeight.Bold,
                        color = NeuAccentBlue,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text("اسم المحل / التاجر بالعربي:", fontSize = 11.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    BasicTextField(
                        value = storeNameArabic,
                        onValueChange = { storeNameArabic = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(NeuAccentBlue),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (storeNameArabic.isEmpty()) {
                                Text("مثال: بقالة العزي", color = NeuTextMuted, fontSize = 12.sp)
                            }
                            inner()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text("اسم المحل بالإنجليزي (اختياري):", fontSize = 11.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    BasicTextField(
                        value = storeNameEnglish,
                        onValueChange = { storeNameEnglish = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary),
                        cursorBrush = SolidColor(NeuAccentBlue),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (storeNameEnglish.isEmpty()) {
                                Text("مثال: Al-Ezzi Grocery", color = NeuTextMuted, fontSize = 12.sp)
                            }
                            inner()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text("النشاط / الوصف الإضافي:", fontSize = 11.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    BasicTextField(
                        value = storeSubtitle,
                        onValueChange = { storeSubtitle = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary),
                        cursorBrush = SolidColor(NeuAccentBlue),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (storeSubtitle.isEmpty()) {
                                Text("مثال: مواد غذائية واستهلاكية", color = NeuTextMuted, fontSize = 12.sp)
                            }
                            inner()
                        }
                    )
                }
            }

            // 3. CONTACT & ADDRESS
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                elevation = 2.dp,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIconBox(size = 26.dp, shape = RoundedCornerShape(6.dp)) {
                        Icon(AppIcons.Store, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "بيانات الاتصال والعنوان",
                        fontWeight = FontWeight.Bold,
                        color = NeuAccentBlue,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text("رقم الهاتف / الجوال الأساسي:", fontSize = 11.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    BasicTextField(
                        value = phone1,
                        onValueChange = { phone1 = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(NeuAccentBlue),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (phone1.isEmpty()) {
                                Text("مثال: 772437314", color = NeuTextMuted, fontSize = 12.sp)
                            }
                            inner()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text("رقم هاتف إضافي (اختياري):", fontSize = 11.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    BasicTextField(
                        value = phone2,
                        onValueChange = { phone2 = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary),
                        cursorBrush = SolidColor(NeuAccentBlue),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (phone2.isEmpty()) {
                                Text("مثال: 770000000", color = NeuTextMuted, fontSize = 12.sp)
                            }
                            inner()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text("عنوان المحل:", fontSize = 11.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary),
                        cursorBrush = SolidColor(NeuAccentBlue),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (address.isEmpty()) {
                                Text("صنعاء - شارع ...", color = NeuTextMuted, fontSize = 12.sp)
                            }
                            inner()
                        }
                    )
                }
            }

            // 4. INVOICE OPTIONS & CURRENCY
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                elevation = 2.dp,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIconBox(size = 26.dp, shape = RoundedCornerShape(6.dp)) {
                        Icon(AppIcons.Receipt, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "خيارات الفاتورة والعملة",
                        fontWeight = FontWeight.Bold,
                        color = NeuAccentBlue,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("رمز العملة:", fontSize = 11.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                            BasicTextField(
                                value = currencySymbol,
                                onValueChange = { currencySymbol = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary, fontWeight = FontWeight.Bold),
                                cursorBrush = SolidColor(NeuAccentBlue),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (currencySymbol.isEmpty()) {
                                        Text("ر.ي", color = NeuTextMuted, fontSize = 12.sp)
                                    }
                                    inner()
                                }
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("رقم التسلسل التالي:", fontSize = 11.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                            BasicTextField(
                                value = nextInvoiceNumberStr,
                                onValueChange = { nextInvoiceNumberStr = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary, fontWeight = FontWeight.Bold),
                                cursorBrush = SolidColor(NeuAccentBlue),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (nextInvoiceNumberStr.isEmpty()) {
                                        Text("1001", color = NeuTextMuted, fontSize = 12.sp)
                                    }
                                    inner()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text("ملاحظة وتعهد أسفل الفاتورة:", fontSize = 11.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    BasicTextField(
                        value = defaultDisclaimerNote,
                        onValueChange = { defaultDisclaimerNote = it },
                        textStyle = TextStyle(fontSize = 12.sp, color = NeuTextPrimary),
                        cursorBrush = SolidColor(NeuAccentBlue),
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(70.dp))
        }
    }

    if (showSafetyRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showSafetyRestoreConfirm = false },
            containerColor = NeuSurface,
            title = { Text("استرجاع نسخة الأمان", fontWeight = FontWeight.Bold, color = NeuTextPrimary) },
            text = {
                Text(
                    "سيتم استبدال البيانات الحالية بآخر نسخة أمان محفوظة قبل عملية استعادة سابقة. وسيُحفظ ملف أمان جديد من البيانات الحالية قبل الاستبدال. هل تريد المتابعة؟",
                    color = NeuTextSecondary
                )
            },
            confirmButton = {
                NeuButton(
                    onClick = {
                        showSafetyRestoreConfirm = false
                        isOperatingBackup = true
                        viewModel.restoreLatestSafetyBackup { result ->
                            isOperatingBackup = false
                            when (result) {
                                is BackupRestoreResult.Success -> Toast.makeText(
                                    context,
                                    "تم استرجاع نسخة الأمان: ${result.invoicesCount} فاتورة و ${result.productsCount} صنف",
                                    Toast.LENGTH_LONG
                                ).show()
                                is BackupRestoreResult.Error -> Toast.makeText(
                                    context,
                                    "تعذر الاسترجاع: ${result.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    isPrimary = true
                ) { Text("استرجاع الآن", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                NeuButton(onClick = { showSafetyRestoreConfirm = false }, isPrimary = false) {
                    Text("إلغاء", color = NeuTextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Restore Confirmation Dialog
    if (showRestoreConfirmDialog && restoreJsonData != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                restoreJsonData = null
            },
            containerColor = NeuSurface,
            title = { Text("تأكيد استعادة النسخة الاحتياطية", fontWeight = FontWeight.Bold, color = NeuTextPrimary) },
            text = {
                Text(
                    "تحذير: ستُستبدل بيانات التطبيق الحالية بالأصناف والفواتير وإعدادات المحل الموجودة في الملف. سيُحفظ أولاً ملف أمان قابل للاستعادة من البيانات الحالية داخل مساحة التطبيق. أرقام الفواتير وأسماء الأصناف المتكررة في الملف ستبقى كما هي.\n\nهل تريد المتابعة؟",
                    color = NeuTextSecondary
                )
            },
            confirmButton = {
                NeuButton(
                    onClick = {
                        val json = restoreJsonData!!
                        showRestoreConfirmDialog = false
                        restoreJsonData = null
                        viewModel.restoreFromBackupJson(json) { result ->
                            when (result) {
                                is BackupRestoreResult.Success -> {
                                    Toast.makeText(context, "تم الاستبدال: ${result.invoicesCount} فاتورة و ${result.productsCount} صنف. نسخة الأمان: ${result.safetyBackupFileName}", Toast.LENGTH_LONG).show()
                                }
                                is BackupRestoreResult.Error -> {
                                    Toast.makeText(context, "خطأ: ${result.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    isPrimary = true
                ) {
                    Text("نعم، استعادة الآن", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                NeuButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        restoreJsonData = null
                    },
                    isPrimary = false
                ) {
                    Text("إلغاء", color = NeuTextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
