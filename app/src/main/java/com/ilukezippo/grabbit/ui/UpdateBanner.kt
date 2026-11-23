package com.ilukezippo.grabbit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilukezippo.grabbit.BuildConfigInfo
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.update.AppUpdate
import com.ilukezippo.grabbit.update.AppUpdater
import kotlinx.coroutines.launch

/** "Grabbit x.y is available" card with an Update button that downloads and opens the installer. */
@Composable
fun UpdateBanner(u: AppUpdate) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(G.gradientSoft).border(1.dp, G.violet.copy(alpha = .5f), shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(G.gradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.upd_title, u.version), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(stringResource(R.string.upd_desc, BuildConfigInfo.versionName(ctx)), color = G.muted, fontSize = 13.sp)
            }
        }
        val p = progress
        if (p != null) {
            Text(stringResource(R.string.upd_downloading, p.toInt()), color = G.text, fontSize = 13.sp)
            LinearProgressIndicator({ p / 100f }, Modifier.fillMaxWidth(), color = G.pink, trackColor = G.surface2)
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientButton({
                if (!AppUpdater.canInstall(ctx)) {
                    message = ctx.getString(R.string.upd_allow)
                    AppUpdater.askInstallPermission(ctx)
                    return@GradientButton
                }
                message = null; progress = 0f
                scope.launch {
                    runCatching { AppUpdater.downloadAndInstall(ctx, u) { progress = it } }
                        .onFailure { message = ctx.getString(R.string.upd_failed, it.message ?: "") }
                    progress = null
                }
            }, Modifier.weight(1f)) { Text(stringResource(R.string.upd_now)) }
            TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.pageUrl))) }) { Text(stringResource(R.string.upd_whats_new)) }
        }
        message?.let { Text(it, color = G.muted, fontSize = 13.sp) }
    }
}
