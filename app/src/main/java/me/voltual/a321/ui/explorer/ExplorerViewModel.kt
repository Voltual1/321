package me.voltual.a321.ui.explorer

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.voltual.a321.data.repository.PanRepository
import me.voltual.a321.core.utils.Util1DM
import me.voltual.a321.data.unified.PanFile
import me.voltual.a321.data.unified.PanPath

enum class PaneIndex { LEFT, RIGHT }

class ExplorerViewModel(
    private val repository: PanRepository
) : ViewModel() {

    // 窗口状态封装
    class PaneState {
        var fileList by mutableStateOf<List<PanFile>>(emptyList())
        var isLoading by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
        var pathStack by mutableStateOf(listOf(PanPath(0, "/")))
        val currentPath: PanPath get() = pathStack.last()
    }

    val leftPane = PaneState()
    val rightPane = PaneState()

    // 全局上传状态（通常逻辑上一个应用同时处理一个主上传流，或根据焦点窗口处理）
    var activePane by mutableStateOf(PaneIndex.LEFT)
    
    // 提供一个明确的方法来切换激活状态
    fun setActive(pane: PaneIndex) {
        if (activePane != pane) {
            activePane = pane
        }
    }
    
    var isUploading by mutableStateOf(false)
    var uploadProgress by mutableStateOf(0f)
    var uploadMessage by mutableStateOf("")

    init {
        loadFiles(PaneIndex.LEFT)
        loadFiles(PaneIndex.RIGHT)
    }

    fun loadFiles(pane: PaneIndex) {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
        viewModelScope.launch {
            state.isLoading = true
            state.error = null
            repository.getFiles(state.currentPath.id)
                .onSuccess { state.fileList = it }
                .onFailure { state.error = it.message ?: "加载失败" }
            state.isLoading = false
        }
    }

    fun enterFolder(pane: PaneIndex, folder: PanFile) {
        if (folder.isDirectory) {
            val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
            state.pathStack = state.pathStack + PanPath(folder.id, folder.name)
            loadFiles(pane)
        }
    }

    fun navigateBack(pane: PaneIndex): Boolean {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
        if (state.pathStack.size > 1) {
            state.pathStack = state.pathStack.dropLast(1)
            loadFiles(pane)
            return true
        }
        return false
    }

    fun navigateToPath(pane: PaneIndex, path: PanPath) {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
        val index = state.pathStack.indexOf(path)
        if (index != -1) {
            state.pathStack = state.pathStack.take(index + 1)
            loadFiles(pane)
        }
    }

    fun downloadFile(activity: android.app.Activity, file: PanFile) {
        viewModelScope.launch {
            val result = repository.getDownloadUrl(file)
            result.onSuccess { url ->
                try {
                    Util1DM.downloadFile(activity, url, false, true)
                } catch (e: Exception) {
                    if (activePane == PaneIndex.LEFT) leftPane.error = "1DM失败" else rightPane.error = "1DM失败"
                }
            }.onFailure {
                // 错误处理...
            }
        }
    }

    fun uploadFile(uri: Uri, name: String, size: Long, targetPane: PaneIndex) {
        val state = if (targetPane == PaneIndex.LEFT) leftPane else rightPane
        viewModelScope.launch {
            isUploading = true
            uploadProgress = 0f
            uploadMessage = "上传至 ${state.currentPath.name}..."

            repository.uploadFile(
                uri = uri,
                fileName = name,
                fileSize = size,
                parentId = state.currentPath.id,
                onProgress = { uploadProgress = it }
            ).onSuccess {
                loadFiles(targetPane)
            }
            delay(2000)
            isUploading = false
        }
    }
}