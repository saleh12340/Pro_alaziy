package com.saleh.enezi;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

/**
 * مركز موحد لمشاركة وإدارة المستندات والمخرجات والصور والطباعة.
 * جميع مشاركات الملفات تستخدم FileProvider وصلاحيات القراءة المؤقتة المعتمدة.
 */
public final class DocumentCenter {
    private DocumentCenter(){}

    public static void shareText(Activity a, String text){
        if (a == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "مشاركة النص"));
        } catch (Exception e) {
            Toast.makeText(a, "تعذر مشاركة النص", Toast.LENGTH_SHORT).show();
        }
    }

    public static void shareFile(Activity a, File file, String mime, String caption, String chooser){
        if (a == null) return;
        if (file == null || !file.exists()) {
            Toast.makeText(a, "الملف المراد مشاركته غير موجود", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", file);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime == null ? "application/octet-stream" : mime);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            if (caption != null && !caption.isEmpty()) {
                i.putExtra(Intent.EXTRA_TEXT, caption);
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, chooser == null ? "مشاركة الملف" : chooser));
        } catch (Exception e) {
            Toast.makeText(a, "حدث خطأ أثناء إعداد مشاركة الملف", Toast.LENGTH_SHORT).show();
        }
    }

    public static void sharePdf(Activity a, File file, String caption, String chooser){
        shareFile(a, file, "application/pdf", caption, chooser == null ? "مشاركة ملف PDF" : chooser);
    }

    public static void shareImage(Activity a, File file, String caption, String chooser){
        shareFile(a, file, "image/png", caption, chooser == null ? "مشاركة الصورة" : chooser);
    }

    public static void shareImageUri(Activity a, Uri uri, String caption, String chooser){
        if (a == null || uri == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            if (caption != null && !caption.isEmpty()) {
                i.putExtra(Intent.EXTRA_TEXT, caption);
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, chooser == null ? "مشاركة الصورة" : chooser));
        } catch (Exception e) {
            Toast.makeText(a, "حدث خطأ أثناء مشاركة الصورة", Toast.LENGTH_SHORT).show();
        }
    }

    public static String copyText(Context c, String text){
        if (c == null) return text;
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("نص المشاركة", text == null ? "" : text));
                Toast.makeText(c, "تم نسخ النص إلى الحافظة بنجاح", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {}
        return text;
    }
}
