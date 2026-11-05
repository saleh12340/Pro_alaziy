package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "store_config")
data class StoreConfigEntity(
    @PrimaryKey val id: Int = 1,
    val storeNameArabic: String = "بقالة العزي",
    val storeNameEnglish: String = "Al-Ezzi Grocery",
    val storeSubtitle: String = "مواد غذائية واستهلاكية",
    val logoText: String = "العزي",
    val address: String = "العنوان: صنعاء",
    val phone1: String = "772437314",
    val phone2: String = "770000000",
    val currencySymbol: String = "ر.ي",
    val nextInvoiceNumber: Int = 1118,
    val defaultDisclaimerNote: String = "البضاعة المباعة لا ترد ولا تستبدل بعد خروجها من المحل",
    val printerPaperWidth: Int = 58 // 58 or 80 mm
) {
    val printerWidthDots: Int
        get() = if (printerPaperWidth == 80) 576 else 384
}

@Entity(
    tableName = "invoices",
    indices = [
        Index("customerName"),
        Index("dateString"),
        Index("createdAtTimestamp")
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val invoiceId: Long = 0,
    val invoiceNumber: Int,
    val dateString: String,
    val customerName: String,
    val paymentType: String, // "نقداً" or "أجل"
    val subtotal: Double,
    val discount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val grandTotal: Double,
    val notes: String = "",
    val currency: String = "YER",
    val qrCodeData: String = "",
    val createdAtTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["invoiceId"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["invoiceId"])]
)
data class InvoiceItemEntity(
    @PrimaryKey(autoGenerate = true) val itemId: Long = 0,
    val invoiceId: Long,
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalAmount: Double
)

@Entity(
    tableName = "products_catalog",
    indices = [Index("name")]
)
data class ProductCatalogEntity(
    @PrimaryKey(autoGenerate = true) val productId: Long = 0,
    val name: String,
    val defaultUnitPrice: Double = 0.0,
    val category: String = "عام",
    val stockQuantity: Double = 0.0,
    val minStockAlert: Double = 5.0
)

@Entity(
    tableName = "customer_receipts",
    indices = [
        Index("customerName"),
        Index("dateString")
    ]
)
data class CustomerReceiptEntity(
    @PrimaryKey(autoGenerate = true) val receiptId: Long = 0,
    val customerName: String,
    val amount: Double,
    val dateString: String,
    val paymentType: String, // e.g. "سند قبض", "تسديد جزئي للفاتورة", "تسديد حساب العميل", "قيد دين"
    val notes: String = "",
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val isDebit: Boolean = false
)
