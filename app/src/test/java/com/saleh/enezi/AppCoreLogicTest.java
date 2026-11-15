package com.saleh.enezi;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.saleh.enezi.print.PrintEngine;
import com.saleh.enezi.share.ShareEngine;
import com.saleh.enezi.state.AppStateStore;
import com.saleh.enezi.ui.DesignSystem;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure JVM unit tests for the upgraded PrintEngine, ShareEngine, AppStateStore,
 * DesignSystem, formatting helpers, and ZXing QR generation.
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
    public void testWhatsAppPhoneNormalizationYemenFormats() {
        assertEquals("967776425052", ShareEngine.normalizeWhatsAppPhone("776425052"));
        assertEquals("967776425052", ShareEngine.normalizeWhatsAppPhone("0776425052"));
        assertEquals("967776425052", ShareEngine.normalizeWhatsAppPhone("+967 776-425-052"));
        assertEquals("967776425052", ShareEngine.normalizeWhatsAppPhone("00967776425052"));
        assertEquals("", ShareEngine.normalizeWhatsAppPhone(null));
    }

    @Test
    public void testAppStateStoreAndDesignSystemEnums() {
        assertEquals(4, AppStateStore.UIState.values().length);
        assertEquals(2, AppStateStore.ViewMode.values().length);
        assertEquals(5, AppStateStore.SortOrder.values().length);
        assertEquals(3, DesignSystem.WindowSizeClass.values().length);
        assertEquals(0, DesignSystem.THEME_AUTO);
        assertEquals(1, DesignSystem.THEME_LIGHT);
        assertEquals(2, DesignSystem.THEME_DARK);
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
}
