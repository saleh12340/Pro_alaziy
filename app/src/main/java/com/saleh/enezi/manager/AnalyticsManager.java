package com.saleh.enezi.manager;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public final class AnalyticsManager {

    private AnalyticsManager() {}

    public static class DailySalesData {
        public String date;
        public String label;
        public double totalSales;
        public double cashSales;
        public double creditSales;
        public int invoiceCount;

        public DailySalesData(String date, String label, double totalSales, double cashSales, double creditSales, int invoiceCount) {
            this.date = date;
            this.label = label;
            this.totalSales = totalSales;
            this.cashSales = cashSales;
            this.creditSales = creditSales;
            this.invoiceCount = invoiceCount;
        }
    }

    public static class InventoryStockData {
        public long itemId;
        public String itemName;
        public double quantity;
        public double minQuantity;
        public double unitCost;
        public double salePrice;
        public double valuation;
        public int status; // 0=Good, 1=Low, 2=Empty

        public InventoryStockData(long itemId, String itemName, double quantity, double minQuantity, double unitCost, double salePrice) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.quantity = quantity;
            this.minQuantity = minQuantity;
            this.unitCost = unitCost;
            this.salePrice = salePrice;
            this.valuation = Math.max(0, quantity) * (unitCost > 0 ? unitCost : salePrice);
            if (quantity <= 0) {
                this.status = 2; // Empty
            } else if (minQuantity > 0 && quantity <= minQuantity) {
                this.status = 1; // Low
            } else {
                this.status = 0; // Good
            }
        }
    }

    public static class TopSellingItem {
        public String name;
        public double totalQty;
        public double totalRevenue;

        public TopSellingItem(String name, double totalQty, double totalRevenue) {
            this.name = name;
            this.totalQty = totalQty;
            this.totalRevenue = totalRevenue;
        }
    }

    public static class DashboardKPIs {
        public double todaySales;
        public double monthSales;
        public double totalCredits; // لك
        public double totalDebts;   // عليك
        public double stockValuation;
        public int lowStockCount;
        public int totalItemsCount;
        public double estimatedProfit;

        public DashboardKPIs(double todaySales, double monthSales, double totalCredits, double totalDebts, double stockValuation, int lowStockCount, int totalItemsCount, double estimatedProfit) {
            this.todaySales = todaySales;
            this.monthSales = monthSales;
            this.totalCredits = totalCredits;
            this.totalDebts = totalDebts;
            this.stockValuation = stockValuation;
            this.lowStockCount = lowStockCount;
            this.totalItemsCount = totalItemsCount;
            this.estimatedProfit = estimatedProfit;
        }
    }

    public static List<DailySalesData> getDailySalesTrends(SQLiteDatabase db, int daysCount) {
        List<DailySalesData> list = new ArrayList<>();
        if (db == null) return list;

        SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat labelFmt = new SimpleDateFormat("dd/MM", Locale.US);

        Calendar cal = Calendar.getInstance();
        List<String> dates = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = daysCount - 1; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_MONTH, -i);
            dates.add(isoFmt.format(c.getTime()));
            labels.add(labelFmt.format(c.getTime()));
        }

        HashMap<String, double[]> map = new HashMap<>(); // [total, cash, credit, count]

        Cursor c = null;
        try {
            c = db.rawQuery("SELECT date, COALESCE(total,0), COALESCE(paid,0), COALESCE(sale_type,'credit') FROM invoices", null);
            while (c.moveToNext()) {
                String rawDate = c.getString(0);
                if (rawDate == null || rawDate.length() < 10) continue;
                String datePart = rawDate.substring(0, 10);
                double total = c.getDouble(1);
                double paid = c.getDouble(2);
                String saleType = c.getString(3);

                double cash = "cash".equalsIgnoreCase(saleType) || paid >= total ? total : paid;
                double credit = Math.max(0, total - cash);

                double[] vals = map.get(datePart);
                if (vals == null) {
                    vals = new double[4];
                    map.put(datePart, vals);
                }
                vals[0] += total;
                vals[1] += cash;
                vals[2] += credit;
                vals[3] += 1;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }

        for (int i = 0; i < dates.size(); i++) {
            String d = dates.get(i);
            String l = labels.get(i);
            double[] v = map.get(d);
            if (v == null) {
                list.add(new DailySalesData(d, l, 0, 0, 0, 0));
            } else {
                list.add(new DailySalesData(d, l, v[0], v[1], v[2], (int) v[3]));
            }
        }

        return list;
    }

    public static List<InventoryStockData> getInventoryStockLevels(SQLiteDatabase db, int limit) {
        List<InventoryStockData> list = new ArrayList<>();
        if (db == null) return list;

        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id, name, COALESCE(qty,0), COALESCE(min_qty,0), COALESCE(cost,0), COALESCE(sale,0) FROM items ORDER BY qty ASC LIMIT ?",
                    new String[]{String.valueOf(limit)});
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                double qty = c.getDouble(2);
                double minQty = c.getDouble(3);
                double cost = c.getDouble(4);
                double sale = c.getDouble(5);

                list.add(new InventoryStockData(id, name, qty, minQty, cost, sale));
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }

        return list;
    }

    public static List<TopSellingItem> getTopSellingItems(SQLiteDatabase db, int limit) {
        List<TopSellingItem> list = new ArrayList<>();
        if (db == null) return list;

        Cursor c = null;
        try {
            c = db.rawQuery("SELECT name, SUM(qty) as total_qty, SUM(total) as total_rev FROM invoice_items GROUP BY lower(trim(name)) ORDER BY total_rev DESC LIMIT ?",
                    new String[]{String.valueOf(limit)});
            while (c.moveToNext()) {
                String name = c.getString(0);
                double qty = c.getDouble(1);
                double rev = c.getDouble(2);
                list.add(new TopSellingItem(name, qty, rev));
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }

        return list;
    }

    public static DashboardKPIs calculateKPIs(SQLiteDatabase db) {
        if (db == null) return new DashboardKPIs(0, 0, 0, 0, 0, 0, 0, 0);

        double todaySales = 0;
        double monthSales = 0;
        double totalCredits = 0;
        double totalDebts = 0;
        double stockValuation = 0;
        int lowStockCount = 0;
        int totalItemsCount = 0;
        double estimatedProfit = 0;

        SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy-MM", Locale.US);
        Date now = new Date();
        String todayStr = isoFmt.format(now);
        String monthStr = monthFmt.format(now);

        Cursor c = null;
        try {
            // Sales today and month
            c = db.rawQuery("SELECT date, COALESCE(total,0) FROM invoices", null);
            while (c.moveToNext()) {
                String d = c.getString(0);
                double tot = c.getDouble(1);
                if (d != null) {
                    if (d.startsWith(todayStr)) todaySales += tot;
                    if (d.startsWith(monthStr)) monthSales += tot;
                }
            }
            c.close();

            // Customer Credits (لك - ديون على العملاء)
            c = db.rawQuery("SELECT id FROM customers", null);
            while (c.moveToNext()) {
                long cid = c.getLong(0);
                Cursor tc = db.rawQuery("SELECT type, COALESCE(amount,0) FROM transactions WHERE customer_id=?", new String[]{String.valueOf(cid)});
                double deb = 0, cred = 0; // type 0=دفعة(له), 1=دين(عليه)
                while (tc.moveToNext()) {
                    int type = tc.getInt(0);
                    double amt = tc.getDouble(1);
                    if (type == 1) deb += amt;
                    else cred += amt;
                }
                tc.close();
                double net = deb - cred;
                if (net > 0) totalCredits += net;
            }
            c.close();

            // Supplier Debts (عليك - مستحقات الموردين)
            c = db.rawQuery("SELECT id FROM suppliers", null);
            while (c.moveToNext()) {
                long sid = c.getLong(0);
                Cursor stc = db.rawQuery("SELECT type, COALESCE(amount,0) FROM supplier_transactions WHERE supplier_id=?", new String[]{String.valueOf(sid)});
                double deb = 0, cred = 0;
                while (stc.moveToNext()) {
                    int type = stc.getInt(0);
                    double amt = stc.getDouble(1);
                    if (type == 1) deb += amt; // فاتورة له
                    else cred += amt; // سداد منا
                }
                stc.close();
                double net = deb - cred;
                if (net > 0) totalDebts += net;
            }
            c.close();

            // Stock valuation & low stock count
            c = db.rawQuery("SELECT qty, min_qty, cost, sale FROM items", null);
            while (c.moveToNext()) {
                totalItemsCount++;
                double q = c.getDouble(0);
                double mq = c.getDouble(1);
                double cost = c.getDouble(2);
                double sale = c.getDouble(3);

                if (q > 0) stockValuation += q * (cost > 0 ? cost : sale);
                if (mq > 0 && q <= mq) lowStockCount++;
            }
            c.close();

            // Estimated Gross Profit
            c = db.rawQuery("SELECT qty, total, unit_cost FROM invoice_items", null);
            while (c.moveToNext()) {
                double q = c.getDouble(0);
                double rev = c.getDouble(1);
                double uCost = c.getDouble(2);
                double cogs = q * uCost;
                estimatedProfit += (rev - cogs);
            }
            c.close();

        } catch (Exception ignored) {
        } finally {
            if (c != null && !c.isClosed()) c.close();
        }

        return new DashboardKPIs(todaySales, monthSales, totalCredits, totalDebts, stockValuation, lowStockCount, totalItemsCount, estimatedProfit);
    }
}
