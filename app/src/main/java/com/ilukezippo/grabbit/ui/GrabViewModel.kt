package com.ilukezippo.grabbit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.core.Choice
import com.ilukezippo.grabbit.core.LinkExtractor
import com.ilukezippo.grabbit.download.DownloadSpec
import com.ilukezippo.grabbit.download.Downloads
import com.ilukezippo.grabbit.engine.MediaInfo
import com.ilukezippo.grabbit.engine.Resolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface GrabState {
    data object Idle : GrabState
    data object Loading : GrabState
    data class Loaded(val info: MediaInfo) : GrabState
    data class Error(val message: String) : GrabState
}

class GrabViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<GrabState>(GrabState.Idle)
    val state: StateFlow<GrabState> = _state
    val input = MutableStateFlow("")
    private var job: Job? = null

    fun grab(text: String = input.value) {
        val ctx = getApplication<Application>()
        val url = LinkExtractor.extract(text.trim()) ?: LinkExtractor.extract("https://" + text.trim().removePrefix("//"))
            ?.takeIf { '.' in text && ' ' !in text.trim() }
        if (url == null) { _state.value = GrabState.Error(ctx.getString(R.string.err_no_link)); return }
        input.value = url
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = GrabState.Loading
            _state.value = try {
                GrabState.Loaded(Resolver.resolve(ctx, url))
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                GrabState.Error(e.message ?: ctx.getString(R.string.err_unsupported))
            }
        }
    }

    fun download(info: MediaInfo, choice: Choice?) {
        Downloads.enqueue(getApplication(), DownloadSpec(info.url, info.title, info.thumbnail, choice, info.isLive, info.direct))
        reset()
    }

    /** Screenshots/tests only. */
    fun showForPreview(url: String, info: MediaInfo) { input.value = url; _state.value = GrabState.Loaded(info) }

    fun reset() {
        job?.cancel()
        _state.value = GrabState.Idle
        input.value = ""
    }
}
