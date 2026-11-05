package com.example.data.backup

import android.content.Context
import com.example.data.local.*
import com.example.data.repository.InvoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(
    private val context: Context,
    private val repository: InvoiceRepository
) {
    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 2)
        root.put("appName", "Fatooraty Al-Ezzi")
        root.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        root.put("timestamp", System.currentTimeMillis())

        val config = repository.storeConfig.first() ?: StoreConfigEntity()
        root.put("storeConfig", JSONObject().apply {
            put("storeNameArabic", config.storeNameArabic)
            put("storeNameEnglish", config.storeNameEnglish)
            put("storeSubtitle", config.storeSubtitle)
            put("logoText", config.logoText)
            put("address", config.address)
            put("phone1", config.phone1)
            put("phone2", config.phone2)
            put("currencySymbol", config.currencySymbol)
            put("nextInvoiceNumber", config.nextInvoiceNumber)
            put("defaultDisclaimerNote", config.defaultDisclaimerNote)
            put("printerPaperWidth", config.printerPaperWidth)
        })

        val products = repository.allProducts.first()
        root.put("products", JSONArray().apply {
            products.forEach { product ->
                put(JSONObject().apply {
                    put("name", product.name)
                    put("defaultUnitPrice", product.defaultUnitPrice)
                    put("category", product.category)
                })
            }
        })

        val invoices = repository.allInvoices.first()
        root.put("invoices", JSONArray().apply {
            invoices.forEach { entry ->
                val invoice = entry.invoice
                put(JSONObject().apply {
                    put("invoiceNumber", invoice.invoiceNumber)
                    put("dateString", invoice.dateString)
                    put("customerName", invoice.customerName)
                    put("paymentType", invoice.paymentType)
                    put("subtotal", invoice.subtotal)
                    put("discount", invoice.discount)
                    put("paidAmount", invoice.paidAmount)
                    put("remainingAmount", invoice.remainingAmount)
                    put("grandTotal", invoice.grandTotal)
                    put("notes", invoice.notes)
                    put("createdAtTimestamp", invoice.createdAtTimestamp)
                    put("items", JSONArray().apply {
                        entry.items.forEach { item ->
                            put(JSONObject().apply {
                                put("description", item.description)
                                put("quantity", item.quantity)
                                put("unitPrice", item.unitPrice)
                                put("totalAmount", item.totalAmount)
                            })
                        }
                    })
                })
            }
        })

        root.put("customerReceipts", JSONArray().apply {
            repository.allReceipts.first().forEach { receipt ->
                put(JSONObject().apply {
                    put("customerName", receipt.customerName)
                    put("amount", receipt.amount)
                    put("dateString", receipt.dateString)
                    put("paymentType", receipt.paymentType)
                    put("notes", receipt.notes)
                    put("createdAtTimestamp", receipt.createdAtTimestamp)
                    put("isDebit", receipt.isDebit)
                })
            }
        })
        root.toString(2)
    }

    suspend fun readLatestSafetyBackupJson(): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "restore-safety-backups")
        val latest = dir.listFiles { file -> file.isFile && file.name.startsWith("before_restore_") && file.extension == "json" }
            ?.maxByOrNull { it.lastModified() } ?: return@withContext null
        latest.readText(Charsets.UTF_8).takeIf { it.isNotBlank() }
    }

    /** Validate the whole file first, save a restorable safety copy, then replace tables atomically. */
    suspend fun restoreFromJson(jsonString: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("products") || !root.has("invoices")) {
                return@withContext BackupRestoreResult.Error("الملف لا يحتوي على قسمي الأصناف والفواتير المطلوبين")
            }

            val configJson = root.optJSONObject("storeConfig")
            val currentConfig = repository.storeConfig.first() ?: StoreConfigEntity()
            val config = if (configJson == null) currentConfig else StoreConfigEntity(
                id = 1,
                storeNameArabic = configJson.optString("storeNameArabic", currentConfig.storeNameArabic),
                storeNameEnglish = configJson.optString("storeNameEnglish", currentConfig.storeNameEnglish),
                storeSubtitle = configJson.optString("storeSubtitle", currentConfig.storeSubtitle),
                logoText = configJson.optString("logoText", currentConfig.logoText),
                address = configJson.optString("address", currentConfig.address),
                phone1 = configJson.optString("phone1", currentConfig.phone1),
                phone2 = configJson.optString("phone2", currentConfig.phone2),
                currencySymbol = configJson.optString("currencySymbol", currentConfig.currencySymbol),
                nextInvoiceNumber = configJson.optInt("nextInvoiceNumber", currentConfig.nextInvoiceNumber),
                defaultDisclaimerNote = configJson.optString("defaultDisclaimerNote", currentConfig.defaultDisclaimerNote),
                printerPaperWidth = configJson.optInt("printerPaperWidth", currentConfig.printerPaperWidth)
            )

            val productsArray = root.getJSONArray("products")
            val products = ArrayList<ProductCatalogEntity>(productsArray.length())
            for (i in 0 until productsArray.length()) {
                val p = productsArray.getJSONObject(i)
                val name = p.optString("name", "")
                if (name.isBlank()) {
                    return@withContext BackupRestoreResult.Error("يوجد صنف بلا اسم في الملف؛ لم يتم تغيير قاعدة البيانات")
                }
                products.add(ProductCatalogEntity(
                    name = name,
                    defaultUnitPrice = p.optDouble("defaultUnitPrice", 0.0),
                    category = p.optString("category", "عام")
                ))
            }

            val invoicesArray = root.getJSONArray("invoices")
            val invoices = ArrayList<Pair<InvoiceEntity, List<InvoiceItemEntity>>>(invoicesArray.length())
            for (i in 0 until invoicesArray.length()) {
                val jsonInvoice = invoicesArray.getJSONObject(i)
                if (!jsonInvoice.has("invoiceNumber")) {
                    return@withContext BackupRestoreResult.Error("الفاتورة رقم ${i + 1} لا تحتوي على رقم فاتورة؛ لم يتم تغيير قاعدة البيانات")
                }
                val invoice = InvoiceEntity(
                    invoiceId = 0,
                    invoiceNumber = jsonInvoice.getInt("invoiceNumber"),
                    dateString = jsonInvoice.optString("dateString", ""),
                    customerName = jsonInvoice.optString("customerName", ""),
                    paymentType = jsonInvoice.optString("paymentType", "نقداً"),
                    subtotal = jsonInvoice.optDouble("subtotal", 0.0),
                    discount = jsonInvoice.optDouble("discount", 0.0),
                    paidAmount = if (jsonInvoice.has("paidAmount")) {
                        jsonInvoice.optDouble("paidAmount", 0.0)
                    } else if (jsonInvoice.optString("paymentType", "نقداً") == "نقداً") {
                        jsonInvoice.optDouble("grandTotal", 0.0)
                    } else {
                        0.0
                    },
                    remainingAmount = if (jsonInvoice.has("remainingAmount")) {
                        jsonInvoice.optDouble("remainingAmount", 0.0)
                    } else {
                        (jsonInvoice.optDouble("grandTotal", 0.0) - if (jsonInvoice.has("paidAmount")) {
                            jsonInvoice.optDouble("paidAmount", 0.0)
                        } else if (jsonInvoice.optString("paymentType", "نقداً") == "نقداً") {
                            jsonInvoice.optDouble("grandTotal", 0.0)
                        } else {
                            0.0
                        }).coerceAtLeast(0.0)
                    },
                    grandTotal = jsonInvoice.optDouble("grandTotal", 0.0),
                    notes = jsonInvoice.optString("notes", ""),
                    createdAtTimestamp = jsonInvoice.optLong("createdAtTimestamp", System.currentTimeMillis() + i)
                )
                val items = ArrayList<InvoiceItemEntity>()
                val itemsArray = jsonInvoice.optJSONArray("items") ?: JSONArray()
                for (j in 0 until itemsArray.length()) {
                    val item = itemsArray.getJSONObject(j)
                    items.add(InvoiceItemEntity(
                        itemId = 0,
                        invoiceId = 0,
                        description = item.optString("description", ""),
                        quantity = item.optDouble("quantity", 1.0),
                        unitPrice = item.optDouble("unitPrice", 0.0),
                        totalAmount = item.optDouble("totalAmount", 0.0)
                    ))
                }
                invoices.add(invoice to items)
            }

            val receiptsArray = root.optJSONArray("customerReceipts")
            val receipts = if (receiptsArray == null) {
                // Legacy backups do not contain receipts; keep current receipt history instead of deleting it.
                repository.allReceipts.first()
            } else {
                ArrayList<CustomerReceiptEntity>(receiptsArray.length()).also { parsed ->
                    for (i in 0 until receiptsArray.length()) {
                        val receipt = receiptsArray.getJSONObject(i)
                        parsed.add(
                            CustomerReceiptEntity(
                                receiptId = 0,
                                customerName = receipt.optString("customerName", ""),
                                amount = receipt.optDouble("amount", 0.0),
                                dateString = receipt.optString("dateString", ""),
                                paymentType = receipt.optString("paymentType", ""),
                                notes = receipt.optString("notes", ""),
                                createdAtTimestamp = receipt.optLong(
                                    "createdAtTimestamp",
                                    System.currentTimeMillis() + i
                                ),
                                isDebit = receipt.optBoolean("isDebit", false)
                            )
                        )
                    }
                }
            }

            val safetyJson = createBackupJson()
            val backupDir = File(context.filesDir, "restore-safety-backups")
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                return@withContext BackupRestoreResult.Error("تعذر إنشاء مجلد النسخة الاحتياطية؛ لم يتم تغيير قاعدة البيانات")
            }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val safetyFile = File(backupDir, "before_restore_$stamp.json")
            safetyFile.writeText(safetyJson, Charsets.UTF_8)
            if (!safetyFile.isFile || safetyFile.length() == 0L) {
                return@withContext BackupRestoreResult.Error("فشل حفظ النسخة الاحتياطية السابقة؛ لم يتم تغيير قاعدة البيانات")
            }

            repository.replaceAllData(config, products, invoices, receipts)
            BackupRestoreResult.Success(invoices.size, products.size, safetyFile.name)
        } catch (e: Throwable) {
            BackupRestoreResult.Error(e.localizedMessage ?: "فشل في قراءة الملف أو استعادة البيانات")
        }
    }
}

sealed class BackupRestoreResult {
    data class Success(
        val invoicesCount: Int,
        val productsCount: Int,
        val safetyBackupFileName: String = ""
    ) : BackupRestoreResult()
    data class Error(val message: String) : BackupRestoreResult()
}
