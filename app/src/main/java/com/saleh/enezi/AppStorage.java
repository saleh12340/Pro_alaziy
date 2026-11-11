package com.saleh.enezi;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AppStorage {
    public static final String ROOT_FOLDER = "بقالة العزي Pro خاص";
    public static final String SUB_INVOICE_PHOTOS = "صور الفواتير";
    public static final String SUB_APP_IMAGES = "الصور التي ينتجها التطبيق";
    public static final String SUB_BACKUPS = "النسخ الاحتياطية";
    public static final String SUB_PDFS = "ملفات PDF";

    public static final String RELATIVE_ROOT = "Download/" + ROOT_FOLDER;
    public static final String RELATIVE_INVOICE_PHOTOS = RELATIVE_ROOT + "/" + SUB_INVOICE_PHOTOS;
    public static final String RELATIVE_APP_IMAGES = RELATIVE_ROOT + "/" + SUB_APP_IMAGES;
    public static final String RELATIVE_BACKUPS = RELATIVE_ROOT + "/" + SUB_BACKUPS;
    public static final String RELATIVE_PDFS = RELATIVE_ROOT + "/" + SUB_PDFS;

    public static File getPublicRootFolder() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File root = new File(downloads, ROOT_FOLDER);
        if (!root.exists()) root.mkdirs();
        return root;
    }

    public static File getInvoicesPhotosDir() {
        File dir = new File(getPublicRootFolder(), SUB_INVOICE_PHOTOS);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getAppImagesDir() {
        File dir = new File(getPublicRootFolder(), SUB_APP_IMAGES);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getBackupsDir() {
        File dir = new File(getPublicRootFolder(), SUB_BACKUPS);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getInternalInvoicesDir(Context context) {
        File base=context.getExternalFilesDir(null);
        if(base==null)base=context.getFilesDir();
        File dir=new File(base,SUB_INVOICE_PHOTOS);
        if(!dir.exists())dir.mkdirs();
        return dir;
    }

    public static File getInternalAppImagesDir(Context context) {
        File base=context.getExternalFilesDir(null);
        if(base==null)base=context.getFilesDir();
        File dir=new File(base,SUB_APP_IMAGES);
        if(!dir.exists())dir.mkdirs();
        return dir;
    }

    public static File getInternalBackupsDir(Context context) {
        File base=context.getExternalFilesDir(null);
        if(base==null)base=context.getFilesDir();
        File dir=new File(base,SUB_BACKUPS);
        if(!dir.exists())dir.mkdirs();
        return dir;
    }

    public static File getInternalPdfsDir(Context context) {
        File base=context.getExternalFilesDir(null);
        if(base==null)base=context.getFilesDir();
        File dir=new File(base,SUB_PDFS);
        if(!dir.exists())dir.mkdirs();
        return dir;
    }

    public static void initializeAllDirectories(Context context) {
        try {
            if(Build.VERSION.SDK_INT<29){
                getInvoicesPhotosDir();
                getAppImagesDir();
                getBackupsDir();
            }
            getInternalInvoicesDir(context);
            getInternalAppImagesDir(context);
            getInternalBackupsDir(context);
            getInternalPdfsDir(context);
        } catch (Exception ignored) {}
    }

    private static boolean writeBitmap(File file,Bitmap bitmap,Bitmap.CompressFormat format,int quality) {
        if(file==null||bitmap==null)return false;
        File parent=file.getParentFile();
        if(parent!=null&&!parent.exists()&&!parent.mkdirs())return false;
        try(FileOutputStream out=new FileOutputStream(file)){
            boolean ok=bitmap.compress(format,quality,out);
            out.flush();
            return ok&&file.exists()&&file.length()>0;
        }catch(Exception e){return false;}
    }

    private static Uri saveBitmapToDownloads(Context context,Bitmap bitmap,String fileName,
                                               String mime,String relativePath,
                                               Bitmap.CompressFormat format,int quality) {
        if(Build.VERSION.SDK_INT<29||bitmap==null)return null;
        Uri uri=null;
        try{
            ContentValues values=new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME,fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE,mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,relativePath);
            values.put(MediaStore.MediaColumns.IS_PENDING,1);
            uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(uri==null)return null;
            try(OutputStream out=context.getContentResolver().openOutputStream(uri,"w")){
                if(out==null||!bitmap.compress(format,quality,out))throw new IOException("تعذر كتابة الصورة");
                out.flush();
            }
            ContentValues ready=new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING,0);
            context.getContentResolver().update(uri,ready,null,null);
            return uri;
        }catch(Exception e){
            if(uri!=null){try{context.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}}
            return null;
        }
    }

    private static Uri internalFileUri(Context context,File file) {
        if(context==null||file==null||!file.exists())return null;
        try{return FileProvider.getUriForFile(context,context.getPackageName()+".fileprovider",file);}
        catch(Exception e){return null;}
    }

    public static String saveInvoicePhoto(Context context, Bitmap bitmap, String fileName) {
        if(context==null||bitmap==null||fileName==null||fileName.trim().isEmpty())return null;
        File internal=new File(getInternalInvoicesDir(context),fileName);
        boolean internalSaved=writeBitmap(internal,bitmap,Bitmap.CompressFormat.JPEG,94);

        if(Build.VERSION.SDK_INT>=29){
            // Scoped storage: publish through MediaStore rather than a direct public-path write.
            saveBitmapToDownloads(context,bitmap,fileName,"image/jpeg",
                    RELATIVE_INVOICE_PHOTOS,Bitmap.CompressFormat.JPEG,94);
        }else{
            try{
                File publicFile=new File(getInvoicesPhotosDir(),fileName);
                if(writeBitmap(publicFile,bitmap,Bitmap.CompressFormat.JPEG,94)){
                    MediaScannerConnection.scanFile(context,new String[]{publicFile.getAbsolutePath()},new String[]{"image/jpeg"},null);
                }
            }catch(Exception ignored){}
        }

        if(internalSaved){
            MediaScannerConnection.scanFile(context,new String[]{internal.getAbsolutePath()},new String[]{"image/jpeg"},null);
            return internal.getAbsolutePath();
        }
        return null;
    }

    public static Uri saveAppImage(Context context, Bitmap bitmap, String fileName) {
        if(context==null||bitmap==null||fileName==null||fileName.trim().isEmpty())return null;
        File internal=new File(getInternalAppImagesDir(context),fileName);
        boolean internalSaved=writeBitmap(internal,bitmap,Bitmap.CompressFormat.PNG,100);

        Uri publicUri=null;
        if(Build.VERSION.SDK_INT>=29){
            publicUri=saveBitmapToDownloads(context,bitmap,fileName,"image/png",
                    RELATIVE_APP_IMAGES,Bitmap.CompressFormat.PNG,100);
        }else{
            try{
                File publicFile=new File(getAppImagesDir(),fileName);
                if(writeBitmap(publicFile,bitmap,Bitmap.CompressFormat.PNG,100)){
                    MediaScannerConnection.scanFile(context,new String[]{publicFile.getAbsolutePath()},new String[]{"image/png"},null);
                }
            }catch(Exception ignored){}
        }
        if(publicUri!=null)return publicUri;
        return internalSaved?internalFileUri(context,internal):null;
    }

    public static Uri savePdf(Context context,File source,String fileName) {
        if(context==null||source==null||!source.isFile()||source.length()<=0)return null;
        String safeName=(fileName==null||fileName.trim().isEmpty())?source.getName():fileName.trim();
        File internal=new File(getInternalPdfsDir(context),safeName);
        boolean internalSaved=false;
        try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(internal,false)){
            byte[] buffer=new byte[16384];int count;
            while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            out.flush();
            internalSaved=internal.isFile()&&internal.length()==source.length();
        }catch(Exception ignored){}

        if(Build.VERSION.SDK_INT>=29){
            Uri uri=null;
            try{
                ContentValues values=new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME,safeName);
                values.put(MediaStore.MediaColumns.MIME_TYPE,"application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,RELATIVE_PDFS);
                values.put(MediaStore.MediaColumns.IS_PENDING,1);
                uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                if(uri!=null){
                    try(InputStream in=new FileInputStream(source);OutputStream out=context.getContentResolver().openOutputStream(uri,"w")){
                        if(out==null)throw new IOException("تعذر فتح ملف PDF");
                        byte[] buffer=new byte[16384];int count;
                        while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                        out.flush();
                    }
                    ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);
                    context.getContentResolver().update(uri,ready,null,null);
                    return uri;
                }
            }catch(Exception e){
                if(uri!=null){try{context.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}}
            }
        }else{
            try{
                File dir=new File(getPublicRootFolder(),SUB_PDFS);
                if(!dir.exists())dir.mkdirs();
                File dest=new File(dir,safeName);
                try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(dest,false)){
                    byte[] buffer=new byte[16384];int count;
                    while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                    out.flush();
                }
                if(dest.isFile()&&dest.length()==source.length()){
                    MediaScannerConnection.scanFile(context,new String[]{dest.getAbsolutePath()},new String[]{"application/pdf"},null);
                    Uri publicUri=internalFileUri(context,dest);
                    if(publicUri!=null)return publicUri;
                }
            }catch(Exception ignored){}
        }
        if(internalSaved)return internalFileUri(context,internal);
        return internalFileUri(context,source);
    }

    public static boolean saveDatabaseBackup(Context context, File srcDb, String fileName) throws IOException {
        if(context==null||srcDb==null||!srcDb.isFile()||srcDb.length()<=0)return false;
        File tmp=new File(context.getCacheDir(),"backup_tmp.db");
        try{
            try(InputStream in=new FileInputStream(srcDb);OutputStream out=new FileOutputStream(tmp,false)){
                byte[] buffer=new byte[16384];int count;
                while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                out.flush();
            }
            if(!tmp.isFile()||tmp.length()!=srcDb.length()||tmp.length()==0)throw new IOException("empty or incomplete backup");

            if(Build.VERSION.SDK_INT>=29){
                Uri uri=null;
                try{
                    ContentValues values=new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME,fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH,RELATIVE_BACKUPS);
                    values.put(MediaStore.MediaColumns.IS_PENDING,1);
                    uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                    if(uri==null)throw new IOException("تعذر إنشاء ملف النسخة الاحتياطية");
                    long copied=0;
                    try(InputStream in=new FileInputStream(tmp);OutputStream out=context.getContentResolver().openOutputStream(uri,"w")){
                        if(out==null)throw new IOException("تعذر فتح ملف النسخة الاحتياطية");
                        byte[] buffer=new byte[16384];int count;
                        while((count=in.read(buffer))!=-1){out.write(buffer,0,count);copied+=count;}
                        out.flush();
                    }
                    if(copied!=tmp.length())throw new IOException("نسخة احتياطية غير مكتملة");
                    ContentValues ready=new ContentValues();
                    ready.put(MediaStore.MediaColumns.IS_PENDING,0);
                    int published=context.getContentResolver().update(uri,ready,null,null);
                    if(published<=0)throw new IOException("تعذر إتاحة النسخة الاحتياطية");
                    return true;
                }catch(Exception e){
                    if(uri!=null){try{context.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}}
                    if(e instanceof IOException)throw (IOException)e;
                    throw new IOException("تعذر حفظ النسخة الاحتياطية في التنزيلات",e);
                }
            }

            File dest=new File(getBackupsDir(),fileName);
            try(InputStream in=new FileInputStream(tmp);OutputStream out=new FileOutputStream(dest,false)){
                byte[] buffer=new byte[16384];int count;
                while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                out.flush();
            }
            if(!dest.isFile()||dest.length()!=tmp.length())throw new IOException("نسخة احتياطية غير مكتملة");
            MediaScannerConnection.scanFile(context,new String[]{dest.getAbsolutePath()},null,null);
            return true;
        }finally{
            try{if(tmp.exists())tmp.delete();}catch(Exception ignored){}
        }
    }
}
