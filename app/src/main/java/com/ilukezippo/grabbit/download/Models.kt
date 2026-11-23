package com.ilukezippo.grabbit.download

import com.ilukezippo.grabbit.core.Choice
import com.ilukezippo.grabbit.engine.DirectItem

enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELED }

data class SavedFile(val uri: String, val name: String, val mime: String, val size: Long)

data class DownloadSpec(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val choice: Choice?,
    val isLive: Boolean = false,
    val direct: List<DirectItem> = emptyList(),
)

data class DownloadTask(
    val id: String,
    val spec: DownloadSpec,
    val status: Status = Status.QUEUED,
    val progress: Float = -1f,
    val etaSec: Long = -1,
    val detail: String = "",
    val error: String? = null,
    val files: List<SavedFile> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val active get() = status == Status.QUEUED || status == Status.RUNNING
}
