package com.example.data.repository

import com.example.data.local.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class InvoiceRepository(private val db: AppDatabase) {

    val storeConfig: Flow<StoreConfigEntity?> = db.storeConfigDao().getStoreConfig()
    val allInvoices: Flow<List<InvoiceWithItems>> = db.invoiceDao().getAllInvoicesWithItems()
    val allProducts: Flow<List<ProductCatalogEntity>> = db.productDao().getAllProducts()
    val allReceipts: Flow<List<CustomerReceiptEntity>> = db.customerReceiptDao().getAllReceipts()

    /** Replaces all backup-managed tables atomically. Duplicate invoice numbers and product names are preserved. */
    suspend fun replaceAllData(
        config: StoreConfigEntity,
        products: List<ProductCatalogEntity>,
        invoices: List<Pair<InvoiceEntity, List<InvoiceItemEntity>>>,
        receipts: List<CustomerReceiptEntity> = emptyList()
    ) {
        db.withTransaction {
            db.invoiceDao().clearAllItems()
            db.invoiceDao().clearAllInvoices()
            db.productDao().clearAll()
            db.customerReceiptDao().clearAll()
            db.storeConfigDao().clearAll()
            db.storeConfigDao().insertOrUpdate(config)
            products.forEach { db.productDao().insertProduct(it) }
            invoices.forEach { (invoice, items) ->
                val id = db.invoiceDao().insertInvoice(invoice.copy(invoiceId = 0))
                if (items.isNotEmpty()) {
                    db.invoiceDao().insertInvoiceItems(items.map { it.copy(itemId = 0, invoiceId = id) })
                }
            }
            receipts.forEach { db.customerReceiptDao().insertReceipt(it.copy(receiptId = 0)) }
        }
    }

    fun searchInvoices(query: String): Flow<List<InvoiceWithItems>> =
        db.invoiceDao().searchInvoices(query)

    suspend fun getInvoiceById(id: Long): InvoiceWithItems? =
        db.invoiceDao().getInvoiceById(id)

    suspend fun saveStoreConfig(config: StoreConfigEntity) {
        db.storeConfigDao().insertOrUpdate(config)
    }

    suspend fun saveInvoice(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): Long {
        val id = db.invoiceDao().saveFullInvoice(invoice, items)
        
        // Auto increment store config next invoice number if this was a new invoice
        val currentConfig = db.storeConfigDao().getStoreConfigSync() ?: StoreConfigEntity()
        if (invoice.invoiceNumber >= currentConfig.nextInvoiceNumber) {
            db.storeConfigDao().insertOrUpdate(
                currentConfig.copy(nextInvoiceNumber = invoice.invoiceNumber + 1)
            )
        }

        // Auto-save any entered item into the products catalog & deduct inventory
        for (item in items) {
            val cleanName = item.description.trim()
            if (cleanName.isNotBlank()) {
                val existing = db.productDao().getProductByName(cleanName)
                val unitPrice = if (item.quantity > 0) item.totalAmount / item.quantity else item.totalAmount
                if (existing == null) {
                    db.productDao().insertProduct(
                        ProductCatalogEntity(
                            name = cleanName,
                            defaultUnitPrice = unitPrice,
                            category = "عام",
                            stockQuantity = 0.0,
                            minStockAlert = 5.0
                        )
                    )
                } else {
                    val updatedStock = if (existing.stockQuantity > 0.0) {
                        maxOf(0.0, existing.stockQuantity - item.quantity)
                    } else existing.stockQuantity
                    db.productDao().insertProduct(
                        existing.copy(
                            defaultUnitPrice = if (unitPrice > 0) unitPrice else existing.defaultUnitPrice,
                            stockQuantity = updatedStock
                        )
                    )
                }
            }
        }

        return id
    }

    suspend fun autoRegisterProductIfNew(name: String, price: Double = 0.0) {
        val cleanName = name.trim()
        if (cleanName.isNotBlank()) {
            val existing = db.productDao().getProductByName(cleanName)
            if (existing == null) {
                db.productDao().insertProduct(
                    ProductCatalogEntity(
                        name = cleanName,
                        defaultUnitPrice = price,
                        category = "عام",
                        stockQuantity = 0.0,
                        minStockAlert = 5.0
                    )
                )
            }
        }
    }

    suspend fun deleteInvoice(invoiceId: Long) {
        db.invoiceDao().deleteInvoice(invoiceId)
    }

    suspend fun saveProduct(product: ProductCatalogEntity) {
        db.productDao().insertProduct(product)
    }

    suspend fun deleteProduct(product: ProductCatalogEntity) {
        db.productDao().deleteProduct(product)
    }

    suspend fun getNextInvoiceNumber(): Int {
        val maxNumber = db.invoiceDao().getMaxInvoiceNumber()
        val config = db.storeConfigDao().getStoreConfigSync()
        val configNext = config?.nextInvoiceNumber ?: 1001
        return if (maxNumber != null) maxOf(maxNumber + 1, configNext) else configNext
    }
}
