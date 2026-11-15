package com.saleh.enezi.print;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.saleh.enezi.DocumentCenter;
import com.saleh.enezi.R;
import com.saleh.enezi.share.ShareEngine;
import com.saleh.enezi.state.AppStateStore;
import com.saleh.enezi.ui.DesignSystem;
import com.saleh.enezi.ui.PresentationKit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * محرك الطباعة المتقدم (PrintEngine)
 * - قوالب طباعة قابلة للتخصيص (شعار، ترويسة، تذييل، أرقام صفحات، تاريخ/وقت، رمز QR)
 * - دعم أحجام الورق: A4, A5, Letter, Thermal 58mm, Thermal 80mm
 * - توليد ملفات PDF متعددة الصفحات عالية الجودة مع اتجاه RTL سليم 100%
 * - حماية متقدمة من الطباعة المعكوسة أو المقلوبة على الطابعات الحرارية
 * - الطباعة المباشرة (Bluetooth + Android Print Spooler) والطباعة الجماعية (Batch Printing)
 * 100% Free & Open Source - Standard Android SDK.
 */
public final class PrintEngine {

    public enum PaperSize {
        THERMAL_58MM("حراري 58mm", 384, 210, 0),
        THERMAL_80MM("حراري 80mm", 576, 280, 0),
        A4("ورق A4", 595, 595, 842),
        A5("ورق A5", 420, 420, 595),
        LETTER("ورق Letter", 612, 612, 792);

        public final String label;
        public final int thermalWidthPx;
        public final int pdfWidthPt;
        public final int pdfHeightPt;

        PaperSize(String label, int thermalWidthPx, int pdfWidthPt, int pdfHeightPt) {
            this.label = label;
            this.thermalWidthPx = thermalWidthPx;
            this.pdfWidthPt = pdfWidthPt;
            this.pdfHeightPt = pdfHeightPt;
        }

        public boolean isThermal() {
            return this == THERMAL_58MM || this == THERMAL_80MM;
        }
    }

    public static class PrintTemplateConfig {
        public String headerTitle = "بقالة العزي للمواد الغذائية";
        public String headerPhone = "776425052";
        public String headerSubtitle = "نظام المبيعات والحسابات المتكامل";
        public String footerText = "شكراً لتعاملكم معنا • البضاعة المباعة لا ترد إلا بحالتها";
        public boolean showLogo = true;
        public boolean showDateTime = true;
        public boolean showPageNumbers = true;
        public boolean showQrCode = true;
        public PaperSize paperSize = PaperSize.THERMAL_58MM;
        public int darknessThreshold = 180; // 140 light, 180 standard, 210 dark
        public boolean flip180 = false;
        public boolean mirrorHorizontal = false;
    }

    public static class PrintDocumentModel {
        public String docTitle = "مستند";
        public String docSubtitle = "";
        public List<String> metaLines = new ArrayList<>();
        public String[] tableHeaders;
        public List<String[]> tableRows = new ArrayList<>();
        public List<String> summaryLines = new ArrayList<>();
        public String highlightBoxText = "";
        public String qrContent = "";
    }

    private static final String PREFS = "printer_settings";

    private PrintEngine() {}

    public static PrintTemplateConfig loadConfig(Context c) {
        PrintTemplateConfig cfg = new PrintTemplateConfig();
        if (c == null) return cfg;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences app = c.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        cfg.headerTitle = p.getString("tpl_header", app.getString("store_name", "بقالة العزي للمواد الغذائية"));
        cfg.headerPhone = p.getString("tpl_phone", app.getString("store_phone", "776425052"));
        cfg.headerSubtitle = p.getString("tpl_sub", "نظام المبيعات والحسابات المتكامل");
        cfg.footerText = p.getString("tpl_footer", "شكراً لتعاملكم معنا");
        cfg.showLogo = p.getBoolean("tpl_logo", true);
        cfg.showDateTime = p.getBoolean("tpl_datetime", true);
        cfg.showPageNumbers = p.getBoolean("tpl_pages", true);
        cfg.showQrCode = p.getBoolean("tpl_qr", true);
        cfg.darknessThreshold = p.getInt("bt_darkness", 180);
        cfg.flip180 = p.getBoolean("bt_flip_180", false);
        cfg.mirrorHorizontal = p.getBoolean("bt_mirror_h", false);
        try {
            cfg.paperSize = PaperSize.valueOf(p.getString("tpl_paper", PaperSize.THERMAL_58MM.name()));
        } catch (Exception ignored) {
            cfg.paperSize = PaperSize.THERMAL_58MM;
        }
        return cfg;
    }

    public static void saveConfig(Context c, PrintTemplateConfig cfg) {
        if (c == null || cfg == null) return;
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("tpl_header", cfg.headerTitle)
                .putString("tpl_phone", cfg.headerPhone)
                .putString("tpl_sub", cfg.headerSubtitle)
                .putString("tpl_footer", cfg.footerText)
                .putBoolean("tpl_logo", cfg.showLogo)
                .putBoolean("tpl_datetime", cfg.showDateTime)
                .putBoolean("tpl_pages", cfg.showPageNumbers)
                .putBoolean("tpl_qr", cfg.showQrCode)
                .putString("tpl_paper", cfg.paperSize.name())
                .putInt("bt_darkness", cfg.darknessThreshold)
                .putBoolean("bt_flip_180", cfg.flip180)
                .putBoolean("bt_mirror_h", cfg.mirrorHorizontal)
                .apply();
    }

    private static Bitmap loadStoreLogo(Context c, int sizePx) {
        try {
            Drawable d = ContextCompat.getDrawable(c, R.drawable.ic_store);
            if (d == null) return null;
            Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            d.setBounds(0, 0, sizePx, sizePx);
            d.draw(canvas);
            return b;
        } catch (Throwable e) {
            return null;
        }
    }

    public static File createStructuredPdf(Context c, List<PrintDocumentModel> docs, PrintTemplateConfig cfg, String filePrefix) {
        if (c == null || docs == null || docs.isEmpty()) return null;
        if (cfg == null) cfg = loadConfig(c);

        File dir = new File(c.getCacheDir(), "pdf");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, (filePrefix == null ? "doc" : filePrefix) + "_" + System.currentTimeMillis() + ".pdf");

        int pageW = cfg.paperSize.pdfWidthPt;
        int pageH = cfg.paperSize.isThermal() ? 842 : cfg.paperSize.pdfHeightPt;
        int margin = cfg.paperSize.isThermal() ? 14 : 28;
        int contentW = pageW - margin * 2;
        float baseFont = cfg.paperSize.isThermal() ? 9.5f : (cfg.paperSize == PaperSize.A5 ? 10f : 11.5f);

        PdfDocument pdf = new PdfDocument();
        List<PdfDocument.Page> createdPages = new ArrayList<>();
        // First pass writes directly with tracked page numbers; we know exact count or draw footer cleanly
        String nowStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(0.8f);
        stroke.setColor(Color.rgb(203, 213, 225));

        TextPaint tpTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tpTitle.setColor(Color.rgb(27, 59, 139));
        tpTitle.setTextSize(baseFont + 5f);
        tpTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        TextPaint tpBold = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tpBold.setColor(Color.rgb(15, 23, 42));
        tpBold.setTextSize(baseFont);
        tpBold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        TextPaint tpBody = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tpBody.setColor(Color.rgb(30, 41, 59));
        tpBody.setTextSize(baseFont);
        tpBody.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        TextPaint tpMuted = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tpMuted.setColor(Color.rgb(100, 116, 139));
        tpMuted.setTextSize(baseFont - 1.5f);

        Bitmap logo = cfg.showLogo ? loadStoreLogo(c, cfg.paperSize.isThermal() ? 34 : 44) : null;
        int pageNum = 0;

        for (PrintDocumentModel doc : docs) {
            int rowIdx = 0;
            boolean headerDrawnForDoc = false;
            int totalRows = doc.tableRows == null ? 0 : doc.tableRows.size();

            do {
                pageNum++;
                PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create());
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);
                int y = margin;

                // Top Header Banner
                int headerH = cfg.paperSize.isThermal() ? 54 : 66;
                fill.setColor(Color.rgb(248, 250, 252));
                canvas.drawRoundRect(margin, y, pageW - margin, y + headerH, 8, 8, fill);
                canvas.drawRoundRect(margin, y, pageW - margin, y + headerH, 8, 8, stroke);

                if (logo != null) {
                    canvas.drawBitmap(logo, margin + 8, y + (headerH - logo.getHeight()) / 2f, null);
                }

                String hTitle = cfg.headerTitle + (cfg.headerPhone != null && !cfg.headerPhone.isEmpty() ? "  •  " + cfg.headerPhone : "");
                StaticLayout slHead = rtlLayout(hTitle, tpTitle, contentW - (logo != null ? logo.getWidth() + 20 : 16), Layout.Alignment.ALIGN_NORMAL);
                canvas.save();
                canvas.translate(margin + (logo != null ? logo.getWidth() + 14 : 8), y + 8);
                slHead.draw(canvas);
                canvas.restore();

                String subLine = doc.docTitle + (cfg.showDateTime ? "  |  " + nowStr : "");
                StaticLayout slSub = rtlLayout(subLine, tpMuted, contentW - (logo != null ? logo.getWidth() + 20 : 16), Layout.Alignment.ALIGN_NORMAL);
                canvas.save();
                canvas.translate(margin + (logo != null ? logo.getWidth() + 14 : 8), y + 12 + slHead.getHeight());
                slSub.draw(canvas);
                canvas.restore();
                y += headerH + 10;

                if (!headerDrawnForDoc) {
                    headerDrawnForDoc = true;
                    if (doc.docSubtitle != null && !doc.docSubtitle.isEmpty()) {
                        StaticLayout sl = rtlLayout(doc.docSubtitle, tpBold, contentW, Layout.Alignment.ALIGN_NORMAL);
                        canvas.save();
                        canvas.translate(margin, y);
                        sl.draw(canvas);
                        canvas.restore();
                        y += sl.getHeight() + 6;
                    }
                    if (doc.metaLines != null) {
                        for (String m : doc.metaLines) {
                            if (m == null || m.trim().isEmpty()) continue;
                            StaticLayout sl = rtlLayout(m, tpBody, contentW, Layout.Alignment.ALIGN_NORMAL);
                            canvas.save();
                            canvas.translate(margin, y);
                            sl.draw(canvas);
                            canvas.restore();
                            y += sl.getHeight() + 3;
                        }
                        y += 6;
                    }
                    if (doc.highlightBoxText != null && !doc.highlightBoxText.isEmpty()) {
                        StaticLayout slHl = rtlLayout(doc.highlightBoxText, tpBold, contentW - 16, Layout.Alignment.ALIGN_CENTER);
                        int hlH = slHl.getHeight() + 14;
                        fill.setColor(Color.rgb(235, 243, 254));
                        canvas.drawRoundRect(margin, y, pageW - margin, y + hlH, 6, 6, fill);
                        canvas.save();
                        canvas.translate(margin + 8, y + 7);
                        slHl.draw(canvas);
                        canvas.restore();
                        y += hlH + 8;
                    }
                }

                // Table Headers
                int footerReserve = cfg.paperSize.isThermal() ? 80 : 110;
                if (doc.tableHeaders != null && doc.tableHeaders.length > 0 && rowIdx < totalRows) {
                    int cols = doc.tableHeaders.length;
                    int thH = 24;
                    fill.setColor(Color.rgb(27, 59, 139));
                    canvas.drawRoundRect(margin, y, pageW - margin, y + thH, 4, 4, fill);
                    TextPaint tpTh = new TextPaint(tpBold);
                    tpTh.setColor(Color.WHITE);
                    tpTh.setTextSize(baseFont - 0.5f);

                    float colW = (float) contentW / cols;
                    for (int cIdx = 0; cIdx < cols; cIdx++) {
                        // RTL column order: index 0 on the right
                        float xLeft = (pageW - margin) - (cIdx + 1) * colW;
                        StaticLayout cellSl = rtlLayout(doc.tableHeaders[cIdx], tpTh, Math.max(20, (int) colW - 6), Layout.Alignment.ALIGN_CENTER);
                        canvas.save();
                        canvas.translate(xLeft + 3, y + Math.max(2, (thH - cellSl.getHeight()) / 2f));
                        cellSl.draw(canvas);
                        canvas.restore();
                    }
                    y += thH + 2;

                    while (rowIdx < totalRows) {
                        String[] r = doc.tableRows.get(rowIdx);
                        int maxRowH = 20;
                        StaticLayout[] cellLayouts = new StaticLayout[cols];
                        for (int cIdx = 0; cIdx < cols; cIdx++) {
                            String val = (r != null && cIdx < r.length && r[cIdx] != null) ? r[cIdx] : "";
                            cellLayouts[cIdx] = rtlLayout(val, tpBody, Math.max(20, (int) colW - 6),
                                    cIdx == 0 ? Layout.Alignment.ALIGN_NORMAL : Layout.Alignment.ALIGN_CENTER);
                            maxRowH = Math.max(maxRowH, cellLayouts[cIdx].getHeight() + 8);
                        }
                        if (y + maxRowH > pageH - margin - footerReserve) {
                            break;
                        }
                        if (rowIdx % 2 == 1) {
                            fill.setColor(Color.rgb(248, 250, 252));
                            canvas.drawRect(margin, y, pageW - margin, y + maxRowH, fill);
                        }
                        canvas.drawLine(margin, y + maxRowH, pageW - margin, y + maxRowH, stroke);
                        for (int cIdx = 0; cIdx < cols; cIdx++) {
                            float xLeft = (pageW - margin) - (cIdx + 1) * colW;
                            canvas.save();
                            canvas.translate(xLeft + 3, y + 4);
                            cellLayouts[cIdx].draw(canvas);
                            canvas.restore();
                        }
                        y += maxRowH;
                        rowIdx++;
                    }
                }

                // Summary & QR when finished rows for this document
                if (rowIdx >= totalRows) {
                    y += 10;
                    if (doc.summaryLines != null && !doc.summaryLines.isEmpty()) {
                        for (String s : doc.summaryLines) {
                            if (s == null || s.trim().isEmpty()) continue;
                            StaticLayout sl = rtlLayout(s, tpBold, contentW, Layout.Alignment.ALIGN_NORMAL);
                            if (y + sl.getHeight() < pageH - margin - 45) {
                                canvas.save();
                                canvas.translate(margin, y);
                                sl.draw(canvas);
                                canvas.restore();
                                y += sl.getHeight() + 4;
                            }
                        }
                    }
                    if (cfg.showQrCode && doc.qrContent != null && !doc.qrContent.isEmpty() && y + 66 < pageH - margin - 26) {
                        Bitmap qr = ShareEngine.generateQrBitmap(doc.qrContent, 60);
                        if (qr != null) {
                            canvas.drawBitmap(qr, margin, y + 4, null);
                        }
                    }
                }

                // Footer & Page Number
                int fy = pageH - margin - 18;
                canvas.drawLine(margin, fy - 4, pageW - margin, fy - 4, stroke);
                String footerStr = cfg.footerText == null ? "" : cfg.footerText;
                if (cfg.showPageNumbers) {
                    footerStr = footerStr + "  •  صفحة " + pageNum;
                }
                StaticLayout slFoot = rtlLayout(footerStr, tpMuted, contentW, Layout.Alignment.ALIGN_CENTER);
                canvas.save();
                canvas.translate(margin, fy);
                slFoot.draw(canvas);
                canvas.restore();

                pdf.finishPage(page);
            } while (rowIdx < totalRows);
        }

        try (FileOutputStream out = new FileOutputStream(file)) {
            pdf.writeTo(out);
            out.flush();
        } catch (Exception e) {
            return null;
        } finally {
            pdf.close();
        }
        return file;
    }

    public static Bitmap createThermalDocumentBitmap(Context c, PrintDocumentModel doc, PrintTemplateConfig cfg) {
        if (c == null || doc == null) return null;
        if (cfg == null) cfg = loadConfig(c);

        final int width = cfg.paperSize.isThermal() ? cfg.paperSize.thermalWidthPx : 384;
        final int margin = 12;
        final int contentW = width - margin * 2;
        final float scale = width >= 500 ? 1.32f : 1.0f;
        final float bodyPx = 13f * (203f / 160f) * scale;
        final float smallPx = bodyPx * 0.86f;

        TextPaint tpTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tpTitle.setColor(Color.BLACK);
        tpTitle.setTextSize(bodyPx + 2f);
        tpTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        TextPaint tpBold = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tpBold.setColor(Color.BLACK);
        tpBold.setTextSize(bodyPx);
        tpBold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        TextPaint tpBody = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tpBody.setColor(Color.BLACK);
        tpBody.setTextSize(bodyPx);
        tpBody.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        TextPaint tpSmall = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tpSmall.setColor(Color.BLACK);
        tpSmall.setTextSize(smallPx);

        List<StaticLayout> topBlocks = new ArrayList<>();
        int totalH = 14;

        Bitmap logo = cfg.showLogo ? loadStoreLogo(c, Math.round(48 * scale)) : null;
        if (logo != null) totalH += logo.getHeight() + 6;

        String headerStr = cfg.headerTitle + (cfg.headerPhone != null && !cfg.headerPhone.isEmpty() ? "\n" + cfg.headerPhone : "");
        StaticLayout slHead = rtlLayout(headerStr, tpTitle, contentW, Layout.Alignment.ALIGN_CENTER);
        topBlocks.add(slHead);
        totalH += slHead.getHeight() + 6;

        String nowStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        String subStr = doc.docTitle + (cfg.showDateTime ? "\n" + nowStr : "");
        StaticLayout slDoc = rtlLayout(subStr, tpBold, contentW, Layout.Alignment.ALIGN_CENTER);
        topBlocks.add(slDoc);
        totalH += slDoc.getHeight() + 8;

        if (doc.metaLines != null) {
            for (String m : doc.metaLines) {
                if (m == null || m.trim().isEmpty()) continue;
                StaticLayout sl = rtlLayout(m, tpBody, contentW, Layout.Alignment.ALIGN_NORMAL);
                topBlocks.add(sl);
                totalH += sl.getHeight() + 4;
            }
        }

        if (doc.highlightBoxText != null && !doc.highlightBoxText.isEmpty()) {
            StaticLayout sl = rtlLayout(doc.highlightBoxText, tpBold, contentW, Layout.Alignment.ALIGN_CENTER);
            topBlocks.add(sl);
            totalH += sl.getHeight() + 10;
        }

        List<StaticLayout[]> rowLayouts = new ArrayList<>();
        int cols = doc.tableHeaders != null ? doc.tableHeaders.length : 0;
        float colW = cols > 0 ? (float) contentW / cols : contentW;
        if (cols > 0 && doc.tableRows != null) {
            totalH += 34; // header bar
            for (String[] row : doc.tableRows) {
                StaticLayout[] cells = new StaticLayout[cols];
                int maxR = 22;
                for (int i = 0; i < cols; i++) {
                    String val = (row != null && i < row.length && row[i] != null) ? row[i] : "";
                    cells[i] = rtlLayout(val, tpSmall, Math.max(24, (int) colW - 4),
                            i == 0 ? Layout.Alignment.ALIGN_NORMAL : Layout.Alignment.ALIGN_CENTER);
                    maxR = Math.max(maxR, cells[i].getHeight() + 6);
                }
                rowLayouts.add(cells);
                totalH += maxR + 4;
            }
        }

        List<StaticLayout> bottomBlocks = new ArrayList<>();
        if (doc.summaryLines != null) {
            for (String s : doc.summaryLines) {
                if (s == null || s.trim().isEmpty()) continue;
                StaticLayout sl = rtlLayout(s, tpBold, contentW, Layout.Alignment.ALIGN_NORMAL);
                bottomBlocks.add(sl);
                totalH += sl.getHeight() + 5;
            }
        }

        Bitmap qr = (cfg.showQrCode && doc.qrContent != null && !doc.qrContent.isEmpty())
                ? ShareEngine.generateQrBitmap(doc.qrContent, Math.round(110 * scale))
                : null;
        if (qr != null) totalH += qr.getHeight() + 10;

        StaticLayout slFooter = rtlLayout(cfg.footerText == null ? "" : cfg.footerText, tpSmall, contentW, Layout.Alignment.ALIGN_CENTER);
        totalH += slFooter.getHeight() + 24;

        Bitmap bmp = Bitmap.createBitmap(width, Math.max(160, totalH), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(2f);

        int y = 8;
        if (logo != null) {
            canvas.drawBitmap(logo, (width - logo.getWidth()) / 2f, y, null);
            y += logo.getHeight() + 6;
        }
        for (StaticLayout sl : topBlocks) {
            canvas.save();
            canvas.translate(margin, y);
            sl.draw(canvas);
            canvas.restore();
            y += sl.getHeight() + 5;
        }
        canvas.drawLine(margin, y, width - margin, y, linePaint);
        y += 8;

        if (cols > 0 && doc.tableHeaders != null) {
            Paint thBg = new Paint();
            thBg.setColor(Color.BLACK);
            canvas.drawRect(margin, y, width - margin, y + 28, thBg);
            TextPaint tpTh = new TextPaint(tpSmall);
            tpTh.setColor(Color.WHITE);
            tpTh.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            for (int i = 0; i < cols; i++) {
                float xLeft = (width - margin) - (i + 1) * colW;
                StaticLayout sl = rtlLayout(doc.tableHeaders[i], tpTh, Math.max(24, (int) colW - 4), Layout.Alignment.ALIGN_CENTER);
                canvas.save();
                canvas.translate(xLeft + 2, y + 4);
                sl.draw(canvas);
                canvas.restore();
            }
            y += 32;

            for (StaticLayout[] cells : rowLayouts) {
                int maxR = 22;
                for (int i = 0; i < cols; i++) {
                    float xLeft = (width - margin) - (i + 1) * colW;
                    canvas.save();
                    canvas.translate(xLeft + 2, y + 2);
                    cells[i].draw(canvas);
                    canvas.restore();
                    maxR = Math.max(maxR, cells[i].getHeight() + 6);
                }
                y += maxR + 4;
            }
            canvas.drawLine(margin, y, width - margin, y, linePaint);
            y += 8;
        }

        for (StaticLayout sl : bottomBlocks) {
            canvas.save();
            canvas.translate(margin, y);
            sl.draw(canvas);
            canvas.restore();
            y += sl.getHeight() + 5;
        }

        if (qr != null) {
            canvas.drawBitmap(qr, (width - qr.getWidth()) / 2f, y + 4, null);
            y += qr.getHeight() + 10;
        }

        canvas.save();
        canvas.translate(margin, y + 4);
        slFooter.draw(canvas);
        canvas.restore();
        y += slFooter.getHeight() + 14;

        return Bitmap.createBitmap(bmp, 0, 0, width, Math.min(y, bmp.getHeight()));
    }

    public static Bitmap prepareBitmapOrientationForPrinter(Bitmap src, PrintTemplateConfig cfg) {
        if (src == null || cfg == null) return src;
        if (!cfg.flip180 && !cfg.mirrorHorizontal) return src;
        Matrix m = new Matrix();
        if (cfg.flip180) m.postRotate(180f);
        if (cfg.mirrorHorizontal) m.postScale(-1f, 1f);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    public static void sendBitmapToBluetoothDevice(Activity a, BluetoothDevice device, Bitmap rawBitmap, PrintTemplateConfig cfg) {
        if (a == null || device == null || rawBitmap == null) return;
        if (cfg == null) cfg = loadConfig(a);
        final int targetWidth = cfg.paperSize == PaperSize.THERMAL_80MM ? 576 : 384;
        final int threshold = cfg.darknessThreshold;
        final Bitmap oriented = prepareBitmapOrientationForPrinter(rawBitmap, cfg);

        new Thread(() -> {
            BluetoothSocket socket = null;
            OutputStream out = null;
            try {
                if (Build.VERSION.SDK_INT >= 31 && a.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("BLUETOOTH_CONNECT required");
                }
                UUID spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    try { adapter.cancelDiscovery(); } catch (Throwable ignored) {}
                }
                try {
                    socket = device.createRfcommSocketToServiceRecord(spp);
                    socket.connect();
                } catch (Exception first) {
                    try { if (socket != null) socket.close(); } catch (Exception ignored) {}
                    try {
                        socket = device.createInsecureRfcommSocketToServiceRecord(spp);
                        socket.connect();
                    } catch (Exception second) {
                        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
                        java.lang.reflect.Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                        socket = (BluetoothSocket) m.invoke(device, 1);
                        socket.connect();
                    }
                }
                out = socket.getOutputStream();
                out.write(new byte[]{0x1B, 0x40}); // Initialize printer
                out.write(new byte[]{0x1B, 0x7B, 0x00}); // Explicitly disable upside-down printing
                out.write(new byte[]{0x1B, 0x61, 0x00}); // Reset alignment

                Bitmap prepared = oriented;
                if (oriented.getWidth() != targetWidth) {
                    float scale = (float) targetWidth / oriented.getWidth();
                    int newH = Math.max(1, Math.round(oriented.getHeight() * scale));
                    prepared = Bitmap.createScaledBitmap(oriented, targetWidth, newH, true);
                }

                int width = prepared.getWidth(), height = prepared.getHeight();
                int bpr = (width + 7) / 8;
                final int sliceH = 48;

                for (int y = 0; y < height; y += sliceH) {
                    int curH = Math.min(sliceH, height - y);
                    byte[] cmd = new byte[8 + bpr * curH];
                    cmd[0] = 0x1D; cmd[1] = 0x76; cmd[2] = 0x30; cmd[3] = 0x00;
                    cmd[4] = (byte) (bpr & 0xFF); cmd[5] = (byte) ((bpr >> 8) & 0xFF);
                    cmd[6] = (byte) (curH & 0xFF); cmd[7] = (byte) ((curH >> 8) & 0xFF);
                    int p = 8;
                    for (int sy = 0; sy < curH; sy++) {
                        int actualY = y + sy;
                        for (int xb = 0; xb < bpr; xb++) {
                            int v = 0;
                            for (int bit = 0; bit < 8; bit++) {
                                int x = xb * 8 + bit;
                                if (x < width) {
                                    int px = prepared.getPixel(x, actualY);
                                    int g = (Color.red(px) + Color.green(px) + Color.blue(px)) / 3;
                                    if (g < threshold) v |= 1 << (7 - bit);
                                }
                            }
                            cmd[p++] = (byte) v;
                        }
                    }
                    out.write(cmd);
                    out.flush();
                    try { Thread.sleep(14); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw ie; }
                }

                out.write(new byte[]{0x1B, 0x64, 0x04});
                out.flush();
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
                try { out.write(new byte[]{0x1D, 0x56, 0x00}); out.flush(); } catch (Throwable ignored) {}
                a.runOnUiThread(() -> Toast.makeText(a, "✓ تم إرسال المستند إلى الطابعة بنجاح", Toast.LENGTH_SHORT).show());
            } catch (SecurityException se) {
                a.runOnUiThread(() -> Toast.makeText(a, "يرجى منح إذن البلوتوث للطباعة", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                a.runOnUiThread(() -> Toast.makeText(a, "تعذر الطباعة؛ تأكد من تشغيل الطابعة واقترانها", Toast.LENGTH_LONG).show());
            } finally {
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    public static void printBitmapViaBluetooth(Activity a, Bitmap bitmap) {
        if (a == null || bitmap == null) return;
        if (Build.VERSION.SDK_INT >= 31 && a.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 5103);
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { Toast.makeText(a, "الجهاز لا يدعم البلوتوث", Toast.LENGTH_SHORT).show(); return; }
        if (!adapter.isEnabled()) { Toast.makeText(a, "يرجى تفعيل البلوتوث أولاً", Toast.LENGTH_SHORT).show(); return; }

        PrintTemplateConfig cfg = loadConfig(a);
        String addr = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("printer_address", "");
        BluetoothDevice saved = null;
        if (!addr.isEmpty()) { try { saved = adapter.getRemoteDevice(addr); } catch (Exception ignored) {} }
        if (saved != null) {
            sendBitmapToBluetoothDevice(a, saved, bitmap, cfg);
            return;
        }
        Set<BluetoothDevice> paired = adapter.getBondedDevices();
        if (paired == null || paired.isEmpty()) {
            Toast.makeText(a, "لا توجد طابعة بلوتوث مقترنة", Toast.LENGTH_LONG).show();
            return;
        }
        BluetoothDevice[] ds = paired.toArray(new BluetoothDevice[0]);
        String[] names = new String[ds.length];
        for (int i = 0; i < ds.length; i++) {
            names[i] = (ds[i].getName() == null ? "طابعة حرارية" : ds[i].getName()) + "\n" + ds[i].getAddress();
        }
        new AlertDialog.Builder(a).setTitle("اختر الطابعة الحرارية")
                .setItems(names, (d, w) -> {
                    a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("printer_address", ds[w].getAddress()).apply();
                    sendBitmapToBluetoothDevice(a, ds[w], bitmap, cfg);
                })
                .setNegativeButton("إلغاء", null).show();
    }

    public static void printPdfWithSystemSpooler(Activity a, String jobName, File pdfFile, PaperSize paper) {
        if (a == null || pdfFile == null || !pdfFile.exists()) return;
        PrintManager pm = (PrintManager) a.getSystemService(Context.PRINT_SERVICE);
        if (pm == null) { Toast.makeText(a, "خدمة الطباعة غير متوفرة", Toast.LENGTH_SHORT).show(); return; }
        PrintAttributes.MediaSize media = PrintAttributes.MediaSize.ISO_A4;
        if (paper == PaperSize.A5) media = PrintAttributes.MediaSize.ISO_A5;
        else if (paper == PaperSize.LETTER) media = PrintAttributes.MediaSize.NA_LETTER;

        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(media)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build();

        pm.print(jobName == null ? "AlAzzi_Print" : jobName, new PrintDocumentAdapter() {
            @Override
            public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                 CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
                if (cancellationSignal.isCanceled()) { callback.onLayoutCancelled(); return; }
                PrintDocumentInfo info = new PrintDocumentInfo.Builder((jobName == null ? "document" : jobName) + ".pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build();
                callback.onLayoutFinished(info, true);
            }

            @Override
            public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                                CancellationSignal cancellationSignal, WriteResultCallback callback) {
                try (InputStream in = new FileInputStream(pdfFile);
                     OutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (Exception e) {
                    callback.onWriteFailed(e.getMessage());
                }
            }
        }, attrs);
    }

    public static void showPrintCenterDialog(Activity a, PrintDocumentModel doc, ShareEngine.SharePayload sharePayload) {
        if (a == null || doc == null) return;
        final PrintTemplateConfig cfg = loadConfig(a);
        final Bitmap[] currentBitmap = {createThermalDocumentBitmap(a, doc, cfg)};

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(DesignSystem.dp(a, 14), DesignSystem.dp(a, 12), DesignSystem.dp(a, 14), DesignSystem.dp(a, 12));
        root.setBackgroundColor(DesignSystem.card(a));

        TextView head = new TextView(a);
        head.setText("🖨️ مركز الطباعة والمعاينة الاحترافية");
        head.setTextSize(16f);
        head.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        head.setTextColor(DesignSystem.primary(a));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // Paper Size Selector Pills
        LinearLayout paperRow = new LinearLayout(a);
        paperRow.setOrientation(LinearLayout.HORIZONTAL);
        paperRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        paperRow.setPadding(0, DesignSystem.dp(a, 8), 0, DesignSystem.dp(a, 8));

        final ImageView previewImg = new ImageView(a);
        previewImg.setAdjustViewBounds(true);
        previewImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImg.setBackgroundColor(Color.WHITE);
        previewImg.setImageBitmap(currentBitmap[0]);

        PaperSize[] sizes = PaperSize.values();
        Button[] sizeBtns = new Button[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            final PaperSize ps = sizes[i];
            final int idx = i;
            Button b = new Button(a);
            b.setText(ps.label.replace("ورق ", "").replace("حراري ", ""));
            b.setTextSize(11f);
            b.setAllCaps(false);
            b.setPadding(DesignSystem.dp(a, 4), 0, DesignSystem.dp(a, 4), 0);
            sizeBtns[i] = b;
            b.setOnClickListener(v -> {
                cfg.paperSize = ps;
                saveConfig(a, cfg);
                currentBitmap[0] = createThermalDocumentBitmap(a, doc, cfg);
                previewImg.setImageBitmap(currentBitmap[0]);
                for (int k = 0; k < sizes.length; k++) {
                    boolean active = (k == idx);
                    sizeBtns[k].setTextColor(active ? Color.WHITE : DesignSystem.textPrimary(a));
                    DesignSystem.applyInteractiveFeedback(sizeBtns[k], a,
                            active ? DesignSystem.primary(a) : DesignSystem.surfaceAlt(a),
                            DesignSystem.border(a), 8);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, DesignSystem.dp(a, 40), 1f);
            if (i > 0) lp.setMargins(DesignSystem.dp(a, 3), 0, 0, 0);
            paperRow.addView(b, lp);
        }
        // Init active highlight
        for (int k = 0; k < sizes.length; k++) {
            boolean active = (sizes[k] == cfg.paperSize);
            sizeBtns[k].setTextColor(active ? Color.WHITE : DesignSystem.textPrimary(a));
            DesignSystem.applyInteractiveFeedback(sizeBtns[k], a,
                    active ? DesignSystem.primary(a) : DesignSystem.surfaceAlt(a),
                    DesignSystem.border(a), 8);
        }
        root.addView(paperRow, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(a);
        scroll.setBackground(DesignSystem.outlined(Color.WHITE, DesignSystem.border(a), DesignSystem.dp(a, 1), DesignSystem.dp(a, 10)));
        scroll.addView(previewImg, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, DesignSystem.dp(a, 290)));

        TextView zoomTip = new TextView(a);
        zoomTip.setText("🔍 اضغط على المعاينة لفتحها بوضع التكبير وملء الشاشة");
        zoomTip.setTextSize(11f);
        zoomTip.setTextColor(DesignSystem.textMuted(a));
        zoomTip.setGravity(Gravity.CENTER);
        zoomTip.setPadding(0, DesignSystem.dp(a, 6), 0, DesignSystem.dp(a, 6));
        root.addView(zoomTip, new LinearLayout.LayoutParams(-1, -2));
        previewImg.setOnClickListener(v -> PresentationKit.showZoomableImageViewer(a, doc.docTitle, currentBitmap[0],
                () -> printBitmapViaBluetooth(a, currentBitmap[0]),
                () -> {
                    if (sharePayload != null) ShareEngine.showShareSheet(a, sharePayload);
                }));

        // Action row 1: Thermal Print + System PDF Print
        LinearLayout actions1 = new LinearLayout(a);
        actions1.setOrientation(LinearLayout.HORIZONTAL);
        actions1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button btBtn = new Button(a);
        btBtn.setText("🖨️ طباعة بلوتوث");
        btBtn.setAllCaps(false);
        btBtn.setTextColor(Color.WHITE);
        btBtn.setTextSize(13f);
        DesignSystem.applyInteractiveFeedback(btBtn, a, DesignSystem.primary(a), Color.TRANSPARENT, 10);
        btBtn.setOnClickListener(v -> {
            AppStateStore.logActivity(a, "طباعة", doc.docTitle, "طباعة بلوتوث (" + cfg.paperSize.label + ")", "🖨️");
            printBitmapViaBluetooth(a, currentBitmap[0]);
        });
        LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(0, DesignSystem.dp(a, 46), 1f);
        btp.setMargins(DesignSystem.dp(a, 4), 0, 0, 0);
        actions1.addView(btBtn, btp);

        Button pdfPrintBtn = new Button(a);
        pdfPrintBtn.setText("📄 طباعة PDF / نظام");
        pdfPrintBtn.setAllCaps(false);
        pdfPrintBtn.setTextColor(Color.WHITE);
        pdfPrintBtn.setTextSize(13f);
        DesignSystem.applyInteractiveFeedback(pdfPrintBtn, a, DesignSystem.success(a), Color.TRANSPARENT, 10);
        pdfPrintBtn.setOnClickListener(v -> {
            List<PrintDocumentModel> single = new ArrayList<>();
            single.add(doc);
            File pdf = createStructuredPdf(a, single, cfg, "print");
            if (pdf != null) {
                AppStateStore.logActivity(a, "طباعة PDF", doc.docTitle, cfg.paperSize.label, "📄");
                printPdfWithSystemSpooler(a, doc.docTitle, pdf, cfg.paperSize);
            }
        });
        actions1.addView(pdfPrintBtn, new LinearLayout.LayoutParams(0, DesignSystem.dp(a, 46), 1f));
        root.addView(actions1, new LinearLayout.LayoutParams(-1, -2));

        // Action row 2: Export/Share PDF + Template Settings
        LinearLayout actions2 = new LinearLayout(a);
        actions2.setOrientation(LinearLayout.HORIZONTAL);
        actions2.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams a2p = new LinearLayout.LayoutParams(-1, -2);
        a2p.topMargin = DesignSystem.dp(a, 6);

        Button sharePdfBtn = new Button(a);
        sharePdfBtn.setText("📤 تصدير ومشاركة PDF");
        sharePdfBtn.setAllCaps(false);
        sharePdfBtn.setTextColor(DesignSystem.textPrimary(a));
        sharePdfBtn.setTextSize(12.5f);
        DesignSystem.applyInteractiveFeedback(sharePdfBtn, a, DesignSystem.surfaceAlt(a), DesignSystem.border(a), 10);
        sharePdfBtn.setOnClickListener(v -> {
            List<PrintDocumentModel> single = new ArrayList<>();
            single.add(doc);
            File pdf = createStructuredPdf(a, single, cfg, "export");
            if (pdf != null) {
                DocumentCenter.sharePdf(a, pdf, doc.docTitle + " — " + cfg.headerTitle, "مشاركة ملف PDF");
            }
        });
        LinearLayout.LayoutParams spp = new LinearLayout.LayoutParams(0, DesignSystem.dp(a, 44), 1.3f);
        spp.setMargins(DesignSystem.dp(a, 4), 0, 0, 0);
        actions2.addView(sharePdfBtn, spp);

        Button tplBtn = new Button(a);
        tplBtn.setText("⚙️ القالب والاتجاه");
        tplBtn.setAllCaps(false);
        tplBtn.setTextColor(DesignSystem.textPrimary(a));
        tplBtn.setTextSize(12.5f);
        DesignSystem.applyInteractiveFeedback(tplBtn, a, DesignSystem.card(a), DesignSystem.border(a), 10);
        tplBtn.setOnClickListener(v -> showTemplateSettingsDialog(a, () -> {
            PrintTemplateConfig updated = loadConfig(a);
            currentBitmap[0] = createThermalDocumentBitmap(a, doc, updated);
            previewImg.setImageBitmap(currentBitmap[0]);
        }));
        actions2.addView(tplBtn, new LinearLayout.LayoutParams(0, DesignSystem.dp(a, 44), 1f));
        root.addView(actions2, a2p);

        new AlertDialog.Builder(a).setView(root).setNegativeButton("إغلاق", null).show();
    }

    public static void showTemplateSettingsDialog(Activity a, Runnable onSaved) {
        if (a == null) return;
        PrintTemplateConfig cfg = loadConfig(a);

        ScrollView sv = new ScrollView(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(DesignSystem.dp(a, 14), DesignSystem.dp(a, 10), DesignSystem.dp(a, 14), DesignSystem.dp(a, 10));

        EditText etHeader = new EditText(a);
        etHeader.setHint("عنوان الترويسة");
        etHeader.setText(cfg.headerTitle);
        EditText etPhone = new EditText(a);
        etPhone.setHint("رقم الهاتف بالترويسة");
        etPhone.setText(cfg.headerPhone);
        EditText etFooter = new EditText(a);
        etFooter.setHint("نص التذييل أسفل الورقة");
        etFooter.setText(cfg.footerText);

        CheckBox cbLogo = new CheckBox(a); cbLogo.setText("إظهار الشعار في الترويسة"); cbLogo.setChecked(cfg.showLogo);
        CheckBox cbDt = new CheckBox(a); cbDt.setText("إظهار التاريخ والوقت"); cbDt.setChecked(cfg.showDateTime);
        CheckBox cbPages = new CheckBox(a); cbPages.setText("إظهار أرقام الصفحات في PDF"); cbPages.setChecked(cfg.showPageNumbers);
        CheckBox cbQr = new CheckBox(a); cbQr.setText("إظهار رمز QR للتحقق السريع"); cbQr.setChecked(cfg.showQrCode);
        CheckBox cbFlip180 = new CheckBox(a); cbFlip180.setText("تدوير 180° (علاج الطباعة المقلوبة رأساً على عقب)"); cbFlip180.setChecked(cfg.flip180);
        CheckBox cbMirror = new CheckBox(a); cbMirror.setText("عكس أفقي (للطابعات ذات رأس طباعة معكوس)"); cbMirror.setChecked(cfg.mirrorHorizontal);

        box.addView(etHeader);
        box.addView(etPhone);
        box.addView(etFooter);
        box.addView(cbLogo);
        box.addView(cbDt);
        box.addView(cbPages);
        box.addView(cbQr);
        box.addView(cbFlip180);
        box.addView(cbMirror);
        sv.addView(box);

        new AlertDialog.Builder(a)
                .setTitle("تخصيص قالب الطباعة وإصلاح الاتجاه")
                .setView(sv)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ القالب", (d, w) -> {
                    cfg.headerTitle = etHeader.getText().toString().trim();
                    cfg.headerPhone = etPhone.getText().toString().trim();
                    cfg.footerText = etFooter.getText().toString().trim();
                    cfg.showLogo = cbLogo.isChecked();
                    cfg.showDateTime = cbDt.isChecked();
                    cfg.showPageNumbers = cbPages.isChecked();
                    cfg.showQrCode = cbQr.isChecked();
                    cfg.flip180 = cbFlip180.isChecked();
                    cfg.mirrorHorizontal = cbMirror.isChecked();
                    saveConfig(a, cfg);
                    Toast.makeText(a, "✓ تم حفظ قالب الطباعة المخصص", Toast.LENGTH_SHORT).show();
                    if (onSaved != null) onSaved.run();
                }).show();
    }

    private static StaticLayout rtlLayout(String text, TextPaint paint, int width, Layout.Alignment align) {
        String safe = text == null ? "" : text;
        int w = Math.max(16, width);
        return StaticLayout.Builder.obtain(safe, 0, safe.length(), paint, w)
                .setAlignment(align)
                .setLineSpacing(0f, 1.08f)
                .setIncludePad(true)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .build();
    }
}
