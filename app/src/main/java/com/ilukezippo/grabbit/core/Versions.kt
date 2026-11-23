package com.ilukezippo.grabbit.core

object Versions {
    private fun parts(v: String): List<Int>? {
        val p = v.trim().removePrefix("v").removePrefix("V").split('.')
        return p.map { it.toIntOrNull() ?: return null }
    }

    /** True when [latest] is a higher version than [current] ("v1.0.2" > "1.0.1"). Unparseable → false. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The APK asset that matches the phone's CPU (arm64 / armv7), else any APK. */
    fun pickApk(names: List<String>, abi: String): String? {
        val apks = names.filter { it.endsWith(".apk", true) }
        val want = if (abi.startsWith("arm64")) listOf("arm64") else if (abi.startsWith("armeabi")) listOf("armv7", "armeabi") else emptyList()
        return apks.firstOrNull { n -> want.any { n.contains(it, true) } } ?: apks.firstOrNull { !it.contains("arm64", true) && abi.startsWith("armeabi") } ?: apks.firstOrNull()
    }
}
