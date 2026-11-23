package com.ilukezippo.grabbit.core

import java.io.ByteArrayOutputStream

/** A one-page PDF that shows a JPEG at its own size (1 px = 1 pt). Written by hand: no Android APIs needed. */
object SinglePagePdf {
    fun withJpeg(jpeg: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(n: Int, body: () -> Unit) { offsets += out.size(); w("$n 0 obj\n"); body(); w("\nendobj\n") }

        w("%PDF-1.4\n%âãÏÓ\n")
        obj(1) { w("<< /Type /Catalog /Pages 2 0 R >>") }
        obj(2) { w("<< /Type /Pages /Kids [3 0 R] /Count 1 >>") }
        obj(3) { w("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $width $height] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>") }
        obj(4) {
            w("<< /Type /XObject /Subtype /Image /Width $width /Height $height /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
            out.write(jpeg); w("\nendstream")
        }
        val content = "q $width 0 0 $height 0 0 cm /Im0 Do Q"
        obj(5) { w("<< /Length ${content.length} >>\nstream\n$content\nendstream") }
        val xref = out.size()
        w("xref\n0 6\n0000000000 65535 f \n")
        offsets.forEach { w("%010d 00000 n \n".format(it)) }
        w("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}
