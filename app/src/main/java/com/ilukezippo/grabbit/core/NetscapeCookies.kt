package com.ilukezippo.grabbit.core

object NetscapeCookies {
    /** Converts a `name=value; name2=value2` header (as returned by CookieManager) to Netscape cookies.txt. */
    fun fromHeader(domain: String, header: String, expiry: Long): String = buildString {
        append("# Netscape HTTP Cookie File\n")
        header.split(';').map { it.trim() }.filter { '=' in it }.forEach { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=')
            append("$domain\tTRUE\t/\tTRUE\t$expiry\t$name\t$value\n")
        }
    }

    fun value(header: String?, name: String): String? =
        header?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
}
