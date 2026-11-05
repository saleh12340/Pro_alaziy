package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.StoreConfigEntity
import com.example.printer.PrinterConnectionState
import com.example.ui.components.ReceiptBitmapHelper
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun PrinterSetupScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val printerState by viewModel.printerState.collectAsState()
    val storeConfigState by viewModel.storeConfig.collectAsState()
    val config = storeConfigState ?: StoreConfigEntity()

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    val currentPaperWidth = config.printerPaperWidth
    val selectedPaperWidthDots = config.printerWidthDots

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            pairedDevices = viewModel.bluetoothPrinterManager.getPairedDevices()
        } else {
            Toast.makeText(context, "تتطلب الطباعة منح صلاحية البلوتوث", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(missing.toTypedArray())
        } else {
            pairedDevices = viewModel.bluetoothPrinterManager.getPairedDevices()
        }
    }

    LaunchedEffect(Unit) {
        checkAndRequestPermissions()
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إعدادات طابعة الفواتير (بلوتوث)",
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
            // STATUS BANNER (Compact Neumorphic Card)
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                elevation = 3.dp,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeuIconBox(
                        size = 34.dp,
                        shape = RoundedCornerShape(8.dp),
                        backgroundColor = when (printerState) {
                            is PrinterConnectionState.Connected -> NeuSuccess.copy(alpha = 0.12f)
                            is PrinterConnectionState.Connecting -> NeuAccentBlue.copy(alpha = 0.12f)
                            else -> NeuInsetBg
                        }
                    ) {
                        Icon(
                            AppIcons.Bluetooth,
                            contentDescription = null,
                            tint = when (printerState) {
                                is PrinterConnectionState.Connected -> NeuSuccess
                                is PrinterConnectionState.Connecting -> NeuAccentBlue
                                else -> NeuTextMuted
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "حالة الاتصال بالطابعة:",
                            fontSize = 10.sp,
                            color = NeuTextSecondary
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = when (val state = printerState) {
                                is PrinterConnectionState.Connected -> "متصل بالطابعة: ${state.deviceName}"
                                is PrinterConnectionState.Connecting -> "جاري الاتصال بالطابعة..."
                                is PrinterConnectionState.Error -> state.message
                                else -> "غير متصل بالطابعة"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = when (printerState) {
                                is PrinterConnectionState.Connected -> NeuSuccess
                                is PrinterConnectionState.Error -> NeuError
                                else -> NeuTextPrimary
                            },
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    if (printerState is PrinterConnectionState.Connected) {
                        NeuButton(
                            onClick = { viewModel.bluetoothPrinterManager.disconnect() },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp),
                            isPrimary = false
                        ) {
                            Text("قطع", fontSize = 10.5.sp, color = NeuError, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // PAPER SIZE SELECTOR (Compact)
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                elevation = 2.dp,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "عرض الورق الحراري:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = NeuTextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NeuButton(
                            onClick = { viewModel.updatePrinterPaperWidth(58) },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp),
                            isPrimary = currentPaperWidth == 58
                        ) {
                            Text(
                                text = "58 مم",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentPaperWidth == 58) Color.White else NeuTextPrimary
                            )
                        }

                        NeuButton(
                            onClick = { viewModel.updatePrinterPaperWidth(80) },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp),
                            isPrimary = currentPaperWidth == 80
                        ) {
                            Text(
                                text = "80 مم",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentPaperWidth == 80) Color.White else NeuTextPrimary
                            )
                        }
                    }
                }
            }

            // TEST PRINT BUTTON (Compact)
            NeuButton(
                onClick = {
                    val testBitmap = ReceiptBitmapHelper.createReceiptBitmap(
                        context = context,
                        storeConfig = config,
                        invoiceNumber = 9999,
                        dateString = "2026/10/14",
                        customerName = "تجربة طباعة الفاتورة",
                        paymentType = "نقداً",
                        rows = listOf(
                            com.example.ui.viewmodels.ReceiptRow(
                                description = "أرز بسمتي فاخر",
                                quantityStr = "1",
                                totalAmountStr = "1500"
                            ),
                            com.example.ui.viewmodels.ReceiptRow(
                                description = "زيت طبخ 2 لتر",
                                quantityStr = "2",
                                totalAmountStr = "2400"
                            )
                        ),
                        receivedAmount = 3000.0,
                        widthPx = selectedPaperWidthDots
                    )
                    viewModel.printInvoiceAsBitmap(testBitmap, selectedPaperWidthDots)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                enabled = printerState is PrinterConnectionState.Connected,
                shape = RoundedCornerShape(8.dp),
                isPrimary = true
            ) {
                Icon(AppIcons.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("طباعة فاتورة تجريبية كصورة", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.5.sp)
            }

            // DEVICE LIST HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الأجهزة المقترنة بالهاتف (${pairedDevices.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = NeuTextPrimary
                )

                NeuCircleButton(
                    onClick = { pairedDevices = viewModel.bluetoothPrinterManager.getPairedDevices() },
                    size = 30.dp
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = NeuAccentBlue, modifier = Modifier.size(15.dp))
                }
            }

            if (pairedDevices.isEmpty()) {
                NeuCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text(
                        text = "لا توجد أجهزة بلوتوث مقترنة. يرجى التوجه لإعدادات الهاتف والاقتران بالطابعة أولاً.",
                        color = NeuTextSecondary,
                        fontSize = 11.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pairedDevices) { device ->
                        NeuCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        viewModel.bluetoothPrinterManager.connectToDevice(device)
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    NeuIconBox(size = 30.dp, shape = RoundedCornerShape(6.dp)) {
                                        Icon(AppIcons.Print, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(16.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.name ?: "طابعة غير معروفة",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = NeuTextPrimary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = device.address,
                                            fontSize = 10.sp,
                                            color = NeuTextMuted
                                        )
                                    }
                                }

                                NeuButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.bluetoothPrinterManager.connectToDevice(device)
                                        }
                                    },
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    isPrimary = false
                                ) {
                                    Text("اتصال", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NeuAccentBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
