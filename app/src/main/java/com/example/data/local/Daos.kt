package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreConfigDao {
    @Query("SELECT * FROM store_config WHERE id = 1 LIMIT 1")
    fun getStoreConfig(): Flow<StoreConfigEntity?>

    @Query("SELECT * FROM store_config WHERE id = 1 LIMIT 1")
    suspend fun getStoreConfigSync(): StoreConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: StoreConfigEntity)

    @Query("DELETE FROM store_config")
    suspend fun clearAll()
}

data class InvoiceWithItems(
    @Embedded val invoice: InvoiceEntity,
    @Relation(
        parentColumn = "invoiceId",
        entityColumn = "invoiceId"
    )
    val items: List<InvoiceItemEntity>
)

@Dao
interface InvoiceDao {
    @Transaction
    @Query("SELECT * FROM invoices ORDER BY invoiceId DESC, createdAtTimestamp DESC")
    fun getAllInvoicesWithItems(): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM invoices WHERE invoiceId = :id LIMIT 1")
    suspend fun getInvoiceById(id: Long): InvoiceWithItems?

    @Transaction
    @Query("SELECT * FROM invoices WHERE customerName LIKE '%' || :query || '%' OR invoiceNumber LIKE '%' || :query || '%' ORDER BY invoiceId DESC, createdAtTimestamp DESC")
    fun searchInvoices(query: String): Flow<List<InvoiceWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceItems(items: List<InvoiceItemEntity>)

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteInvoiceItems(invoiceId: Long)

    @Query("DELETE FROM invoice_items")
    suspend fun clearAllItems()

    @Query("DELETE FROM invoices")
    suspend fun clearAllInvoices()

    @Transaction
    suspend fun saveFullInvoice(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): Long {
        val invoiceId = insertInvoice(invoice)
        deleteInvoiceItems(invoiceId)
        val itemsWithId = items.map { it.copy(invoiceId = invoiceId) }
        insertInvoiceItems(itemsWithId)
        return invoiceId
    }

    @Query("DELETE FROM invoices WHERE invoiceId = :invoiceId")
    suspend fun deleteInvoice(invoiceId: Long)

    @Query("SELECT MAX(invoiceNumber) FROM invoices")
    suspend fun getMaxInvoiceNumber(): Int?
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products_catalog ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductCatalogEntity>>

    @Query("SELECT * FROM products_catalog WHERE name = :name LIMIT 1")
    suspend fun getProductByName(name: String): ProductCatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductCatalogEntity): Long

    @Delete
    suspend fun deleteProduct(product: ProductCatalogEntity)

    @Query("DELETE FROM products_catalog")
    suspend fun clearAll()
}

@Dao
interface CustomerReceiptDao {
    @Query("SELECT * FROM customer_receipts ORDER BY receiptId DESC")
    fun getAllReceipts(): Flow<List<CustomerReceiptEntity>>

    @Query("SELECT * FROM customer_receipts WHERE customerName = :customerName ORDER BY receiptId DESC")
    fun getReceiptsForCustomer(customerName: String): Flow<List<CustomerReceiptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: CustomerReceiptEntity): Long

    @Query("DELETE FROM customer_receipts WHERE receiptId = :receiptId")
    suspend fun deleteReceipt(receiptId: Long)

    @Query("DELETE FROM customer_receipts")
    suspend fun clearAll()
}

