package com.ilukezippo.grabbit.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ImageConverterTest {
    private val ctx: Application get() = ApplicationProvider.getApplicationContext()

    private fun png(): File {
        val b = Bitmap.createBitmap(64, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT); setPixel(5, 5, Color.RED) }
        return File(ctx.cacheDir, "in.png").also { f -> f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun head(f: File, n: Int) = f.inputStream().use { it.readNBytes(n) }

    @Test fun convertsToJpgWebpPngAndPdf() = runBlocking {
        val src = png()
        val jpg = ImageConverter.convert(ctx, src, File(ctx.cacheDir, "o.jpg"))
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), head(jpg, 2))
        val webp = ImageConverter.convert(ctx, src, File(ctx.cacheDir, "o.webp"))
        assertEquals("RIFF", String(head(webp, 4)))
        val pngOut = ImageConverter.convert(ctx, src, File(ctx.cacheDir, "o.png"))
        assertEquals("PNG", String(head(pngOut, 4)).substring(1))
        val pdf = ImageConverter.convert(ctx, src, File(ctx.cacheDir, "o.pdf"))
        assertEquals("%PDF", String(head(pdf, 4)))
        pdf.copyTo(File("/tmp/grabbit-test.pdf"), overwrite = true); Unit
    }

    @Test fun knowsWhichFilesAreImages() {
        assertTrue(ImageConverter.isImage("image/heic"))
        assertTrue(ImageConverter.isImage("image/jpeg"))
        assertFalse(ImageConverter.isImage("image/gif")) // animated: handled as video by FFmpeg
        assertFalse(ImageConverter.isImage("video/mp4"))
    }
}
