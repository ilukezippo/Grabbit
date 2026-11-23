package com.ilukezippo.grabbit.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** Copies finished files into the public Download/Grabbit folder. */
object MediaSaver {
    const val FOLDER = "Grabbit"

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: when (name.substringAfterLast('.').lowercase()) {
                "mkv" -> "video/x-matroska"; "opus" -> "audio/ogg"; "ts" -> "video/mp2t"; "m4a" -> "audio/mp4"
                else -> "application/octet-stream"
            }

    fun save(ctx: Context, file: File, displayName: String = file.name): SavedFile {
        val mime = mimeOf(displayName)
        val size = file.length()
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Can't create file in Downloads")
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            SavedFile(uri.toString(), displayName, mime, size)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER).apply { mkdirs() }
            var target = File(dir, displayName)
            var n = 1
            while (target.exists()) target = File(dir, "${displayName.substringBeforeLast('.')} ($n).${displayName.substringAfterLast('.')}").also { n++ }
            file.copyTo(target)
            MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf(mime), null)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", target)
            SavedFile(uri.toString(), target.name, mime, size)
        }
    }
}
