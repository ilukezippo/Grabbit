package com.ilukezippo.grabbit.core

object FileNames {
    private val bad = Regex("""[\\/:*?"<>|\r\n\t]""")
    private val spaces = Regex("""\s{2,}""")

    fun sanitize(name: String, max: Int = 80): String {
        val clean = name.replace(bad, "_").replace(spaces, " ").trim().trim('.')
        if (clean.isEmpty()) return "grabbit"
        val cps = clean.codePoints().toArray()
        return if (cps.size <= max) clean else String(cps, 0, max).trim()
    }
}
