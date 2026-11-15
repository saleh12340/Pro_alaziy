package com.saleh.enezi.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure Java Navigation & Back-Stack History Manager.
 * Stores typed routes, scalar arguments, and ScrollView Y offsets without depending on Android framework APIs
 * so back-stack behavior and serialization can be unit tested directly on the JVM.
 */
public final class NavHistoryManager {

    public static final String ROUTE_HOME = "home";
    public static final String ROUTE_DASHBOARD = "dashboard";
    public static final String ROUTE_CUSTOMERS = "customers";
    public static final String ROUTE_ACCOUNT = "account";
    public static final String ROUTE_SUPPLIERS = "suppliers";
    public static final String ROUTE_SUPPLIER_ACCOUNT = "supplier_account";
    public static final String ROUTE_UNIFIED_INVOICE = "unified_invoice";
    public static final String ROUTE_INVOICE_FORM = "invoice_form";
    public static final String ROUTE_INVOICE_HISTORY = "invoice_history";
    public static final String ROUTE_PURCHASE_FORM = "purchase_form";
    public static final String ROUTE_QUICK_ADD_HUB = "quick_add_hub";
    public static final String ROUTE_INVENTORY = "inventory";
    public static final String ROUTE_REPORTS = "reports";
    public static final String ROUTE_SALE_TYPE_REPORT = "sale_type_report";
    public static final String ROUTE_TRANSFERS = "transfers";
    public static final String ROUTE_NOTES = "notes";
    public static final String ROUTE_NOTES_HISTORY = "notes_history";
    public static final String ROUTE_NOTES_SEARCH = "notes_search";
    public static final String ROUTE_SCANNER = "scanner";
    public static final String ROUTE_SETTINGS = "settings";
    public static final String ROUTE_ACTIVITY_LOG = "activity_log";

    public static final int MAX_STACK_DEPTH = 25;

    public static final class NavState {
        public final String route;
        public final long longId;
        public final String strArg1;
        public final String strArg2;
        public final boolean boolArg;
        public int scrollY;

        public NavState(String route, long longId, String strArg1, String strArg2, boolean boolArg, int scrollY) {
            this.route = route == null || route.trim().isEmpty() ? ROUTE_HOME : route.trim();
            this.longId = longId;
            this.strArg1 = strArg1 == null ? "" : strArg1;
            this.strArg2 = strArg2 == null ? "" : strArg2;
            this.boolArg = boolArg;
            this.scrollY = Math.max(0, scrollY);
        }

        public static NavState home() {
            return new NavState(ROUTE_HOME, -1L, "", "", false, 0);
        }

        public static NavState simple(String route) {
            return new NavState(route, -1L, "", "", false, 0);
        }

        public static NavState account(long customerId, String customerName) {
            return new NavState(ROUTE_ACCOUNT, customerId, customerName, "", false, 0);
        }

        public static NavState supplierAccount(long supplierId, String name, String phone) {
            return new NavState(ROUTE_SUPPLIER_ACCOUNT, supplierId, name, phone, false, 0);
        }

        public static NavState unifiedInvoice(boolean isSale) {
            return new NavState(ROUTE_UNIFIED_INVOICE, -1L, "", "", isSale, 0);
        }

        public static NavState invoiceForm(boolean edit, long invoiceId) {
            return new NavState(ROUTE_INVOICE_FORM, invoiceId, "", "", edit, 0);
        }

        public static NavState purchaseForm(boolean edit, long purchaseId) {
            return new NavState(ROUTE_PURCHASE_FORM, purchaseId, "", "", edit, 0);
        }

        public static NavState saleTypeReport(boolean cashOnly) {
            return new NavState(ROUTE_SALE_TYPE_REPORT, -1L, "", "", cashOnly, 0);
        }

        public static NavState notesSearch(String query) {
            return new NavState(ROUTE_NOTES_SEARCH, -1L, query, "", false, 0);
        }

        public boolean isHome() {
            return ROUTE_HOME.equals(route);
        }

        public boolean sameTarget(NavState other) {
            if (other == null) return false;
            return route.equals(other.route)
                    && longId == other.longId
                    && boolArg == other.boolArg
                    && strArg1.equals(other.strArg1)
                    && strArg2.equals(other.strArg2);
        }

        public NavState copyWithScroll(int newScrollY) {
            return new NavState(route, longId, strArg1, strArg2, boolArg, newScrollY);
        }

        String encodeLine() {
            return encode(route) + "|" + longId + "|" + encode(strArg1) + "|" + encode(strArg2)
                    + "|" + (boolArg ? "1" : "0") + "|" + Math.max(0, scrollY);
        }

        static NavState decodeLine(String line) {
            if (line == null || line.trim().isEmpty()) return null;
            String[] parts = line.split("\\|", -1);
            if (parts.length < 6) return null;
            String route = decode(parts[0]);
            long id = -1L;
            try { id = Long.parseLong(parts[1]); } catch (Exception ignored) {}
            String a1 = decode(parts[2]);
            String a2 = decode(parts[3]);
            boolean b = "1".equals(parts[4]);
            int sy = 0;
            try { sy = Math.max(0, Integer.parseInt(parts[5])); } catch (Exception ignored) {}
            return new NavState(route, id, a1, a2, b, sy);
        }
    }

    private final List<NavState> backStack = new ArrayList<>();
    private NavState currentState = NavState.home();

    public NavState getCurrentState() {
        return currentState;
    }

    public List<NavState> getBackStackSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(backStack));
    }

    public int stackSize() {
        return backStack.size();
    }

    public boolean canPopBack() {
        return !backStack.isEmpty();
    }

    public void resetToHome() {
        backStack.clear();
        currentState = NavState.home();
    }

    public void updateCurrentScrollY(int scrollY) {
        if (currentState != null) {
            currentState.scrollY = Math.max(0, scrollY);
        }
    }

    /**
     * Records a transition into {@code next}.
     * - Entering Home clears the back stack (root destination).
     * - Restoring from Back or configuration refresh updates {@code currentState} without pushing a new frame.
     * - Re-entering the exact same route+params refreshes currentState in place instead of creating duplicate entries.
     */
    public void onEnterScreen(NavState next, int previousScrollY, boolean restoringFromHistory) {
        if (next == null) return;
        if (next.isHome()) {
            resetToHome();
            return;
        }
        if (restoringFromHistory) {
            currentState = next.copyWithScroll(next.scrollY);
            return;
        }
        if (currentState != null && currentState.sameTarget(next)) {
            int keepScroll = next.scrollY > 0 ? next.scrollY : Math.max(0, previousScrollY);
            currentState = next.copyWithScroll(keepScroll);
            return;
        }
        if (currentState != null) {
            NavState snapshot = currentState.copyWithScroll(Math.max(0, previousScrollY));
            if (backStack.isEmpty() || !backStack.get(backStack.size() - 1).sameTarget(snapshot)) {
                backStack.add(snapshot);
                while (backStack.size() > MAX_STACK_DEPTH) {
                    backStack.remove(0);
                }
            }
        }
        currentState = next.copyWithScroll(next.scrollY);
    }

    /**
     * Pops the previous screen from the back stack, or returns null when the stack is empty.
     */
    public NavState popBack() {
        if (backStack.isEmpty()) {
            return null;
        }
        NavState prev = backStack.remove(backStack.size() - 1);
        currentState = prev;
        return prev;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("C:").append(currentState != null ? currentState.encodeLine() : NavState.home().encodeLine());
        for (NavState s : backStack) {
            if (s != null) {
                sb.append("\nS:").append(s.encodeLine());
            }
        }
        return sb.toString();
    }

    public boolean restoreFromSerialized(String data) {
        if (data == null || data.trim().isEmpty()) return false;
        NavState restoredCurrent = null;
        List<NavState> restoredStack = new ArrayList<>();
        String[] lines = data.split("\n");
        for (String raw : lines) {
            if (raw.startsWith("C:")) {
                restoredCurrent = NavState.decodeLine(raw.substring(2));
            } else if (raw.startsWith("S:")) {
                NavState item = NavState.decodeLine(raw.substring(2));
                if (item != null) restoredStack.add(item);
            }
        }
        if (restoredCurrent == null) return false;
        backStack.clear();
        while (restoredStack.size() > MAX_STACK_DEPTH) {
            restoredStack.remove(0);
        }
        backStack.addAll(restoredStack);
        currentState = restoredCurrent;
        return true;
    }

    private static String encode(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '%') out.append("%25");
            else if (ch == '|') out.append("%7C");
            else if (ch == '\n') out.append("%0A");
            else if (ch == '\r') out.append("%0D");
            else out.append(ch);
        }
        return out.toString();
    }

    private static String decode(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '%' && i + 2 < s.length()) {
                String hex = s.substring(i + 1, i + 3);
                if ("25".equals(hex)) { out.append('%'); i += 2; continue; }
                if ("7C".equalsIgnoreCase(hex)) { out.append('|'); i += 2; continue; }
                if ("0A".equalsIgnoreCase(hex)) { out.append('\n'); i += 2; continue; }
                if ("0D".equalsIgnoreCase(hex)) { out.append('\r'); i += 2; continue; }
            }
            out.append(ch);
        }
        return out.toString();
    }
}
