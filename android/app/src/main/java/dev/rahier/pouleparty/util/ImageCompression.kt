package dev.rahier.pouleparty.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object ImageCompression {
    fun compressJpeg(bytes: ByteArray, maxDimension: Int = 1200, quality: Int = 80): ByteArray {
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
