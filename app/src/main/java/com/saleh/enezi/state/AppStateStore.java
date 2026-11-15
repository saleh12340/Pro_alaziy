package com.saleh.enezi.state;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * مدير الحالة الموحد (State Management + Favorites + Activity Log + Smart Alerts + Auto-Save)
 * خالٍ تماماً من أي مكتبات مدفوعة أو سحابية؛ يعمل محلياً دون اتصال 100%.
 */
public final class AppStateStore {
    public enum UIState { LOADING, CONTENT, EMPTY, ERROR }
    public enum ViewMode { LIST, GRID }
    public enum SortOrder { DEFAULT, NAME_ASC, VALUE_DESC, VALUE_ASC, RECENT }

    private static final String PREFS = "alazzi_state_store";
    private static final String KEY_FAVS = "favorites_set";
    private static final String KEY_ACTIVITY = "activity_log_json";
    private static final String KEY_LAST_SCREEN = "last_screen";
    private static final int MAX_ACTIVITY_ITEMS = 80;

    private AppStateStore() {}

    public static class ActivityEntry {
        public final String category;
        public final String title;
        public final String detail;
        public final String time;
        public final String icon;

        public ActivityEntry(String category, String title, String detail, String time, String icon) {
            this.category = category;
            this.title = title;
            this.detail = detail;
            this.time = time;
            this.icon = icon;
        }
    }

    public static class AlertItem {
        public final String title;
        public final String subtitle;
        public final String actionKey;
        public final boolean urgent;

        public AlertItem(String title, String subtitle, String actionKey, boolean urgent) {
            this.title = title;
            this.subtitle = subtitle;
            this.actionKey = actionKey;
            this.urgent = urgent;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // Favorites
    public static boolean isFavorite(Context c, String key) {
        if (c == null || key == null) return false;
        Set<String> set = prefs(c).getStringSet(KEY_FAVS, Collections.emptySet());
        return set.contains(key);
    }

    public static boolean toggleFavorite(Context c, String key) {
        if (c == null || key == null) return false;
        Set<String> cur = new HashSet<>(prefs(c).getStringSet(KEY_FAVS, Collections.emptySet()));
        boolean added;
        if (cur.contains(key)) {
            cur.remove(key);
            added = false;
        } else {
            cur.add(key);
            added = true;
        }
        prefs(c).edit().putStringSet(KEY_FAVS, cur).apply();
        return added;
    }

    // View Mode & Sort Order per Screen
    public static ViewMode getViewMode(Context c, String screenKey) {
        if (c == null) return ViewMode.LIST;
        String v = prefs(c).getString("view_" + screenKey, ViewMode.LIST.name());
        try { return ViewMode.valueOf(v); } catch (Exception e) { return ViewMode.LIST; }
    }

    public static void setViewMode(Context c, String screenKey, ViewMode mode) {
        if (c == null) return;
        prefs(c).edit().putString("view_" + screenKey, mode.name()).apply();
    }

    public static SortOrder getSortOrder(Context c, String screenKey) {
        if (c == null) return SortOrder.DEFAULT;
        String v = prefs(c).getString("sort_" + screenKey, SortOrder.DEFAULT.name());
        try { return SortOrder.valueOf(v); } catch (Exception e) { return SortOrder.DEFAULT; }
    }

    public static void setSortOrder(Context c, String screenKey, SortOrder order) {
        if (c == null) return;
        prefs(c).edit().putString("sort_" + screenKey, order.name()).apply();
    }

    public static boolean isFavOnly(Context c, String screenKey) {
        return c != null && prefs(c).getBoolean("fav_only_" + screenKey, false);
    }

    public static void setFavOnly(Context c, String screenKey, boolean favOnly) {
        if (c != null) prefs(c).edit().putBoolean("fav_only_" + screenKey, favOnly).apply();
    }

    // Last screen auto-save/restore
    public static void saveLastScreen(Context c, String screenName) {
        if (c != null && screenName != null && !screenName.isEmpty()) {
            prefs(c).edit().putString(KEY_LAST_SCREEN, screenName).apply();
        }
    }

    public static String getLastScreen(Context c) {
        return c == null ? "الرئيسية" : prefs(c).getString(KEY_LAST_SCREEN, "الرئيسية");
    }

    // Activity Log
    public static void logActivity(Context c, String category, String title, String detail, String icon) {
        if (c == null) return;
        try {
            String raw = prefs(c).getString(KEY_ACTIVITY, "[]");
            JSONArray arr = new JSONArray(raw);
            JSONObject obj = new JSONObject();
            obj.put("cat", category == null ? "عام" : category);
            obj.put("title", title == null ? "" : title);
            obj.put("detail", detail == null ? "" : detail);
            obj.put("icon", icon == null ? "📌" : icon);
            obj.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));

            JSONArray next = new JSONArray();
            next.put(obj);
            for (int i = 0; i < arr.length() && i < MAX_ACTIVITY_ITEMS - 1; i++) {
                next.put(arr.get(i));
            }
            prefs(c).edit().putString(KEY_ACTIVITY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<ActivityEntry> getActivityLog(Context c) {
        List<ActivityEntry> list = new ArrayList<>();
        if (c == null) return list;
        try {
            String raw = prefs(c).getString(KEY_ACTIVITY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new ActivityEntry(
                        o.optString("cat", "عام"),
                        o.optString("title", ""),
                        o.optString("detail", ""),
                        o.optString("time", ""),
                        o.optString("icon", "📌")
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void clearActivityLog(Context c) {
        if (c != null) prefs(c).edit().putString(KEY_ACTIVITY, "[]").apply();
    }

    // Real-time offline smart alerts computed directly from SQLite
    public static List<AlertItem> buildSmartAlerts(Context c, SQLiteDatabase db) {
        List<AlertItem> alerts = new ArrayList<>();
        if (db == null) return alerts;
        Cursor cur = null;
        try {
            cur = db.rawQuery("SELECT COUNT(*) FROM items WHERE COALESCE(min_qty,0)>0 AND COALESCE(qty,0)<=COALESCE(min_qty,0)", null);
            if (cur.moveToFirst()) {
                int low = cur.getInt(0);
                if (low > 0) {
                    alerts.add(new AlertItem("تنبيه نقص المخزون (" + low + " أصناف)", "اضغط لعرض قائمة النواقص وتجهيز الطلبية", "inventory", true));
                }
            }
            cur.close();

            cur = db.rawQuery("SELECT COUNT(*) FROM transfers WHERE COALESCE(review,0)=1", null);
            if (cur.moveToFirst()) {
                int pending = cur.getInt(0);
                if (pending > 0) {
                    alerts.add(new AlertItem("حوالات قيد المراجعة (" + pending + ")", "توجد حوالات تحتاج لتأكيد التسليم أو المراجعة", "transfers", false));
                }
            }
            cur.close();

            // Draft invoice check
            if (c != null) {
                String draft = c.getSharedPreferences("draft", Context.MODE_PRIVATE).getString("invoice", "");
                if (!draft.isEmpty()) {
                    alerts.add(new AlertItem("مسودة فاتورة محفوظة تلقائياً", "يمكنك استكمال الفاتورة من حيث توقفت", "invoice", false));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null && !cur.isClosed()) cur.close();
        }
        return alerts;
    }
}
