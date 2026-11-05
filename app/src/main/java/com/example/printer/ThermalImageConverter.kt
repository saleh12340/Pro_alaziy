package com.example.printer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ThermalImageConverter {

    /**
     * Resizes and converts a Bitmap to a monochrome (1-bit black and white) image suitable
     * for thermal printers (58mm = 384 dots width, 80mm = 576 dots width).
     */
    fun prepareForThermalPrinter(
        original: Bitmap,
        targetWidthDots: Int = 384 // Default 384px for 58mm POS printer
    ): Bitmap {
        val scaledBitmap = if (original.width != targetWidthDots) {
            val aspectRatio = original.height.toFloat() / original.width.toFloat()
            val targetHeight = (targetWidthDots * aspectRatio).toInt()
            Bitmap.createScaledBitmap(original, targetWidthDots, targetHeight, true)
        } else {
            original
        }

        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val bwBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            // High-contrast threshold: Ensures every stroke of Arabic text is printed solid jet black
            pixels[i] = if (gray < 210) Color.BLACK else Color.WHITE
        }

        bwBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bwBitmap
    }

    /**
     * Encodes a monochrome Bitmap into standard ESC/POS Raster Image format commands (`GS v 0`).
     * Compatible with 99% of mini Bluetooth thermal printers (POS-58, POS-80, PT-210, etc.).
     */
    fun decodeBitmapToEscPosBytes(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        val output = ByteArrayOutputStream()

        // Initialize printer command: ESC @
        output.write(byteArrayOf(0x1B, 0x40))
        // Center alignment command: ESC a 1
        output.write(byteArrayOf(0x1B, 0x61, 0x01))

        // Split large images into smaller chunks (e.g. max 128 rows per chunk) to avoid printer buffer overflow
        val maxChunkHeight = 128
        var currentY = 0

        while (currentY < height) {
            val chunkHeight = Math.min(maxChunkHeight, height - currentY)

            // ESC/POS GS v 0 command header: GS v 0 m xL xH yL yH
            val xL = (widthBytes and 0xFF).toByte()
            val xH = ((widthBytes shr 8) and 0xFF).toByte()
            val yL = (chunkHeight and 0xFF).toByte()
            val yH = ((chunkHeight shr 8) and 0xFF).toByte()

            val header = byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH)
            output.write(header)

            val chunkBytes = ByteArray(widthBytes * chunkHeight)
            var byteIdx = 0

            for (y in currentY until (currentY + chunkHeight)) {
                for (xByte in 0 until widthBytes) {
                    var byteVal = 0
                    for (bit in 0..7) {
                        val xPixel = xByte * 8 + bit
                        if (xPixel < width) {
                            val pixel = bitmap.getPixel(xPixel, y)
                            val isBlack = Color.red(pixel) < 128
                            if (isBlack) {
                                byteVal = byteVal or (0x80 shr bit)
                            }
                        }
                    }
                    chunkBytes[byteIdx++] = byteVal.toByte()
                }
            }
            output.write(chunkBytes)
            currentY += chunkHeight
        }

        // Feed paper lines & cut command
        output.write(byteArrayOf(0x1B, 0x64, 0x02)) // Feed 2 lines to save paper
        output.write(byteArrayOf(0x1D, 0x56, 0x41, 0x00)) // Partial paper cut

        return output.toByteArray()
    }

    /**
     * Saves invoice bitmap image to device external storage or MediaStore Gallery.
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val filename = "Invoice_${fileName}_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var imageUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Fatooraty")
                }
                val resolver = context.contentResolver
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val fatooratyDir = File(imagesDir, "Fatooraty")
                if (!fatooratyDir.exists()) fatooratyDir.mkdirs()
                val imageFile = File(fatooratyDir, filename)
                fos = FileOutputStream(imageFile)
                imageUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
            }

            if (fos != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
                fos.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return imageUri
    }

    /**
     * Saves bitmap temporarily in cache for sharing via Intent (WhatsApp, etc.).
     */
    fun getShareableUri(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "invoice_share_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
