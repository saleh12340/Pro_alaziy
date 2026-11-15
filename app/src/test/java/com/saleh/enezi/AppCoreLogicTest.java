package com.saleh.enezi;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.saleh.enezi.print.PrintEngine;
import com.saleh.enezi.share.ShareEngine;
import com.saleh.enezi.state.AppStateStore;
import com.saleh.enezi.state.NavHistoryManager;
import com.saleh.enezi.state.NavHistoryManager.NavState;
import com.saleh.enezi.ui.DesignSystem;

import org.junit.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure JVM unit tests for the upgraded PrintEngine, ShareEngine, AppStateStore,
 * NavHistoryManager, DesignSystem, NumericInput, formatting helpers, and ZXing QR generation.
 */
public class AppCoreLogicTest {

    @Test
    public void testPaperSizesAndThermalFlags() {
        assertTrue(PrintEngine.PaperSize.THERMAL_58MM.isThermal());
        assertEquals(384, PrintEngine.PaperSize.THERMAL_58MM.thermalWidthPx);

        assertTrue(PrintEngine.PaperSize.THERMAL_80MM.isThermal());
        assertEquals(576, PrintEngine.PaperSize.THERMAL_80MM.thermalWidthPx);

        assertFalse(PrintEngine.PaperSize.A4.isThermal());
        assertEquals(595, PrintEngine.PaperSize.A4.pdfWidthPt);
        assertEquals(842, PrintEngine.PaperSize.A4.pdfHeightPt);

        assertFalse(PrintEngine.PaperSize.A5.isThermal());
        assertFalse(PrintEngine.PaperSize.LETTER.isThermal());
    }

    @Test
    public void testPrintTemplateConfigDefaultsPreventReversePrinting() {
        PrintEngine.PrintTemplateConfig cfg = PrintEngine.loadConfig(null);
        assertNotNull(cfg);
        assertFalse("flip180 should default to false for standard forward thermal feed", cfg.flip180);
        assertFalse("mirrorHorizontal should default to false", cfg.mirrorHorizontal);
        assertEquals(PrintEngine.PaperSize.THERMAL_58MM, cfg.paperSize);
        assertTrue(cfg.showLogo);
        assertTrue(cfg.showQrCode);
        assertTrue(cfg.showPageNumbers);
        assertTrue(cfg.showDateTime);
        assertEquals(180, cfg.darknessThreshold);
    }

    @Test
    public void testPrintEngineRtlTableColumnsAndBidiIsolation() {
        float[] cols3 = PrintEngine.computeColumnWidths(3, 360f);
        assertEquals(3, cols3.length);
        assertEquals(360f, cols3[0] + cols3[1] + cols3[2], 0.01f);
        assertTrue("Primary item column should be widest", cols3[0] > cols3[2] && cols3[2] > cols3[1]);

        float[] cols4 = PrintEngine.computeColumnWidths(4, 500f);
        assertEquals(4, cols4.length);
        assertEquals(500f, cols4[0] + cols4[1] + cols4[2] + cols4[3], 0.01f);

        assertEquals("\u20661,500.50\u2069", PrintEngine.formatCellRtlSafe("1,500.50"));
        assertEquals("\u20662026-11-15 14:30\u2069", PrintEngine.formatCellRtlSafe("2026-11-15 14:30"));
        assertEquals("أرز بسمتي", PrintEngine.formatCellRtlSafe("أرز بسمتي"));
        assertEquals("", PrintEngine.formatCellRtlSafe(null));
    }

    @Test
    public void testWhatsAppPhoneNormalizationYemenFormats() {
        assertEquals("967776425052", ShareEngine.normalizeWhatsAppPhone("776425052"));
        assertEquals("967776425052", ShareEngine.normalizeWhatsAppPhone("0776425052"));
        assertEquals("967776425052", ShareEngine.normalizeWhatsAppPhone("+967 776-425-052"));
        assertEquals("967776425052", ShareEngine.normalizeWhatsAppPhone("00967776425052"));
        assertEquals("", ShareEngine.normalizeWhatsAppPhone(null));
    }

    @Test
    public void testAppStateStoreAndLockedLightDesignSystem() {
        assertEquals(4, AppStateStore.UIState.values().length);
        assertEquals(2, AppStateStore.ViewMode.values().length);
        assertEquals(5, AppStateStore.SortOrder.values().length);
        assertEquals(3, DesignSystem.WindowSizeClass.values().length);
        assertEquals(0, DesignSystem.THEME_AUTO);
        assertEquals(1, DesignSystem.THEME_LIGHT);
        assertEquals(2, DesignSystem.THEME_DARK);

        assertEquals("Theme must stay locked to Light Mode", DesignSystem.THEME_LIGHT, DesignSystem.getThemeMode(null));
        assertFalse("isDark must remain false", DesignSystem.isDark(null));
        assertFalse("Navigation rail must remain disabled", DesignSystem.useNavigationRail(null));
    }

    @Test
    public void testNumericInputArabicAndFormattedNumbers() {
        assertEquals(1500.0, NumericInput.parseDouble("1,500", -1), 0.001);
        assertEquals(125.5, NumericInput.parseDouble("١٢٥٫٥٠", -1), 0.001);
        assertEquals(42.0, NumericInput.parseDouble("  ٤٢  ", -1), 0.001);
        assertEquals(-1.0, NumericInput.parseDouble("abc", -1), 0.001);
        assertEquals(-1.0, NumericInput.parseDouble("", -1), 0.001);
    }

    @Test
    public void testNavHistoryBackStackScrollMemoryAndSerialization() {
        NavHistoryManager nav = new NavHistoryManager();
        assertTrue(nav.getCurrentState().isHome());
        assertFalse(nav.canPopBack());
        assertNull(nav.popBack());

        // home(scroll 320) -> customers(scroll 640) -> account(scroll 180) -> unifiedInvoice
        nav.onEnterScreen(NavState.simple(NavHistoryManager.ROUTE_CUSTOMERS), 320, false);
        // Same target refresh should NOT push duplicate entry
        nav.onEnterScreen(NavState.simple(NavHistoryManager.ROUTE_CUSTOMERS), 50, false);
        assertEquals(1, nav.stackSize());

        nav.onEnterScreen(NavState.account(7L, "سعيد | العزي%100"), 640, false);
        nav.onEnterScreen(NavState.unifiedInvoice(true), 180, false);
        assertEquals(3, nav.stackSize());
        assertEquals(NavHistoryManager.ROUTE_UNIFIED_INVOICE, nav.getCurrentState().route);

        // Serialize & restore after configuration change / process state restore
        String encoded = nav.serialize();
        NavHistoryManager restored = new NavHistoryManager();
        assertTrue(restored.restoreFromSerialized(encoded));
        assertEquals(NavHistoryManager.ROUTE_UNIFIED_INVOICE, restored.getCurrentState().route);
        assertTrue(restored.getCurrentState().boolArg);
        assertEquals(3, restored.stackSize());

        // Pop chain preserves scrollY and special characters
        NavState back1 = restored.popBack();
        assertNotNull(back1);
        assertEquals(NavHistoryManager.ROUTE_ACCOUNT, back1.route);
        assertEquals(7L, back1.longId);
        assertEquals("سعيد | العزي%100", back1.strArg1);
        assertEquals(180, back1.scrollY);

        NavState back2 = restored.popBack();
        assertNotNull(back2);
        assertEquals(NavHistoryManager.ROUTE_CUSTOMERS, back2.route);
        assertEquals(640, back2.scrollY);

        NavState back3 = restored.popBack();
        assertNotNull(back3);
        assertTrue(back3.isHome());
        assertEquals(320, back3.scrollY);

        assertNull(restored.popBack());
    }

    @Test
    public void testNavHistoryMaxDepthCapAndDeepLinkDirectOpen() {
        NavHistoryManager nav = new NavHistoryManager();
        for (int i = 1; i <= 35; i++) {
            nav.onEnterScreen(NavState.account(i, "عميل " + i), i * 10, false);
        }
        List<NavState> stack = nav.getBackStackSnapshot();
        assertEquals("Back stack should stay bounded at MAX_STACK_DEPTH", NavHistoryManager.MAX_STACK_DEPTH, stack.size());

        // Deep-link restoration where stack is empty -> popBack returns null
        NavHistoryManager deepLinkNav = new NavHistoryManager();
        deepLinkNav.onEnterScreen(NavState.account(99L, "عميل مباشر"), 0, true);
        assertEquals(0, deepLinkNav.stackSize());
        assertNull(deepLinkNav.popBack());
    }

    @Test
    public void testCurrencyFormattingHelper() {
        assertEquals("1,500", MainActivity.fmt(1500.0));
        assertEquals("250.50", MainActivity.fmt(250.5));
        assertEquals("0", MainActivity.fmt(0.0));
    }

    @Test
    public void testZxingFreeOfflineQrGeneration() throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode("alazzi://invoice?no=1118", BarcodeFormat.QR_CODE, 200, 200, hints);
        assertNotNull(matrix);
        assertEquals(200, matrix.getWidth());
        assertEquals(200, matrix.getHeight());
    }

    @Test
    public void testUnifiedInvoiceItemModelAndDerivedUnitPrice() {
        double total = 1500.0;
        double qty = 5.0;
        double derivedUnit = total / qty;
        MainActivity.UnifiedInvoiceItem item = new MainActivity.UnifiedInvoiceItem("أرز الشعلان 10ك", qty, total, derivedUnit, 320.0);
        assertEquals("أرز الشعلان 10ك", item.name);
        assertEquals(5.0, item.qty, 0.001);
        assertEquals(1500.0, item.total, 0.001);
        assertEquals(300.0, item.unitPrice, 0.001);
        assertEquals(320.0, item.salePrice, 0.001);
    }

    @Test
    public void testRowAutoSpawnLogicStartsAtTwo() {
        // Simulating row list: starts at 2
        java.util.List<String[]> simulatedRows = new java.util.ArrayList<>();
        simulatedRows.add(new String[]{"", ""}); // Row 0 empty
        simulatedRows.add(new String[]{"", ""}); // Row 1 empty
        assertEquals(2, simulatedRows.size());

        // Filling Row 0: Last row (Row 1) is still empty, so do not spawn
        simulatedRows.set(0, new String[]{"سكر", "500"});
        String[] lastRow = simulatedRows.get(simulatedRows.size() - 1);
        boolean shouldSpawn = !lastRow[0].isEmpty() || !lastRow[1].isEmpty();
        assertFalse("Should not spawn when last row is still empty", shouldSpawn);

        // Filling Row 1: Now both initial rows are filled -> should spawn Row 2
        simulatedRows.set(1, new String[]{"زيت", "300"});
        lastRow = simulatedRows.get(simulatedRows.size() - 1);
        shouldSpawn = !lastRow[0].isEmpty() || !lastRow[1].isEmpty();
        assertTrue("Should auto-spawn new row when both initial rows are filled", shouldSpawn);

        if (shouldSpawn) {
            simulatedRows.add(new String[]{"", ""});
        }
        assertEquals(3, simulatedRows.size());
    }
}
