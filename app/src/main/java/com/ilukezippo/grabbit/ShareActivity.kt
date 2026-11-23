package com.ilukezippo.grabbit

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilukezippo.grabbit.ui.*

/** Quick bottom sheet shown when a link is shared to Grabbit from another app. */
class ShareActivity : AppCompatActivity() {
    private val vm: GrabViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) vm.grab(intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString ?: "")
        setContent {
            GrabbitTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                ModalBottomSheet(onDismissRequest = { finish() }, containerColor = G.bg, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Grabbit", style = gradientTitle.copy(fontSize = 24.sp))
                        when (val s = state) {
                            is GrabState.Loaded -> GrabPanel(s.info) { choice ->
                                vm.download(s.info, choice)
                                Toast.makeText(this@ShareActivity, R.string.added_queue, Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            is GrabState.Error -> {
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(G.red.copy(alpha = .12f)).padding(14.dp)) {
                                    Icon(Icons.Rounded.ErrorOutline, null, tint = G.red); Spacer(Modifier.width(10.dp)); Text(s.message)
                                }
                                OutlinedButton({ finish() }, Modifier.fillMaxWidth()) { Text(getString(R.string.cancel)) }
                            }
                            else -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = G.violet)
                                    Spacer(Modifier.height(12.dp)); Text(getString(R.string.fetching), color = G.muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
