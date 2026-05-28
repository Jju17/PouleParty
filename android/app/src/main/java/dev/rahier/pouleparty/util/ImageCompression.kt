package dev.rahier.pouleparty.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object ImageCompression {
    // 900 px / quality 60 produces ~70-150 KB JPEGs instead of the
    // 200-500 KB the old 1200 / 80 settings emitted. Validators only
    // need enough resolution to confirm "yes the challenge was done";
    // hi-res isn't useful and dominates Firebase Storage bandwidth.
    // Mirrors iOS `UIImage.jpegDataResized` defaults.
    fun compressJpeg(bytes: ByteArray, maxDimension: Int = 900, quality: Int = 60): ByteArray {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val largest = maxOf(source.width, source.height)
        val scaled = if (largest > maxDimension) {
            val scale = maxDimension.toFloat() / largest
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else {
            source
        }
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }
}
