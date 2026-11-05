package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Locale

/** One-time, read-only import from the existing Al-Azzi SQLite database. */
object LegacyDataImporter {
    private const val LEGACY_DB = "alazzi_grocery_pro_v5.db"
    private const val PREFS = "invoice_center_legacy_import"
    private const val KEY_DONE = "v1_done"

    fun importIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        val file = context.getDatabasePath(LEGACY_DB)
        if (!file.exists()) { prefs.edit().putBoolean(KEY_DONE, true).apply(); return }
        var legacy: SQLiteDatabase? = null
        try {
            val legacyDb = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            legacy = legacyDb
            val room = AppDatabase.getDatabase(context)
            room.runInTransaction {
                val target = room.openHelper.writableDatabase
                val count = target.query("SELECT COUNT(*) FROM invoices", emptyArray())
                val alreadyHasInvoices = try { count.moveToFirst() && count.getLong(0) > 0 } finally { count.close() }
                if (alreadyHasInvoices) return@runInTransaction
                val invoiceIds = HashMap<Long, Long>()
                var nextNumber = 1001
                val invoices = legacyDb.rawQuery("SELECT id,no,customer,total,paid,date,sale_type FROM invoices ORDER BY id ASC", null)
                try {
                    while (invoices.moveToNext()) {
                        val oldId = invoices.getLong(0)
                        val no = invoices.getString(1)?.toIntOrNull() ?: nextNumber
                        nextNumber = maxOf(nextNumber, no + 1)
                        val customer = invoices.getString(2)?.trim().orEmpty().ifBlank { "عميل نقدي" }
                        val total = invoices.getDouble(3).coerceAtLeast(0.0)
                        val paid = invoices.getDouble(4).coerceIn(0.0, total)
                        val date = invoices.getString(5).orEmpty()
                        val rawType = invoices.getString(6).orEmpty()
                        val values = ContentValues().apply {
                            put("invoiceNumber", no); put("dateString", date); put("customerName", customer)
                            put("paymentType", if (rawType.contains("نقد") || rawType.equals("cash", true)) "نقداً" else "أجل")
                            put("subtotal", total); put("discount", 0.0); put("paidAmount", paid)
                            put("remainingAmount", (total-paid).coerceAtLeast(0.0)); put("grandTotal", total)
                            put("notes", ""); put("currency", "YER"); put("qrCodeData", "")
                            put("createdAtTimestamp", parseDate(date))
                        }
                        invoiceIds[oldId] = target.insert("invoices", SQLiteDatabase.CONFLICT_ABORT, values)
                    }
                } finally { invoices.close() }
                val items = legacy.rawQuery("SELECT invoice_id,name,qty,total,unit_cost FROM invoice_items ORDER BY id ASC", null)
                try {
                    while (items.moveToNext()) {
                        val newId = invoiceIds[items.getLong(0)] ?: continue
                        val qty = items.getDouble(2).coerceAtLeast(0.0); val total = items.getDouble(3).coerceAtLeast(0.0)
                        val values = ContentValues().apply {
                            put("invoiceId", newId); put("description", items.getString(1).orEmpty())
                            put("quantity", qty); put("unitPrice", if (qty>0) total/qty else total); put("totalAmount", total)
                        }
                        target.insert("invoice_items", SQLiteDatabase.CONFLICT_ABORT, values)
                    }
                } finally { items.close() }
                val products = legacy.rawQuery("SELECT name,qty,min_qty,cost,sale FROM items ORDER BY id ASC", null)
                try {
                    while (products.moveToNext()) {
                        val name = products.getString(0)?.trim().orEmpty(); if (name.isBlank()) continue
                        val check = target.query("SELECT productId FROM products_catalog WHERE name=?", arrayOf(name))
                        val exists = try { check.moveToFirst() } finally { check.close() }
                        if (exists) continue
                        val values = ContentValues().apply {
                            put("name", name); put("defaultUnitPrice", products.getDouble(4)); put("category", "عام")
                            put("stockQuantity", products.getDouble(1)); put("minStockAlert", products.getDouble(2))
                        }
                        target.insert("products_catalog", SQLiteDatabase.CONFLICT_ABORT, values)
                    }
                } finally { products.close() }
                val receipts = legacy.rawQuery("SELECT t.amount,t.details,t.type,t.date,c.name FROM transactions t LEFT JOIN customers c ON c.id=t.customer_id ORDER BY t.id ASC", null)
                try {
                    while (receipts.moveToNext()) {
                        val amount = receipts.getDouble(0).coerceAtLeast(0.0); if (amount<=0) continue
                        val details = receipts.getString(1).orEmpty(); val type = receipts.getInt(2)
                        if (type==1 && details.startsWith("فاتورة مبيعات رقم ")) continue
                        val customer = receipts.getString(4)?.trim().orEmpty(); if (customer.isBlank()) continue
                        val date = receipts.getString(3).orEmpty()
                        val values = ContentValues().apply {
                            put("customerName", customer); put("amount", amount); put("dateString", date)
                            put("paymentType", details.ifBlank { if(type==1) "قيد دين" else "سند قبض" })
                            put("notes", details); put("createdAtTimestamp", parseDate(date)); put("isDebit", if(type==1) 1 else 0)
                        }
                        target.insert("customer_receipts", SQLiteDatabase.CONFLICT_ABORT, values)
                    }
                } finally { receipts.close() }
                val cfg = target.query("SELECT id FROM store_config WHERE id=1", emptyArray())
                val hasConfig = try { cfg.moveToFirst() } finally { cfg.close() }
                if (!hasConfig) {
                    val values = ContentValues().apply {
                        put("id",1); put("storeNameArabic","بقالة العزي"); put("storeNameEnglish","Al-Ezzi Grocery")
                        put("storeSubtitle","مواد غذائية واستهلاكية"); put("logoText","العزي"); put("currencySymbol","ر.ي")
                        put("nextInvoiceNumber",nextNumber); put("printerPaperWidth",58)
                    }
                    target.insert("store_config", SQLiteDatabase.CONFLICT_ABORT, values)
                } else target.execSQL("UPDATE store_config SET nextInvoiceNumber=MAX(nextInvoiceNumber, ?) WHERE id=1", arrayOf<Any>(nextNumber))
            }
            prefs.edit().putBoolean(KEY_DONE,true).apply()
        } catch(e:Exception) { e.printStackTrace() }
        finally { try { legacy?.close() } catch(_:Exception) {} }
    }
    private fun parseDate(value:String):Long {
        for(pattern in arrayOf("yyyy/MM/dd HH:mm:ss","yyyy-MM-dd HH:mm:ss","yyyy/MM/dd","yyyy-MM-dd")) {
            try { val parsed=SimpleDateFormat(pattern,Locale.US).parse(value); if(parsed!=null) return parsed.time } catch(_:Exception) {}
        }
        return System.currentTimeMillis()
    }
}
