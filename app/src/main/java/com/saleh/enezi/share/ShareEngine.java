package com.saleh.enezi.share;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.saleh.enezi.DocumentCenter;
import com.saleh.enezi.state.AppStateStore;
import com.saleh.enezi.ui.DesignSystem;

import java.io.File;
import java.io.FileOutputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * محرك المشاركة الشامل والحديث (ShareEngine)
 * - مشاركة عبر واتساب / بريد إلكتروني / رسائل SMS / مشاركة النظام الأصلية
 * - توليد بطاقات مشاركة احترافية (Share Cards)
 * - توليد رموز QR فورية عبر مكتبة ZXing المجانية المفتوحة المصدر (Apache 2.0)
 * - روابط عميقة (Deep Links) وصلاحيات قراءة فقط أو قابل للتعديل
 */
public final class ShareEngine {

    public enum AccessMode { READ_ONLY, EDITABLE }

    public static class SharePayload {
        public String screenTag = "general";
        public String title = "مشاركة المستند";
        public String subtitle = "بقالة العزي للمواد الغذائية";
        public String highlightLabel = "";
        public String highlightValue = "";
        public int accentColor = Color.rgb(27, 59, 139);
        public String phone = "";
        public String emailSubject = "بقالة العزي للمواد الغذائية";
        public String textReadOnly = "";
        public String textEditable = "";
        public String deepLink = "alazzi://home";
        public List<String> cardRows;
        public File pdfFile;
        public Uri imageUri;
        public Bitmap prebuiltCardBitmap;
        public AccessMode defaultAccess = AccessMode.READ_ONLY;
    }

    private ShareEngine() {}

    public static String buildDeepLink(String route, String paramKey, String paramValue) {
        String safeRoute = route == null ? "home" : Uri.encode(route);
        if (paramKey == null || paramValue == null || paramValue.isEmpty()) {
            return "alazzi://" + safeRoute;
        }
        return "alazzi://" + safeRoute + "?" + Uri.encode(paramKey) + "=" + Uri.encode(paramValue);
    }

    public static Bitmap generateQrBitmap(String content, int sizePx) {
        if (content == null || content.trim().isEmpty()) content = "alazzi://home";
        int size = Math.max(160, sizePx);
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                int offset = y * w;
                for (int x = 0; x < w; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        } catch (Throwable e) {
            return null;
        }
    }

    public static Bitmap createShareCardBitmap(Context c, SharePayload payload) {
        if (payload == null) return null;
        if (payload.prebuiltCardBitmap != null) return payload.prebuiltCardBitmap;

        final int w = 780;
        final int pad = 34;
        final int innerW = w - pad * 2;
        final int accent = payload.accentColor != 0 ? payload.accentColor : Color.rgb(27, 59, 139);

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(26f);
        tp.setColor(Color.rgb(15, 23, 42));

        StringBuilder rowsStr = new StringBuilder();
        if (payload.cardRows != null) {
            for (String r : payload.cardRows) {
                if (r != null && !r.trim().isEmpty()) {
                    rowsStr.append("• ").append(r.trim()).append("\n");
                }
            }
        }
        String bodyText = rowsStr.toString().trim();
        if (bodyText.isEmpty() && payload.textReadOnly != null) {
            bodyText = payload.textReadOnly;
        }
        if (bodyText.length() > 650) {
            bodyText = bodyText.substring(0, 648) + "…";
        }

        StaticLayout bodyLayout = StaticLayout.Builder.obtain(bodyText, 0, bodyText.length(), tp, innerW - 36)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1.15f)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .build();

        int h = 230 + (payload.highlightValue != null && !payload.highlightValue.isEmpty() ? 120 : 0)
                + bodyLayout.getHeight() + 210;
        h = Math.max(520, h);

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.rgb(241, 245, 249));

        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(18, 18, w - 18, h - 18, 28, 28, cardPaint);

        // Header Gradient Banner
        Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setShader(new LinearGradient(0, 18, w, 170, accent, Color.rgb(15, 36, 94), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(18, 18, w - 18, 168, 28, 28, headerPaint);
        canvas.drawRect(18, 130, w - 18, 168, headerPaint);

        TextPaint hTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        hTitle.setColor(Color.WHITE);
        hTitle.setTextSize(32f);
        hTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        hTitle.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(payload.title == null ? "بقالة العزي Pro" : payload.title, w - pad - 8, 78, hTitle);

        TextPaint hSub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        hSub.setColor(Color.argb(220, 255, 255, 255));
        hSub.setTextSize(22f);
        hSub.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(payload.subtitle == null ? "بقالة العزي للمواد الغذائية" : payload.subtitle, w - pad - 8, 122, hSub);

        // Read-only badge
        String badgeTxt = payload.defaultAccess == AccessMode.READ_ONLY ? "🔒 نسخة موثقة (قراءة فقط)" : "✏️ نسخة قابلة للتعديل";
        Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setColor(Color.argb(45, 255, 255, 255));
        canvas.drawRoundRect(pad, 36, pad + 260, 76, 16, 16, badgePaint);
        TextPaint bTp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bTp.setColor(Color.WHITE);
        bTp.setTextSize(17f);
        bTp.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(badgeTxt, pad + 130, 62, bTp);

        int y = 194;
        if (payload.highlightValue != null && !payload.highlightValue.isEmpty()) {
            Paint hlBg = new Paint(Paint.ANTI_ALIAS_FLAG);
            hlBg.setColor(Color.rgb(235, 243, 254));
            canvas.drawRoundRect(pad, y, w - pad, y + 96, 18, 18, hlBg);

            TextPaint hlLbl = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            hlLbl.setColor(Color.rgb(100, 116, 139));
            hlLbl.setTextSize(20f);
            hlLbl.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(payload.highlightLabel == null ? "" : payload.highlightLabel, w / 2f, y + 34, hlLbl);

            TextPaint hlVal = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            hlVal.setColor(accent);
            hlVal.setTextSize(34f);
            hlVal.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            hlVal.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(payload.highlightValue, w / 2f, y + 78, hlVal);
            y += 116;
        }

        // Body text
        canvas.save();
        canvas.translate(pad + 18, y);
        bodyLayout.draw(canvas);
        canvas.restore();
        y += bodyLayout.getHeight() + 26;

        // Footer with QR Code
        Paint divPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        divPaint.setColor(Color.rgb(226, 232, 240));
        canvas.drawRect(pad, y, w - pad, y + 2, divPaint);
        y += 18;

        Bitmap qr = generateQrBitmap(payload.deepLink != null ? payload.deepLink : payload.textReadOnly, 135);
        if (qr != null) {
            canvas.drawBitmap(qr, pad, y, null);
        }

        TextPaint fTp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        fTp.setColor(Color.rgb(71, 85, 105));
        fTp.setTextSize(19f);
        fTp.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("امسح رمز QR للفتح المباشر أو التحقق", w - pad, y + 48, fTp);
        fTp.setColor(Color.rgb(100, 116, 139));
        fTp.setTextSize(16f);
        canvas.drawText("نظام إدارة بقالة العزي Pro • مشاركة محلية آمنة", w - pad, y + 82, fTp);

        return bmp;
    }

    public static Uri saveCardToCache(Context c, Bitmap bmp, String prefix) {
        if (c == null || bmp == null) return null;
        try {
            File dir = new File(c.getCacheDir(), "share_cards");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, (prefix == null ? "card" : prefix) + "_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream out = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            }
            return FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
        } catch (Exception e) {
            return null;
        }
    }

    public static String normalizeWhatsAppPhone(String phone) {
        String p = phone == null ? "" : phone.trim().replaceAll("[^0-9+]", "");
        if (p.startsWith("+")) p = p.substring(1);
        if (p.startsWith("00")) p = p.substring(2);
        if (p.startsWith("967")) return p;
        if (p.startsWith("0") && p.length() == 10) return "967" + p.substring(1);
        if (p.matches("\\d{9}")) return "967" + p;
        return p;
    }

    public static void shareWhatsApp(Activity a, String phone, String text, Uri streamUri, String mime) {
        if (a == null) return;
        String p = normalizeWhatsAppPhone(phone);
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(streamUri != null ? (mime == null ? "image/png" : mime) : "text/plain");
            if (text != null && !text.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, text);
            if (streamUri != null && "content".equalsIgnoreCase(streamUri.getScheme())) {
                i.putExtra(Intent.EXTRA_STREAM, streamUri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
            if (!p.isEmpty()) i.putExtra("jid", p + "@s.whatsapp.net");

            try {
                Intent wa = new Intent(i);
                wa.setPackage("com.whatsapp");
                a.startActivity(wa);
                return;
            } catch (Exception ignored) {}

            try {
                Intent w4b = new Intent(i);
                w4b.setPackage("com.whatsapp.w4b");
                a.startActivity(w4b);
                return;
            } catch (Exception ignored) {}

            if (streamUri == null && !p.isEmpty()) {
                try {
                    Uri waUri = Uri.parse("https://wa.me/" + p + "?text=" + Uri.encode(text != null ? text : ""));
                    a.startActivity(new Intent(Intent.ACTION_VIEW, waUri));
                    return;
                } catch (Exception ignored) {}
            }

            i.setPackage(null);
            a.startActivity(Intent.createChooser(i, "مشاركة عبر واتساب"));
        } catch (Exception e) {
            Toast.makeText(a, "تعذر فتح واتساب للمشاركة", Toast.LENGTH_LONG).show();
        }
    }

    public static void shareEmail(Activity a, String subject, String body, Uri attachmentUri) {
        if (a == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(attachmentUri != null ? "application/pdf" : "message/rfc822");
            i.putExtra(Intent.EXTRA_SUBJECT, subject == null ? "بقالة العزي للمواد الغذائية" : subject);
            i.putExtra(Intent.EXTRA_TEXT, body == null ? "" : body);
            if (attachmentUri != null) {
                i.putExtra(Intent.EXTRA_STREAM, attachmentUri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            a.startActivity(Intent.createChooser(i, "إرسال عبر البريد الإلكتروني"));
        } catch (Exception e) {
            Toast.makeText(a, "لا يوجد تطبيق بريد متاح", Toast.LENGTH_SHORT).show();
        }
    }

    public static void shareSms(Activity a, String phone, String text) {
        if (a == null) return;
        try {
            String p = phone == null ? "" : phone.replaceAll("[^0-9+]", "");
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + p));
            i.putExtra("sms_body", text == null ? "" : text);
            a.startActivity(i);
        } catch (Exception e) {
            DocumentCenter.shareText(a, text);
        }
    }

    public static void showQrDialog(Activity a, String title, String qrContent, String subtitle) {
        if (a == null) return;
        Bitmap qr = generateQrBitmap(qrContent, 520);
        if (qr == null) {
            Toast.makeText(a, "تعذر توليد رمز QR", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(DesignSystem.dp(a, 18), DesignSystem.dp(a, 16), DesignSystem.dp(a, 18), DesignSystem.dp(a, 16));
        box.setBackgroundColor(DesignSystem.card(a));

        ImageView iv = new ImageView(a);
        iv.setImageBitmap(qr);
        iv.setAdjustViewBounds(true);
        iv.setBackgroundColor(Color.WHITE);
        iv.setPadding(DesignSystem.dp(a, 10), DesignSystem.dp(a, 10), DesignSystem.dp(a, 10), DesignSystem.dp(a, 10));
        box.addView(iv, new LinearLayout.LayoutParams(DesignSystem.dp(a, 240), DesignSystem.dp(a, 240)));

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tv = new TextView(a);
            tv.setText(subtitle);
            tv.setTextSize(13f);
            tv.setTextColor(DesignSystem.textMuted(a));
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, DesignSystem.dp(a, 10), 0, 0);
            box.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        }

        new AlertDialog.Builder(a)
                .setTitle(title == null ? "رمز QR للمشاركة السريعة" : title)
                .setView(box)
                .setPositiveButton("📲 مشاركة QR", (d, w) -> {
                    Uri u = saveCardToCache(a, qr, "qr");
                    if (u != null) DocumentCenter.shareImageUri(a, u, subtitle, "مشاركة رمز QR");
                })
                .setNeutralButton("🔗 نسخ الرابط", (d, w) -> {
                    copyText(a, qrContent);
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    public static void copyText(Context c, String text) {
        if (c == null) return;
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("بقالة العزي Pro", text == null ? "" : text));
            Toast.makeText(c, "✓ تم النسخ إلى الحافظة", Toast.LENGTH_SHORT).show();
        }
    }

    public static void showShareSheet(Activity a, SharePayload payload) {
        if (a == null || payload == null) return;

        final boolean[] readOnlyMode = {payload.defaultAccess == AccessMode.READ_ONLY};

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        root.setPadding(DesignSystem.dp(a, 16), DesignSystem.dp(a, 14), DesignSystem.dp(a, 16), DesignSystem.dp(a, 14));
        root.setBackground(DesignSystem.rounded(DesignSystem.card(a), DesignSystem.dp(a, 18)));

        TextView head = new TextView(a);
        head.setText("📤 مركز المشاركة الشامل");
        head.setTextSize(17f);
        head.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        head.setTextColor(DesignSystem.primary(a));
        head.setGravity(Gravity.RIGHT);
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(a);
        sub.setText(payload.title + " • " + payload.subtitle);
        sub.setTextSize(12.5f);
        sub.setTextColor(DesignSystem.textMuted(a));
        sub.setPadding(0, DesignSystem.dp(a, 2), 0, DesignSystem.dp(a, 10));
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        // Access mode toggle (Read-Only vs Editable)
        Button accessToggle = new Button(a);
        Runnable syncAccessLabel = () -> {
            accessToggle.setText(readOnlyMode[0]
                    ? "🔒 الصلاحية: قراءة فقط (اضغط للتبديل)"
                    : "✏️ الصلاحية: قابل للتعديل (اضغط للتبديل)");
            DesignSystem.applyInteractiveFeedback(accessToggle, a,
                    readOnlyMode[0] ? DesignSystem.surfaceAlt(a) : DesignSystem.warningSoft(a),
                    DesignSystem.border(a), 10);
        };
        accessToggle.setAllCaps(false);
        accessToggle.setTextSize(12.5f);
        accessToggle.setTextColor(DesignSystem.textPrimary(a));
        syncAccessLabel.run();
        accessToggle.setOnClickListener(v -> {
            readOnlyMode[0] = !readOnlyMode[0];
            payload.defaultAccess = readOnlyMode[0] ? AccessMode.READ_ONLY : AccessMode.EDITABLE;
            syncAccessLabel.run();
        });
        LinearLayout.LayoutParams atp = new LinearLayout.LayoutParams(-1, DesignSystem.dp(a, 44));
        atp.bottomMargin = DesignSystem.dp(a, 10);
        root.addView(accessToggle, atp);

        final AlertDialog dlg = new AlertDialog.Builder(a).setView(root).setNegativeButton("إغلاق", null).create();

        String[] labels = {
                "📲 مشاركة عبر واتساب (نص + بطاقة)",
                "🖼️ توليد ومشاركة بطاقة عرض (Share Card)",
                "📄 مشاركة ملف PDF احترافي",
                "🔳 عرض ومشاركة رمز QR السريع",
                "✉️ إرسال عبر البريد الإلكتروني",
                "💬 إرسال عبر رسالة نصية SMS",
                "🔗 نسخ الرابط العميق (Deep Link)",
                "📋 نسخ النص المنسق"
        };

        for (int idx = 0; idx < labels.length; idx++) {
            final int choice = idx;
            if (choice == 2 && (payload.pdfFile == null || !payload.pdfFile.exists())) continue;
            Button btn = new Button(a);
            btn.setText(labels[idx]);
            btn.setAllCaps(false);
            btn.setTextSize(13.5f);
            btn.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            btn.setPadding(DesignSystem.dp(a, 12), 0, DesignSystem.dp(a, 12), 0);
            boolean primary = (idx == 0 || idx == 1);
            btn.setTextColor(primary ? Color.WHITE : DesignSystem.textPrimary(a));
            DesignSystem.applyInteractiveFeedback(btn, a,
                    primary ? (idx == 0 ? DesignSystem.success(a) : DesignSystem.primary(a)) : DesignSystem.surfaceAlt(a),
                    primary ? Color.TRANSPARENT : DesignSystem.border(a), 12);

            btn.setOnClickListener(v -> {
                dlg.dismiss();
                String activeText = readOnlyMode[0]
                        ? (payload.textReadOnly != null && !payload.textReadOnly.isEmpty() ? payload.textReadOnly : payload.textEditable)
                        : (payload.textEditable != null && !payload.textEditable.isEmpty() ? payload.textEditable : payload.textReadOnly);
                if (payload.deepLink != null && !payload.deepLink.isEmpty() && activeText != null && !activeText.contains("alazzi://")) {
                    activeText = activeText + "\n🔗 " + payload.deepLink;
                }
                AppStateStore.logActivity(a, "مشاركة", payload.title, "طريقة المشاركة: " + labels[choice], "📤");

                if (choice == 0) {
                    Uri img = payload.imageUri;
                    if (img == null) {
                        Bitmap card = createShareCardBitmap(a, payload);
                        img = saveCardToCache(a, card, "share_card");
                    }
                    shareWhatsApp(a, payload.phone, activeText, img, "image/png");
                } else if (choice == 1) {
                    Bitmap card = createShareCardBitmap(a, payload);
                    Uri img = saveCardToCache(a, card, "card");
                    if (img != null) {
                        DocumentCenter.shareImageUri(a, img, activeText, "مشاركة بطاقة العرض");
                    }
                } else if (choice == 2 && payload.pdfFile != null) {
                    DocumentCenter.sharePdf(a, payload.pdfFile, activeText, "مشاركة ملف PDF");
                } else if (choice == 3) {
                    String qrPayload = (payload.deepLink != null && !payload.deepLink.isEmpty()) ? payload.deepLink : activeText;
                    showQrDialog(a, payload.title, qrPayload, payload.highlightValue);
                } else if (choice == 4) {
                    Uri attach = payload.pdfFile != null && payload.pdfFile.exists()
                            ? FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", payload.pdfFile)
                            : payload.imageUri;
                    shareEmail(a, payload.emailSubject, activeText, attach);
                } else if (choice == 5) {
                    shareSms(a, payload.phone, activeText);
                } else if (choice == 6) {
                    copyText(a, payload.deepLink != null ? payload.deepLink : "alazzi://home");
                } else if (choice == 7) {
                    copyText(a, activeText);
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, DesignSystem.dp(a, 48));
            lp.bottomMargin = DesignSystem.dp(a, 6);
            root.addView(btn, lp);
        }

        dlg.show();
    }
}
