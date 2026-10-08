package dev.rahier.pouleparty.util

import androidx.core.graphics.scale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.rahier.pouleparty.model.SubmissionMediaType
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Upload ceiling for a video proof; Storage rules refuse anything above 25 MB. */
const val MAX_VIDEO_PROOF_BYTES = 20 * 1024 * 1024

fun isProofTooLarge(sizeBytes: Int, type: SubmissionMediaType): Boolean =
    type == SubmissionMediaType.VIDEO && sizeBytes > MAX_VIDEO_PROOF_BYTES

/** Width and height that fit [maxDimension] while keeping the aspect ratio. */
fun scaledSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
    val largest = maxOf(width, height)
    if (largest <= maxDimension || largest == 0) return width to height
    val scale = maxDimension.toFloat() / largest
    return (width * scale).toInt() to (height * scale).toInt()
}

/** Prepares a captured proof for upload; photos are shrunk, videos are sent as recorded. */
fun interface ProofMediaPreparer {
    fun prepare(bytes: ByteArray, type: SubmissionMediaType): ByteArray
}

class JpegProofMediaPreparer @Inject constructor() : ProofMediaPreparer {
    override fun prepare(bytes: ByteArray, type: SubmissionMediaType): ByteArray {
        if (type != SubmissionMediaType.IMAGE) return bytes
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val (width, height) = scaledSize(source.width, source.height, MAX_PHOTO_DIMENSION)
        val scaled = if (width != source.width) source.scale(width, height) else source
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, output)
        return output.toByteArray()
    }

    private companion object {
        // Enough for a referee to judge the proof, mirrors the iOS resize.
        const val MAX_PHOTO_DIMENSION = 900
        const val PHOTO_QUALITY = 60
    }
}
