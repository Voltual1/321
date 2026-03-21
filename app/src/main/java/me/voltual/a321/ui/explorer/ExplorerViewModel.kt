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

    // 新增分页状态
    var currentPage by mutableIntStateOf(1)
    var totalCount by mutableIntStateOf(0)
    val pageSize = 4 // 对应 API 中的 limit
    val totalPages: Int get() = kotlin.math.ceil(totalCount.toDouble() / pageSize).toInt().coerceAtLeast(1)
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

    fun loadFiles(pane: PaneIndex, isNextPage: Boolean = false) {
    val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
    
    viewModelScope.launch {
        if (isNextPage) {
            if (state.currentPage >= state.totalPages) return@launch
            state.currentPage++
        } else {
            state.isLoading = true // 仅在首次加载或切换目录时显示全屏加载
            state.currentPage = 1
        }
        
        state.error = null
        
        // 调用 Repository，传入当前页码
        repository.getFilesWithTotal(state.currentPath.id, state.currentPage)
            .onSuccess { (total, newList) ->
                state.totalCount = total
                
                // 处理 ".." 返回目录逻辑
                val processedList = if (state.pathStack.size > 1 && state.currentPage == 1) {
                    val upFolder = PanFile(id = -1, name = "..", isDirectory = true, size = 0, updateTime = "")
                    listOf(upFolder) + newList
                } else {
                    newList
                }

                if (isNextPage) {
                    state.fileList = state.fileList + newList // 追加
                } else {
                    state.fileList = processedList // 覆盖
                }
            }
            .onFailure { 
                state.error = it.message ?: "加载失败" 
                if (isNextPage) state.currentPage-- // 失败时回退页码
            }
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
        state.currentPage = 1 // 重置页码
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