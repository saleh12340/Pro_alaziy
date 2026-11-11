package com.saleh.enezi;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {
    private Context context;
    private File databaseFile;
    private SQLiteDatabase database;
    private MainActivity.DB helper;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseFile = new File(context.getCacheDir(), "migration-fixture.db");
        if (databaseFile.exists()) databaseFile.delete();
        database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        helper = new MainActivity().new DB(context);
        database.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT)");
        database.execSQL("CREATE TABLE invoices(id INTEGER PRIMARY KEY AUTOINCREMENT,no TEXT,customer TEXT,total REAL,paid REAL DEFAULT 0,date TEXT)");
        database.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,customer_id INTEGER,amount REAL,details TEXT,type INTEGER,date TEXT)");
        database.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,qty REAL,min_qty REAL,cost REAL DEFAULT 0,sale REAL DEFAULT 0)");
        database.execSQL("CREATE TABLE suppliers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT)");
        database.execSQL("CREATE TABLE purchase_invoices(id INTEGER PRIMARY KEY AUTOINCREMENT,no TEXT,supplier TEXT,total REAL,paid REAL DEFAULT 0,date TEXT)");
        helper.create(database);
    }

    @After public void tearDown() {
        if (database != null && database.isOpen()) database.close();
        if (databaseFile != null && databaseFile.exists()) databaseFile.delete();
        if (helper != null) helper.close();
    }

    private long scalar(String sql) {
        Cursor c = database.rawQuery(sql, null);
        try { assertTrue(c.moveToFirst()); return c.getLong(0); }
        finally { c.close(); }
    }

    @Test public void mergesDuplicatesAndPreservesLinkedBalancesAndStock() {
        database.execSQL("INSERT INTO customers(id,name,phone) VALUES(1,'أحمد','')");
        database.execSQL("INSERT INTO customers(id,name,phone) VALUES(2,' أحمد ','777')");
        database.execSQL("INSERT INTO transactions(customer_id,amount,details,type,date) VALUES(2,125,'رصيد قديم',1,'2025-01-01')");
        database.execSQL("INSERT INTO invoices(no,customer,total,paid,date) VALUES('S-100',' أحمد ',500,100,'2025-01-02')");
        database.execSQL("INSERT INTO orders(no,customer,total,paid,date) VALUES('O-100',' أحمد ',200,0,'2025-01-03')");

        database.execSQL("INSERT INTO items(id,name,qty,cost,sale) VALUES(1,'سكر',5,10,15)");
        database.execSQL("INSERT INTO items(id,name,qty,cost,sale) VALUES(2,' سكر ',7,10,15)");
        database.execSQL("INSERT INTO stock_movements(item_id,item_name,qty,unit_cost,source_type,source_id,created_at) VALUES(2,' سكر ',3,10,'test',1,'2025-01-01')");
        database.execSQL("INSERT INTO warehouse_transfers(item_id,qty) VALUES(2,2)");
        database.execSQL("INSERT INTO offers(name,item_id) VALUES('عرض',2)");
        database.execSQL("INSERT INTO product_prices(item_id,price_type,price,unit) VALUES(2,'sale',15,'piece')");

        helper.onUpgrade(database, 4, 5);

        assertEquals(1, scalar("SELECT COUNT(*) FROM customers"));
        assertEquals(1, scalar("SELECT customer_id FROM transactions LIMIT 1"));
        assertEquals("أحمد", stringValue("SELECT customer FROM invoices LIMIT 1"));
        assertEquals("أحمد", stringValue("SELECT customer FROM orders LIMIT 1"));
        assertEquals("777", stringValue("SELECT phone FROM customers LIMIT 1"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM items"));
        assertEquals(12d, doubleValue("SELECT qty FROM items LIMIT 1"), 0.001d);
        assertEquals(1, scalar("SELECT item_id FROM stock_movements LIMIT 1"));
        assertEquals(1, scalar("SELECT item_id FROM warehouse_transfers LIMIT 1"));
        assertEquals(1, scalar("SELECT item_id FROM offers LIMIT 1"));
        assertEquals(1, scalar("SELECT item_id FROM product_prices LIMIT 1"));
        assertEquals(125d, doubleValue("SELECT amount FROM transactions LIMIT 1"), 0.001d);
        assertEquals(500d, doubleValue("SELECT total FROM invoices LIMIT 1"), 0.001d);
    }

    @Test public void rejectsDuplicateInvoiceNumbersWithoutChangingHistoricalRows() {
        database.execSQL("INSERT INTO customers(id,name) VALUES(1,'عميل')");
        database.execSQL("INSERT INTO invoices(no,customer,total,paid,date) VALUES('DUP-1','عميل',100,0,'2025-01-01')");
        database.execSQL("INSERT INTO invoices(no,customer,total,paid,date) VALUES('DUP-1','عميل',200,0,'2025-01-02')");

        boolean rejected = false;
        try { helper.normalizeAndConstrain(database); }
        catch (IllegalStateException expected) { rejected = true; }

        assertTrue("Migration must reject duplicate invoice numbers", rejected);
        assertEquals(2, scalar("SELECT COUNT(*) FROM invoices WHERE no='DUP-1'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='ux_invoices_no'"));
    }

    @Test public void rejectsConflictingPricesInsteadOfDiscardingEitherPrice() {
        database.execSQL("INSERT INTO items(id,name,qty,cost,sale) VALUES(1,'حليب',2,10,15)");
        database.execSQL("INSERT INTO items(id,name,qty,cost,sale) VALUES(2,' حليب ',3,10,15)");
        database.execSQL("INSERT INTO product_prices(item_id,price_type,price,unit) VALUES(1,'sale',15,'piece')");
        database.execSQL("INSERT INTO product_prices(item_id,price_type,price,unit) VALUES(2,'sale',18,'piece')");

        boolean rejected = false;
        try { helper.normalizeAndConstrain(database); }
        catch (IllegalStateException expected) { rejected = true; }

        assertTrue("Migration must reject conflicting item prices", rejected);
        assertEquals(2, scalar("SELECT COUNT(*) FROM product_prices WHERE price_type='sale'"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM items"));
    }

    private String stringValue(String sql) {
        Cursor c = database.rawQuery(sql, null);
        try { assertTrue(c.moveToFirst()); return c.getString(0); }
        finally { c.close(); }
    }

    private double doubleValue(String sql) {
        Cursor c = database.rawQuery(sql, null);
        try { assertTrue(c.moveToFirst()); return c.getDouble(0); }
        finally { c.close(); }
    }
}
