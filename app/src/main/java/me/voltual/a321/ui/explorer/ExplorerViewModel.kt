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
    val pageSize = 100 // 对应 API 中的 limit
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
    
    // 在 ExplorerViewModel.kt 中添加
var isActionMenuVisible by mutableStateOf(false)
    private set
var selectedFileForAction by mutableStateOf<PanFile?>(null)
    private set

fun showActionMenu(file: PanFile) {
    selectedFileForAction = file
    isActionMenuVisible = true
}

fun hideActionMenu() {
    isActionMenuVisible = false
    selectedFileForAction = null
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
    
    /**
     * 执行文件操作（从 ActionMenu 触发）
     */
    fun performAction(action: String, pane: PaneIndex) {
        val file = selectedFileForAction ?: return
        hideActionMenu() // 执行前先关闭菜单

        viewModelScope.launch {
            when (action) {
                "delete" -> {
                    // 批量接口也支持单文件删除
                    val result = repository.deleteFiles(listOf(file.id))
                    if (result is PanActionResult.Success) {
                        loadFiles(pane) // 刷新当前侧列表
                    } else if (result is PanActionResult.Error) {
                        updatePaneError(pane, result.message)
                    }
                }
                "share" -> {
                    repository.shareFiles(listOf(file.id))
                        .onSuccess { shareKey ->
                            // 这里可以弹出一个 Dialog 显示链接，或者直接复制到剪贴板
                            uploadMessage = "分享成功，Key: $shareKey" 
                            // 暂时借用上传消息提示，实际建议用 Snackbar
                        }
                        .onFailure { updatePaneError(pane, it.message ?: "分享失败") }
                }
                "rename" -> {
                    // TODO: 需要弹出重命名输入框，逻辑类似 createFolder
                }
                "info" -> {
                    // TODO: 显示文件详情弹窗
                }
            }
        }
    }

    private fun updatePaneError(pane: PaneIndex, message: String) {
        if (pane == PaneIndex.LEFT) leftPane.error = message else rightPane.error = message
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