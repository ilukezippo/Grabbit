package com.ilukezippo.grabbit.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Build
import com.ilukezippo.grabbit.core.SinglePagePdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Converts pictures (JPG, PNG, WebP, HEIC, AVIF, BMP…) to JPG, PNG, WebP or PDF on the phone. */
object ImageConverter {
    val TARGETS = listOf("jpg", "png", "webp", "pdf")

    /** Still pictures. GIFs are left to FFmpeg so animation survives (GIF → MP4 and back). */
    fun isImage(mime: String) = mime.startsWith("image/") && mime != "image/gif"

    suspend fun convert(ctx: Context, input: File, output: File): File = withContext(Dispatchers.IO) {
        val bmp = decode(input)
        try {
            when (output.extension.lowercase()) {
                "jpg", "jpeg" -> output.outputStream().use { flatten(bmp).compress(Bitmap.CompressFormat.JPEG, 92, it) }
                "png" -> output.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                "webp" -> output.outputStream().use {
                    @Suppress("DEPRECATION")
                    val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                    bmp.compress(fmt, 90, it)
                }
                "pdf" -> {
                    val jpeg = java.io.ByteArrayOutputStream().also { flatten(bmp).compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
                    output.writeBytes(SinglePagePdf.withJpeg(jpeg, bmp.width, bmp.height))
                }
                else -> error("Can't convert pictures to ${output.extension}")
            }
        } finally { bmp.recycle() }
        output
    }

    private fun decode(f: File): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) runCatching {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        return BitmapFactory.decodeFile(f.absolutePath) ?: error("This picture can't be read")
    }

    /** JPG has no transparency: put the picture on white so see-through parts don't turn black. */
    private fun flatten(b: Bitmap): Bitmap {
        if (!b.hasAlpha()) return b
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(b, 0f, 0f, null) }
        return out
    }
}
