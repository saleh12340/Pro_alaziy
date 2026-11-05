package com.example.ui.viewmodels

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.*
import com.example.data.local.*
import com.example.data.repository.InvoiceRepository
import com.example.printer.BluetoothPrinterManager
import com.example.printer.PrinterConnectionState
import com.example.printer.ThermalImageConverter
import com.example.ui.components.InvoicePdfHelper
import com.example.ui.components.ReceiptBitmapHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.ui.util.toEnglishDigits
import com.example.ui.util.VoiceInvoiceParser
import com.example.ui.util.VoiceCommand
import com.example.ui.util.ParsedVoiceItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import com.example.ui.util.toCleanDouble
import com.example.ui.util.formatCleanNumber

data class ReceiptRow(
    val id: String = UUID.randomUUID().toString(),
    var description: String = "",
    var quantityStr: String = "1",
    var unitPriceStr: String = "",
    var totalAmountStr: String = ""
) {
    val quantity: Double
        get() = quantityStr.toCleanDouble().let { if (it > 0.0) it else 1.0 }

    val total: Double
        get() {
            val tot = totalAmountStr.toCleanDouble()
            if (tot > 0.0) return tot
            val up = unitPriceStr.toCleanDouble()
            val q = quantity
            return if (up > 0.0) up * q else 0.0
        }

    val unitPrice: Double
        get() {
            val up = unitPriceStr.toCleanDouble()
            if (up > 0.0) return up
            val tot = totalAmountStr.toCleanDouble()
            val q = quantity
            return if (tot > 0.0 && q > 0.0) tot / q else 0.0
        }

    val hasData: Boolean
        get() = description.isNotBlank() || totalAmountStr.isNotBlank() || unitPriceStr.isNotBlank()

    val isCompletelyEmpty: Boolean
        get() = !hasData
}

class InvoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = InvoiceRepository(db)
    val bluetoothPrinterManager = BluetoothPrinterManager(application)
    val backupManager = BackupManager(application, repository)

    val storeConfig = repository.storeConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StoreConfigEntity()
    )

    val allInvoices = repository.allInvoices.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allProducts = repository.allProducts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allReceipts = db.customerReceiptDao().getAllReceipts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val printerState: StateFlow<PrinterConnectionState> = bluetoothPrinterManager.connectionState

    // Form State for Current Invoice (English digits for numbers and dates)
    var invoiceNumber by mutableStateOf(1001)
    var dateString by mutableStateOf(SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date()))
    var customerName by mutableStateOf("")
    var paymentType by mutableStateOf("نقداً") // "نقداً" or "أجل"
    var notes by mutableStateOf("")
    var receivedAmountStr by mutableStateOf("")

    fun getCustomerPhone(customerName: String): String {
        val clean = customerName.trim()
        if (clean.isBlank()) return ""
        val prefs = getApplication<Application>().getSharedPreferences("customer_meta", Context.MODE_PRIVATE)
        val saved = prefs.getString("phone_$clean", "") ?: ""
        if (saved.isNotBlank()) return saved
        // Try extracting phone if embedded in customer name e.g. "أحمد - 772437314"
        val regex = "(\\+?[0-9]{7,14})".toRegex()
        val match = regex.find(clean)
        return match?.value ?: ""
    }

    fun saveCustomerPhone(customerName: String, phone: String) {
        val clean = customerName.trim()
        if (clean.isBlank()) return
        val prefs = getApplication<Application>().getSharedPreferences("customer_meta", Context.MODE_PRIVATE)
        prefs.edit().putString("phone_$clean", phone.trim()).apply()
    }

    // Single-column expanding rows: Starts with DEFAULT_ROW_COUNT
    val receiptRows = mutableStateListOf<ReceiptRow>()

    // Voice Input State
    var voiceInputText by mutableStateOf("")
    var lastSpokenVoiceText by mutableStateOf("")
        private set
    var lastParsedVoiceItems by mutableStateOf<List<ParsedVoiceItem>>(emptyList())
        private set
    private var previousReceiptRowsSnapshot: List<ReceiptRow>? = null
    var canUndoVoiceInput by mutableStateOf(false)
        private set

    fun updateVoiceInputText(text: String) {
        voiceInputText = text
    }

    fun submitVoiceBarText(): Int {
        val clean = voiceInputText.trim()
        if (clean.isBlank()) return 0
        return processVoiceInput(clean)
    }

    fun processVoiceInputCandidates(candidates: List<String>): Int {
        for (candidate in candidates) {
            val clean = candidate.trim()
            if (clean.isBlank()) continue
            val command = VoiceInvoiceParser.parseVoiceCommand(clean)
            when (command) {
                is VoiceCommand.ClearInvoice -> {
                    previousReceiptRowsSnapshot = receiptRows.map { it.copy() }
                    canUndoVoiceInput = true
                    receiptRows.clear()
                    repeat(DEFAULT_ROW_COUNT) { receiptRows.add(ReceiptRow()) }
                    voiceInputText = clean
                    lastSpokenVoiceText = clean
                    _uiEventMessage.value = "تم مسح الفاتورة بالصوت بنجاح"
                    return 1
                }
                is VoiceCommand.DeleteItem -> {
                    previousReceiptRowsSnapshot = receiptRows.map { it.copy() }
                    canUndoVoiceInput = true
                    voiceInputText = clean
                    lastSpokenVoiceText = clean

                    var removedName = ""
                    var removedCount = 0

                    if (command.index != null) {
                        if (command.index == -1) {
                            // Delete last item that has data
                            val lastIdx = receiptRows.indexOfLast { it.hasData }
                            if (lastIdx >= 0) {
                                removedName = receiptRows[lastIdx].description
                                receiptRows.removeAt(lastIdx)
                                removedCount++
                            }
                        } else if (command.index in receiptRows.indices) {
                            removedName = receiptRows[command.index].description
                            receiptRows.removeAt(command.index)
                            removedCount++
                        }
                    } else if (command.query.isNotBlank()) {
                        val q = command.query.trim().lowercase()
                        val qNoAl = q.replace("ال", "")
                        val idx = receiptRows.indexOfFirst { row ->
                            row.hasData && (
                                row.description.lowercase().contains(q) ||
                                q.contains(row.description.lowercase()) ||
                                (qNoAl.isNotBlank() && row.description.lowercase().replace("ال", "").contains(qNoAl))
                            )
                        }
                        if (idx >= 0) {
                            removedName = receiptRows[idx].description
                            receiptRows.removeAt(idx)
                            removedCount++
                        }
                    } else {
                        val lastIdx = receiptRows.indexOfLast { it.hasData }
                        if (lastIdx >= 0) {
                            removedName = receiptRows[lastIdx].description
                            receiptRows.removeAt(lastIdx)
                            removedCount++
                        }
                    }

                    if (receiptRows.isEmpty()) {
                        receiptRows.add(ReceiptRow())
                    }

                    if (removedCount > 0) {
                        val label = if (removedName.isNotBlank()) " '$removedName'" else ""
                        _uiEventMessage.value = "تم حذف الصنف$label بالصوت بنجاح"
                        return 1
                    } else {
                        _uiEventMessage.value = "لم يتم العثور على الصنف المطلوب حذفه: '${command.query}'"
                        return 0
                    }
                }
                is VoiceCommand.AddCustomer -> {
                    customerName = command.customerName
                    voiceInputText = clean
                    lastSpokenVoiceText = clean
                    _uiEventMessage.value = "تم تحديد العميل: '${command.customerName}' بالصوت"
                    return 1
                }
                is VoiceCommand.AddProduct -> {
                    viewModelScope.launch {
                        repository.saveProduct(
                            ProductCatalogEntity(
                                name = command.productName,
                                defaultUnitPrice = command.price,
                                category = "عام"
                            )
                        )
                    }
                    voiceInputText = clean
                    lastSpokenVoiceText = clean
                    val priceLabel = if (command.price > 0) " بسعر ${command.price}" else ""
                    _uiEventMessage.value = "تمت إضافة الصنف '${command.productName}'$priceLabel إلى قائمة الأصناف!"
                    return 1
                }
                is VoiceCommand.Add -> {
                    return applyParsedVoiceItems(clean, command.items)
                }
                is VoiceCommand.Unknown -> {
                    // try next candidate
                }
            }
        }
        val first = candidates.firstOrNull()?.trim() ?: ""
        if (first.isNotBlank()) {
            voiceInputText = first
            lastSpokenVoiceText = first
        }
        _uiEventMessage.value = "لم يتم التعرف على الأمر الصوتي، يرجى التحدث بوضوح (مثال: 4000 كرتون عصير 2 أو احذف عصير)"
        return 0
    }

    fun processVoiceInput(spokenText: String): Int {
        return processVoiceInputCandidates(listOf(spokenText))
    }

    private fun applyParsedVoiceItems(spokenText: String, parsed: List<ParsedVoiceItem>): Int {
        voiceInputText = spokenText
        lastSpokenVoiceText = spokenText
        previousReceiptRowsSnapshot = receiptRows.map { it.copy() }
        canUndoVoiceInput = true
        lastParsedVoiceItems = parsed

        // Remove trailing empty rows first so we append cleanly
        while (receiptRows.isNotEmpty() && receiptRows.last().isCompletelyEmpty) {
            receiptRows.removeAt(receiptRows.lastIndex)
        }

        parsed.forEach { item ->
            val q = item.quantity.toCleanDouble().let { if (it > 0.0) it else 1.0 }
            var up = item.unitPrice.toCleanDouble()
            var tot = item.totalAmount.toCleanDouble()

            // If price was missing, check catalog for product match
            if (tot == 0.0 && up == 0.0) {
                val match = allProducts.value.find { prod ->
                    prod.name.trim().equals(item.description.trim(), ignoreCase = true) ||
                    item.description.contains(prod.name.trim(), ignoreCase = true) ||
                    prod.name.contains(item.description.trim(), ignoreCase = true)
                }
                if (match != null && match.defaultUnitPrice > 0.0) {
                    up = match.defaultUnitPrice
                    tot = up * q
                }
            } else if (tot > 0.0 && up == 0.0 && q > 0.0) {
                up = tot / q
            } else if (up > 0.0 && tot == 0.0) {
                tot = up * q
            }

            receiptRows.add(
                ReceiptRow(
                    description = item.description,
                    quantityStr = formatCleanNumber(q),
                    unitPriceStr = if (up > 0.0) formatCleanNumber(up) else "",
                    totalAmountStr = if (tot > 0.0) formatCleanNumber(tot) else ""
                )
            )
        }

        // Always keep an empty trailing row for quick manual entry
        receiptRows.add(ReceiptRow())

        if (parsed.size == 1) {
            val single = parsed[0]
            val desc = single.description
            val qty = single.quantity
            val tot = single.totalAmount.ifBlank { "0" }
            _uiEventMessage.value = "تمت الإضافة: $desc (العدد: $qty | الإجمالي: $tot ر.ي)"
        } else {
            _uiEventMessage.value = "تمت إضافة ${parsed.size} أصناف بالصوت بنجاح!"
        }
        return parsed.size
    }

    fun undoLastVoiceInput() {
        val snapshot = previousReceiptRowsSnapshot ?: return
        receiptRows.clear()
        receiptRows.addAll(snapshot)
        previousReceiptRowsSnapshot = null
        canUndoVoiceInput = false
        lastParsedVoiceItems = emptyList()
        _uiEventMessage.value = "تم التراجع عن الأصناف المضافة بالصوت"
    }

    fun clearVoiceBar() {
        voiceInputText = ""
        lastSpokenVoiceText = ""
        lastParsedVoiceItems = emptyList()
        canUndoVoiceInput = false
        previousReceiptRowsSnapshot = null
    }

    var searchQuery by mutableStateOf("")

    private val _uiEventMessage = MutableStateFlow<String?>(null)
    val uiEventMessage: StateFlow<String?> = _uiEventMessage.asStateFlow()

    companion object {
        const val DEFAULT_ROW_COUNT = 2
    }

    init {
        viewModelScope.launch {
            invoiceNumber = repository.getNextInvoiceNumber()
            if (receiptRows.isEmpty()) {
                repeat(DEFAULT_ROW_COUNT) {
                    receiptRows.add(ReceiptRow())
                }
            }
        }
    }

    fun clearUiMessage() {
        _uiEventMessage.value = null
    }

    fun resetForm() {
        viewModelScope.launch {
            invoiceNumber = repository.getNextInvoiceNumber()
            dateString = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
            customerName = ""
            paymentType = "نقداً"
            notes = ""
            receivedAmountStr = ""
            receiptRows.clear()
            repeat(DEFAULT_ROW_COUNT) {
                receiptRows.add(ReceiptRow())
            }
        }
    }

    private fun checkAutoExpand(editedIndex: Int) {
        if (editedIndex !in receiptRows.indices) return
        val row = receiptRows[editedIndex]
        // Automatically add a new row when the user types in the last row
        if (editedIndex == receiptRows.lastIndex && row.hasData) {
            receiptRows.add(ReceiptRow())
        }
    }

    fun updateDescription(index: Int, desc: String) {
        if (index in receiptRows.indices) {
            receiptRows[index] = receiptRows[index].copy(description = desc)
            checkAutoExpand(index)
        }
    }

    fun updateQuantity(index: Int, qty: String) {
        if (index in receiptRows.indices) {
            val row = receiptRows[index]
            val cleanQty = qty.toEnglishDigits().replace(",", "").replace("٬", "")
            val q = cleanQty.toCleanDouble().let { if (it > 0.0) it else 1.0 }
            val up = row.unitPrice
            val currentTot = row.total

            // Recalculate total if unit price exists, OR scale total when quantity changes
            val newTotStr = if (up > 0.0) {
                formatCleanNumber(up * q)
            } else if (currentTot > 0.0) {
                val prevQty = row.quantity
                if (prevQty <= 1.0) {
                    formatCleanNumber(currentTot * q)
                } else {
                    formatCleanNumber(currentTot)
                }
            } else {
                row.totalAmountStr
            }

            val newUpStr = if (up > 0.0) {
                row.unitPriceStr
            } else if (currentTot > 0.0 && q > 0.0) {
                formatCleanNumber(currentTot / q)
            } else {
                row.unitPriceStr
            }

            receiptRows[index] = row.copy(
                quantityStr = cleanQty,
                unitPriceStr = newUpStr,
                totalAmountStr = newTotStr
            )
            checkAutoExpand(index)
        }
    }

    fun updateUnitPrice(index: Int, unitPrice: String) {
        if (index in receiptRows.indices) {
            val row = receiptRows[index]
            val cleanUp = unitPrice.toEnglishDigits().replace(",", "").replace("٬", "")
            val up = cleanUp.toCleanDouble()
            val q = row.quantity
            val newTotStr = if (up > 0.0) formatCleanNumber(up * q) else ""
            receiptRows[index] = row.copy(
                unitPriceStr = cleanUp,
                totalAmountStr = newTotStr
            )
            checkAutoExpand(index)
        }
    }

    fun updateTotalAmount(index: Int, total: String) {
        if (index in receiptRows.indices) {
            val row = receiptRows[index]
            val cleanTot = total.toEnglishDigits().replace(",", "").replace("٬", "")
            val tot = cleanTot.toCleanDouble()
            val q = row.quantity
            val newUpStr = if (tot > 0.0 && q > 0.0) formatCleanNumber(tot / q) else ""
            receiptRows[index] = row.copy(
                totalAmountStr = cleanTot,
                unitPriceStr = newUpStr
            )
            checkAutoExpand(index)
        }
    }

    fun addManualRow() {
        receiptRows.add(ReceiptRow())
    }

    fun removeRow(index: Int) {
        if (index in receiptRows.indices) {
            val removed = receiptRows[index]
            if (receiptRows.size > 1) {
                receiptRows.removeAt(index)
            } else {
                receiptRows[0] = ReceiptRow()
            }
            if (removed.hasData) {
                val label = removed.description.ifBlank { "المحدد" }
                _uiEventMessage.value = "تم حذف الصنف '$label' من الفاتورة"
            }
        }
    }

    fun calculateSubtotal(): Double = receiptRows.sumOf { it.total }
    fun getReceivedAmount(): Double = receivedAmountStr.toCleanDouble()
    fun calculateGrandTotal(): Double = calculateSubtotal()

    fun saveInvoiceToDatabase(onSuccess: (Long) -> Unit = {}) {
        viewModelScope.launch {
            if (customerName.isBlank()) {
                customerName = "عميل نقدي"
            }
            val itemsToSave = receiptRows.filter { it.hasData }.map { row ->
                val q = row.quantity
                val tot = row.total
                val up = row.unitPrice
                InvoiceItemEntity(
                    invoiceId = 0,
                    description = row.description.ifBlank { "صنف" },
                    quantity = q,
                    unitPrice = if (up > 0.0) up else if (q > 0.0) tot / q else tot,
                    totalAmount = tot
                )
            }

            if (itemsToSave.isEmpty()) {
                _uiEventMessage.value = "يرجى كتابة أصناف الفاتورة أولاً"
                return@launch
            }

            try {
                val subtotal = calculateSubtotal()
                val grandTotal = calculateGrandTotal()
                val currentInvoiceNum = invoiceNumber
                val received = getReceivedAmount().coerceAtMost(grandTotal)
                val remaining = (grandTotal - received).coerceAtLeast(0.0)

                val invoiceEntity = InvoiceEntity(
                    invoiceNumber = currentInvoiceNum,
                    dateString = dateString,
                    customerName = customerName,
                    paymentType = paymentType,
                    subtotal = subtotal,
                    discount = 0.0,
                    paidAmount = received,
                    remainingAmount = remaining,
                    grandTotal = grandTotal,
                    notes = notes
                )

                val savedId = repository.saveInvoice(invoiceEntity, itemsToSave)
                if (received > 0.0 && customerName != "عميل نقدي") {
                    db.customerReceiptDao().insertReceipt(
                        CustomerReceiptEntity(
                            customerName = customerName.trim(),
                            amount = received,
                            dateString = dateString,
                            paymentType = "المبلغ الواصل للفاتورة #$currentInvoiceNum",
                            notes = "دفعة نقدية مرتبطة بالفاتورة رقم #$currentInvoiceNum"
                        )
                    )
                }
                _uiEventMessage.value = "تم حفظ الفاتورة بنجاح برقم #$currentInvoiceNum وحفظها في السجل!"

                // Advance to next invoice number and prepare fresh rows
                invoiceNumber = repository.getNextInvoiceNumber()
                customerName = ""
                receivedAmountStr = ""
                receiptRows.clear()
                repeat(DEFAULT_ROW_COUNT) {
                    receiptRows.add(ReceiptRow())
                }

                onSuccess(savedId)
            } catch (e: Exception) {
                _uiEventMessage.value = "حدث خطأ أثناء حفظ الفاتورة: ${e.localizedMessage}"
            }
        }
    }

    fun loadInvoiceForEditing(invoiceWithItems: InvoiceWithItems) {
        val inv = invoiceWithItems.invoice
        invoiceNumber = inv.invoiceNumber
        dateString = inv.dateString
        customerName = inv.customerName
        paymentType = inv.paymentType
        notes = inv.notes
        receivedAmountStr = if (inv.paidAmount > 0) inv.paidAmount.toString() else if (inv.discount > 0) inv.discount.toString() else ""

        receiptRows.clear()
        invoiceWithItems.items.forEach { item ->
            receiptRows.add(
                ReceiptRow(
                    description = item.description,
                    quantityStr = if (item.quantity > 0) formatCleanNumber(item.quantity) else "1",
                    unitPriceStr = if (item.unitPrice > 0) formatCleanNumber(item.unitPrice) else "",
                    totalAmountStr = if (item.totalAmount > 0) formatCleanNumber(item.totalAmount) else ""
                )
            )
        }
        // Add 1 trailing empty row
        receiptRows.add(ReceiptRow())
    }

    fun deleteInvoice(invoiceId: Long) {
        viewModelScope.launch {
            repository.deleteInvoice(invoiceId)
            _uiEventMessage.value = "تم حذف الفاتورة"
        }
    }

    fun printInvoiceAsBitmap(bitmap: Bitmap, widthDots: Int? = null) {
        viewModelScope.launch {
            val targetWidth = widthDots ?: (storeConfig.value?.printerWidthDots ?: 384)
            val result = bluetoothPrinterManager.printReceiptBitmap(bitmap, targetWidth)
            result.onSuccess {
                _uiEventMessage.value = "تمت طباعة الفاتورة حرارياً بنجاح!"
            }.onFailure { e ->
                _uiEventMessage.value = e.message ?: "خطأ في الطباعة"
            }
        }
    }

    fun updatePrinterPaperWidth(paperWidthMm: Int) {
        viewModelScope.launch {
            val current = storeConfig.value ?: StoreConfigEntity()
            val updated = current.copy(printerPaperWidth = paperWidthMm)
            repository.saveStoreConfig(updated)
            _uiEventMessage.value = "تم تعيين مقاس ورق الطابعة إلى ${paperWidthMm} مم"
        }
    }

    fun saveInvoiceImageToGallery(bitmap: Bitmap): Uri? {
        val uri = ThermalImageConverter.saveBitmapToGallery(
            context = getApplication(),
            bitmap = bitmap,
            fileName = "$invoiceNumber"
        )
        if (uri != null) {
            _uiEventMessage.value = "تم حفظ الفاتورة كصورة في الاستوديو!"
        } else {
            _uiEventMessage.value = "تعذر حفظ الصورة"
        }
        return uri
    }

    fun getShareableImageUri(bitmap: Bitmap): Uri? {
        return ThermalImageConverter.getShareableUri(getApplication(), bitmap)
    }

    fun generateInvoicePdf(): File? {
        val config = storeConfig.value ?: StoreConfigEntity()
        return InvoicePdfHelper.createInvoicePdf(
            context = getApplication(),
            storeConfig = config,
            invoiceNumber = invoiceNumber,
            dateString = dateString,
            customerName = customerName,
            paymentType = paymentType,
            rows = receiptRows,
            receivedAmount = getReceivedAmount()
        )
    }

    fun getShareableInvoicePdfUri(): Uri? {
        val file = generateInvoicePdf() ?: return null
        return InvoicePdfHelper.getShareablePdfUri(getApplication(), file)
    }

    fun saveInvoicePdfToDownloads(): Uri? {
        val file = generateInvoicePdf() ?: return null
        val uri = InvoicePdfHelper.savePdfToDownloads(getApplication(), file, "$invoiceNumber")
        if (uri != null) {
            _uiEventMessage.value = "تم حفظ ملف PDF في مجلد التنزيلات بنجاح!"
        } else {
            _uiEventMessage.value = "تعذر حفظ ملف PDF"
        }
        return uri
    }

    fun generateInvoicePdfFromItems(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): File? {
        val config = storeConfig.value ?: StoreConfigEntity()
        return InvoicePdfHelper.createInvoicePdfFromItems(
            context = getApplication(),
            storeConfig = config,
            invoiceNumber = invoice.invoiceNumber,
            dateString = invoice.dateString,
            customerName = invoice.customerName,
            paymentType = invoice.paymentType,
            items = items,
            receivedAmount = invoice.paidAmount
        )
    }

    fun getShareablePdfUriForInvoice(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): Uri? {
        val file = generateInvoicePdfFromItems(invoice, items) ?: return null
        return InvoicePdfHelper.getShareablePdfUri(getApplication(), file)
    }

    fun getShareableCustomerStatementPdf(
        customerName: String,
        statementRows: List<InvoicePdfHelper.CustomerStatementRow>,
        totalDebit: Double,
        totalCredit: Double,
        netBalance: Double
    ): Uri? {
        val config = storeConfig.value ?: StoreConfigEntity()
        val file = InvoicePdfHelper.createCustomerStatementPdf(
            context = getApplication(),
            storeConfig = config,
            customerName = customerName,
            statementRows = statementRows,
            totalDebit = totalDebit,
            totalCredit = totalCredit,
            netBalance = netBalance
        ) ?: return null
        return InvoicePdfHelper.getShareablePdfUri(getApplication(), file)
    }

    fun addCatalogProductToInvoice(name: String, price: Double) {
        val priceStr = if (price > 0) formatCleanNumber(price) else ""
        val newRow = ReceiptRow(
            description = name,
            quantityStr = "1",
            unitPriceStr = priceStr,
            totalAmountStr = priceStr
        )
        
        var placed = false
        for (i in receiptRows.indices) {
            val row = receiptRows[i]
            if (!row.hasData) {
                receiptRows[i] = newRow.copy(id = row.id)
                placed = true
                break
            }
        }

        if (!placed) {
            receiptRows.add(newRow)
        }
        _uiEventMessage.value = "تم إدراج الصنف في الفاتورة"
    }

    fun addQuickItem(name: String, quantityStr: String, unitPriceStr: String = "", totalAmountStr: String = "") {
        val cleanName = name.trim().ifEmpty { "صنف" }
        val cleanQty = quantityStr.trim().toEnglishDigits().replace(",", "").replace("٬", "").ifEmpty { "1" }
        val cleanUp = unitPriceStr.trim().toEnglishDigits().replace(",", "").replace("٬", "")
        val cleanTot = totalAmountStr.trim().toEnglishDigits().replace(",", "").replace("٬", "")

        val q = cleanQty.toCleanDouble().let { if (it > 0.0) it else 1.0 }
        val up = cleanUp.toCleanDouble()
        val tot = cleanTot.toCleanDouble()

        val finalUpStr = if (up > 0.0) {
            formatCleanNumber(up)
        } else if (tot > 0.0 && q > 0.0) {
            formatCleanNumber(tot / q)
        } else {
            ""
        }

        val finalTotStr = if (tot > 0.0) {
            formatCleanNumber(tot)
        } else if (up > 0.0) {
            formatCleanNumber(up * q)
        } else {
            "0"
        }

        val newRow = ReceiptRow(
            description = cleanName,
            quantityStr = cleanQty,
            unitPriceStr = finalUpStr,
            totalAmountStr = finalTotStr
        )

        var placed = false
        for (i in receiptRows.indices) {
            val row = receiptRows[i]
            if (!row.hasData) {
                receiptRows[i] = newRow.copy(id = row.id)
                placed = true
                break
            }
        }

        if (!placed) {
            receiptRows.add(newRow)
        }

        _uiEventMessage.value = "تمت إضافة الصنف $cleanName بنجاح!"
    }

    fun addCatalogProduct(
        name: String,
        price: Double,
        category: String = "عام",
        stockQuantity: Double = 0.0,
        minStockAlert: Double = 5.0,
        id: Long = 0
    ) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.saveProduct(
                    ProductCatalogEntity(
                        productId = id,
                        name = name,
                        defaultUnitPrice = price,
                        category = category,
                        stockQuantity = stockQuantity,
                        minStockAlert = minStockAlert
                    )
                )
                _uiEventMessage.value = "تم حفظ بيانات الصنف والمخزون بنجاح"
            }
        }
    }

    fun deleteCatalogProduct(product: ProductCatalogEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            _uiEventMessage.value = "تم حذف الصنف"
        }
    }

    fun updateStoreConfig(config: StoreConfigEntity) {
        viewModelScope.launch {
            repository.saveStoreConfig(config)
            _uiEventMessage.value = "تم حفظ إعدادات المحل/التاجر بنجاح"
        }
    }

    fun createBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = backupManager.createBackupJson()
                onResult(json)
            } catch (e: Throwable) {
                _uiEventMessage.value = "فشل في إنشاء النسخة الاحتياطية: ${e.localizedMessage}"
            }
        }
    }

    fun restoreLatestSafetyBackup(onComplete: (BackupRestoreResult) -> Unit) {
        viewModelScope.launch {
            val json = backupManager.readLatestSafetyBackupJson()
            if (json.isNullOrBlank()) {
                onComplete(BackupRestoreResult.Error("لا توجد نسخة أمان سابقة قابلة للاستعادة"))
                return@launch
            }
            val result = backupManager.restoreFromJson(json)
            when (result) {
                is BackupRestoreResult.Success -> {
                    _uiEventMessage.value = "تمت استعادة نسخة الأمان: ${result.invoicesCount} فاتورة و ${result.productsCount} صنف"
                }
                is BackupRestoreResult.Error -> {
                    _uiEventMessage.value = "فشل استرجاع نسخة الأمان: ${result.message}"
                }
            }
            onComplete(result)
        }
    }

    fun restoreFromBackupJson(jsonString: String, onComplete: (BackupRestoreResult) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.restoreFromJson(jsonString)
            when (result) {
                is BackupRestoreResult.Success -> {
                    _uiEventMessage.value = "تم استعادة ${result.invoicesCount} فاتورة و ${result.productsCount} صنف بنجاح!"
                }
                is BackupRestoreResult.Error -> {
                    _uiEventMessage.value = "فشل في الاستعادة: ${result.message}"
                }
            }
            onComplete(result)
        }
    }

    fun addCustomerReceipt(
        customerName: String,
        amount: Double,
        paymentType: String,
        notes: String,
        isDebit: Boolean = false
    ) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
            db.customerReceiptDao().insertReceipt(
                CustomerReceiptEntity(
                    customerName = customerName,
                    amount = amount,
                    dateString = dateStr,
                    paymentType = paymentType,
                    notes = notes,
                    isDebit = isDebit
                )
            )
            val actionLabel = if (isDebit) "قيد دين (عليه)" else "سند قبض/تسديد (له)"
            _uiEventMessage.value = "تم تسجيل $actionLabel بمبلغ $amount بنجاح"
        }
    }

    fun deleteCustomerReceipt(receiptId: Long) {
        viewModelScope.launch {
            db.customerReceiptDao().deleteReceipt(receiptId)
            _uiEventMessage.value = "تم حذف سند القبض"
        }
    }

    fun updateCustomerReceipt(receipt: CustomerReceiptEntity) {
        viewModelScope.launch {
            db.customerReceiptDao().insertReceipt(receipt)
            _uiEventMessage.value = "تم تعديل سند القبض بنجاح"
        }
    }

    fun deleteMultipleInvoices(invoiceIds: List<Long>) {
        viewModelScope.launch {
            invoiceIds.forEach { repository.deleteInvoice(it) }
            _uiEventMessage.value = "تم حذف ${invoiceIds.size} فاتورة بنجاح"
        }
    }

    fun deleteMultipleReceipts(receiptIds: List<Long>) {
        viewModelScope.launch {
            receiptIds.forEach { db.customerReceiptDao().deleteReceipt(it) }
            _uiEventMessage.value = "تم حذف ${receiptIds.size} سند بنجاح"
        }
    }

    fun printCustomerReceiptVoucher(receipt: CustomerReceiptEntity) {
        viewModelScope.launch {
            val config = storeConfig.value ?: StoreConfigEntity()
            val width = config.printerWidthDots
            val bitmap = ReceiptBitmapHelper.createVoucherBitmap(
                context = getApplication(),
                storeConfig = config,
                receipt = receipt,
                widthPx = width
            )
            val result = bluetoothPrinterManager.printReceiptBitmap(bitmap, width)
            result.onSuccess {
                _uiEventMessage.value = "تمت طباعة سند القبض حرارياً بنجاح!"
            }.onFailure { e ->
                _uiEventMessage.value = e.message ?: "خطأ في طباعة السند"
            }
        }
    }

    fun printCustomerStatementSummary(customerName: String, statementRows: List<InvoicePdfHelper.CustomerStatementRow>) {
        viewModelScope.launch {
            val config = storeConfig.value ?: StoreConfigEntity()
            val width = config.printerWidthDots
            val bitmap = ReceiptBitmapHelper.createCustomerStatementThermalBitmap(
                context = getApplication(),
                storeConfig = config,
                customerName = customerName,
                statementRows = statementRows,
                widthPx = width
            )
            val result = bluetoothPrinterManager.printReceiptBitmap(bitmap, width)
            result.onSuccess {
                _uiEventMessage.value = "تمت طباعة تقرير كشف الحساب حرارياً!"
            }.onFailure { e ->
                _uiEventMessage.value = e.message ?: "خطأ في طباعة كشف الحساب"
            }
        }
    }
}

