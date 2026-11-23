package com.ilukezippo.grabbit

import android.content.Context

object BuildConfigInfo {
    fun versionName(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
}
