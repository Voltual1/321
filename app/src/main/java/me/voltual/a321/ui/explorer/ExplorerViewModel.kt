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

    class PaneState {
        var fileList by mutableStateOf<List<PanFile>>(emptyList())
        var isLoading by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
        var pathStack by mutableStateOf(listOf(PanPath(0, "/")))
        val currentPath: PanPath get() = pathStack.last()
    }

    val leftPane = PaneState()
    val rightPane = PaneState()

    var activePane by mutableStateOf(PaneIndex.LEFT)
    
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
                .onSuccess { originalList ->
                    // 逻辑处理：如果是根目录(size == 1)，直接显示列表
                    // 如果不是根目录，在列表首位插入 ".." 文件夹
                    if (state.pathStack.size > 1) {
                        val upFolder = PanFile(
                            id = -1, // 使用特殊ID标识返回操作
                            name = "..",
                            isDirectory = true,
                            size = 0,
                            updateTime = ""
                        )
                        state.fileList = listOf(upFolder) + originalList
                    } else {
                        state.fileList = originalList
                    }
                }
                .onFailure { state.error = it.message ?: "加载失败" }
            state.isLoading = false
        }
    }

    fun enterFolder(pane: PaneIndex, folder: PanFile) {
        // 关键逻辑：如果是 ".."，执行返回操作
        if (folder.name == ".." && folder.id == -1L) {
            navigateBack(pane)
            return
        }

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
        // 如果是虚拟的 ".." 文件夹，不触发下载
        if (file.id == -1L) return
        
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