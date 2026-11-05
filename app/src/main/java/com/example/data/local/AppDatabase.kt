package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        StoreConfigEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        ProductCatalogEntity::class,
        CustomerReceiptEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeConfigDao(): StoreConfigDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun productDao(): ProductDao
    abstract fun customerReceiptDao(): CustomerReceiptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN currency TEXT NOT NULL DEFAULT 'YER'")
                db.execSQL("ALTER TABLE invoices ADD COLUMN qrCodeData TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products_catalog ADD COLUMN stockQuantity REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE products_catalog ADD COLUMN minStockAlert REAL NOT NULL DEFAULT 5.0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_customerName ON invoices(customerName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_dateString ON invoices(dateString)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_createdAtTimestamp ON invoices(createdAtTimestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_catalog_name ON products_catalog(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_receipts_customerName ON customer_receipts(customerName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_receipts_dateString ON customer_receipts(dateString)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customer_receipts ADD COLUMN isDebit INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fatooraty_database.db"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration(false)
                .addCallback(DatabaseCallback(context.applicationContext))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val appDb = getDatabase(context)
                    appDb.storeConfigDao().insertOrUpdate(StoreConfigEntity())
                }
            }
        }
    }
}
