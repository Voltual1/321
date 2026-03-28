package me.voltual.a321.ui.explorer

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.voltual.a321.core.utils.Util1DM
import me.voltual.a321.data.repository.PanRepository
import me.voltual.a321.data.unified.PanActionResult
import me.voltual.a321.data.unified.PanFile
import me.voltual.a321.data.unified.PanPath
import kotlin.math.ceil

enum class PaneIndex { LEFT, RIGHT }

sealed class ExplorerEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : ExplorerEvent()
}

class ExplorerViewModel(
    private val repository: PanRepository
) : ViewModel() {

    // --- 内部状态类 ---
    class PaneState {
        var fileList by mutableStateOf<List<PanFile>>(emptyList())
        var isLoading by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
        var pathStack by mutableStateOf(listOf(PanPath(0, "/")))
        val currentPath: PanPath get() = pathStack.last()
        var isRecycleBin by mutableStateOf(false)

        // 分页状态
        var currentPage by mutableIntStateOf(1)
        var totalCount by mutableIntStateOf(0)
        val pageSize = 100 
        val totalPages: Int get() = ceil(totalCount.toDouble() / pageSize).toInt().coerceAtLeast(1)
    }

    // --- 响应式 UI 状态 ---
    val leftPane = PaneState()
    val rightPane = PaneState()
    var activePane by mutableStateOf(PaneIndex.LEFT)

    // 上传状态
    var isUploading by mutableStateOf(false)
    var uploadProgress by mutableStateOf(0f)
    var uploadMessage by mutableStateOf("")

    // 弹窗与交互状态
    var isActionMenuVisible by mutableStateOf(false) ; private set
    var isRenameDialogVisible by mutableStateOf(false) ; private set
    var isPropertyDialogVisible by mutableStateOf(false) ; private set
    var isShareSheetVisible by mutableStateOf(false) ; private set
    var isDeleteDialogVisible by mutableStateOf(false) ; private set
    var selectedFileForAction by mutableStateOf<PanFile?>(null) ; private set

    // 事件流
    private val _events = Channel<ExplorerEvent>(Channel.BUFFERED)
    val events: Flow<ExplorerEvent> = _events.receiveAsFlow()

    init {
        loadFiles(PaneIndex.LEFT)
        loadFiles(PaneIndex.RIGHT)
    }

    // --- 核心逻辑：文件加载与导航 ---

    fun setActive(pane: PaneIndex) {
        if (activePane != pane) activePane = pane
    }

    fun loadFiles(pane: PaneIndex, isNextPage: Boolean = false) {
    val state = if (pane == PaneIndex.LEFT) leftPane else rightPane

    viewModelScope.launch {
        if (isNextPage) {
            if (state.currentPage >= state.totalPages) return@launch
            state.currentPage++
        } else {
            state.isLoading = true
            state.currentPage = 1
        }

        state.error = null

        if (state.isRecycleBin) {
            // 加载回收站列表
            repository.getRecycleBinFiles(state.currentPage)
                .onSuccess { pageResult ->
                    // 修复点：使用 totalCount 匹配您的 PanPageResult 模型
                    state.totalCount = pageResult.totalCount
                    val newList = pageResult.files
                    
                    // 处理回收站的“返回”逻辑：在第一页添加一个特殊的退出项
                    val processedList = if (state.currentPage == 1) {
                        val backItem = PanFile(
                            id = -2, 
                            name = ".. [退出回收站]", 
                            isDirectory = true, 
                            size = 0, 
                            updateTime = ""
                        )
                        listOf(backItem) + newList
                    } else {
                        newList
                    }

                    if (isNextPage) {
                        state.fileList = state.fileList + newList
                    } else {
                        state.fileList = processedList
                    }
                }
                .onFailure {
                    state.error = it.message ?: "加载回收站失败"
                    if (isNextPage) state.currentPage--
                }
        } else {
            // 加载普通文件列表
            repository.getFilesWithTotal(state.currentPath.id, state.currentPage)
                .onSuccess { (total, newList) ->
                    state.totalCount = total

                    val processedList = if (state.pathStack.size > 1 && state.currentPage == 1) {
                        val upFolder = PanFile(id = -1, name = "..", isDirectory = true, size = 0, updateTime = "")
                        listOf(upFolder) + newList
                    } else {
                        newList
                    }

                    if (isNextPage) {
                        state.fileList = state.fileList + newList
                    } else {
                        state.fileList = processedList
                    }
                }
                .onFailure {
                    state.error = it.message ?: "加载失败"
                    if (isNextPage) state.currentPage--
                }
        }
        state.isLoading = false
    }
}

        /**
     * 进入或退出回收站
     */
    fun toggleRecycleBin(pane: PaneIndex) {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
        if (!state.isRecycleBin) {
            state.isRecycleBin = true
            // 进入回收站时，我们可以给 pathStack 加一个虚拟节点，或者清空它
            state.pathStack = listOf(PanPath(-2, "回收站"))
        } else {
            state.isRecycleBin = false
            state.pathStack = listOf(PanPath(0, "/"))
        }
        loadFiles(pane)
    }

    fun enterFolder(pane: PaneIndex, folder: PanFile) {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
        
        // 处理回收站中的特殊返回
        if (state.isRecycleBin && folder.id == -2L) {
            toggleRecycleBin(pane)
            return
        }

        if (folder.name == ".." && folder.id == -1L) {
            navigateBack(pane)
            return
        }

        if (folder.isDirectory) {
            state.pathStack = state.pathStack + PanPath(folder.id, folder.name)
            loadFiles(pane)
        }
    }

    fun navigateBack(pane: PaneIndex): Boolean {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
        
        // 如果在回收站，返回则退出回收站
        if (state.isRecycleBin) {
            toggleRecycleBin(pane)
            return true
        }

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
            state.currentPage = 1
            loadFiles(pane)
        }
    }

    // --- 文件操作：下载、重命名、删除、移动、分享 ---

    fun downloadFile(activity: android.app.Activity, file: PanFile) {
        if (file.id == -1L) return

        viewModelScope.launch {
            repository.getDownloadUrl(file).onSuccess { url ->
                runCatching {
                    Util1DM.downloadFile(activity, url, false, true)
                }.onFailure {
                    updatePaneError(activePane, "1DM 调用失败")
                }
            }
        }
    }

    fun performAction(action: String, pane: PaneIndex) {
        val file = selectedFileForAction ?: return
        hideActionMenu()

        viewModelScope.launch {
            when (action) {
                "delete" -> showDeleteDialog()
                "share"  -> showShareSheet()
                "rename" -> showRenameDialog()
                "info"   -> showPropertyDialog()
                "move"   -> {
                    val targetPane = if (pane == PaneIndex.LEFT) rightPane else leftPane
                    val result = repository.moveFiles(listOf(file.id), targetPane.currentPath.id)

                    if (result is PanActionResult.Success) {
                        loadFiles(PaneIndex.LEFT)
                        loadFiles(PaneIndex.RIGHT)
                        _events.send(ExplorerEvent.ShowSnackbar("已移动至 ${targetPane.currentPath.name}"))
                    } else if (result is PanActionResult.Error) {
                        _events.send(ExplorerEvent.ShowSnackbar("移动失败: ${result.message}"))
                    }
                    selectedFileForAction = null
                }
            }
        }
    }
    
    fun confirmDeletePermanently(paneIndex: PaneIndex) {
    val file = selectedFileForAction ?: return
    viewModelScope.launch {
        val result = repository.deleteFilesPermanently(listOf(file.id))
        if (result is PanActionResult.Success) {
            loadFiles(paneIndex)
            _events.send(ExplorerEvent.ShowSnackbar("文件已永久删除"))
        }
        hideDeleteDialog()
    }
}

    // --- 弹窗控制逻辑 ---

    fun showActionMenu(file: PanFile) {
        selectedFileForAction = file
        isActionMenuVisible = true
    }

    fun hideActionMenu() { isActionMenuVisible = false }

    fun showRenameDialog() { isRenameDialogVisible = true }

    fun hideRenameDialog() { isRenameDialogVisible = false }

    fun confirmRename(newName: String, paneIndex: PaneIndex) {
    val file = selectedFileForAction ?: return
    val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
    hideRenameDialog()

    viewModelScope.launch {
        // 1. 执行重命名
        val renameRes = repository.renameFile(file.id, newName)
        
        if (renameRes is PanActionResult.Success) {
            // 2. 如果在回收站，则自动执行恢复
            if (state.isRecycleBin) {
                repository.restoreFiles(listOf(file.id))
                _events.send(ExplorerEvent.ShowSnackbar("已重命名并恢复文件"))
            } else {
                _events.send(ExplorerEvent.ShowSnackbar("重命名成功"))
            }
            loadFiles(paneIndex)
        } else {
            _events.send(ExplorerEvent.ShowSnackbar("操作失败"))
        }
    }
}

    fun showPropertyDialog() { isPropertyDialogVisible = true }

    fun hidePropertyDialog() {
        isPropertyDialogVisible = false
        selectedFileForAction = null
    }

fun performMove(file: PanFile, paneIndex: PaneIndex) {
    val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
    val targetPane = if (paneIndex == PaneIndex.LEFT) rightPane else leftPane

    viewModelScope.launch {
        // 1. 执行移动
        val moveRes = repository.moveFiles(listOf(file.id), targetPane.currentPath.id)
        
        if (moveRes is PanActionResult.Success) {
            // 2. 如果是从回收站发起的移动，自动恢复
            if (state.isRecycleBin) {
                repository.restoreFiles(listOf(file.id))
                _events.send(ExplorerEvent.ShowSnackbar("已移动至 ${targetPane.currentPath.name} 并恢复"))
            } else {
                _events.send(ExplorerEvent.ShowSnackbar("已移动"))
            }
            loadFiles(PaneIndex.LEFT)
            loadFiles(PaneIndex.RIGHT)
        }
    }
}

    fun showShareSheet() { isShareSheetVisible = true }

    fun hideShareSheet() {
        isShareSheetVisible = false
        selectedFileForAction = null
    }

    fun confirmShare(password: String, expiration: String) {
        val file = selectedFileForAction ?: return
        hideShareSheet()

        viewModelScope.launch {
            repository.shareFiles(listOf(file.id), password, expiration)
                .onSuccess { shareKey ->
                    val fullUrl = "https://www.123pan.com/s/$shareKey"
                    _events.send(ExplorerEvent.ShowSnackbar("分享成功：$fullUrl", "复制"))
                }
                .onFailure {
                    _events.send(ExplorerEvent.ShowSnackbar("分享失败：${it.message}"))
                }
        }
    }

    fun showDeleteDialog() { isDeleteDialogVisible = true }

    fun hideDeleteDialog() { isDeleteDialogVisible = false }

    fun confirmDelete(pane: PaneIndex) {
        val file = selectedFileForAction ?: return
        hideDeleteDialog()

        viewModelScope.launch {
            val result = repository.deleteFiles(listOf(file.id))
            if (result is PanActionResult.Success) {
                loadFiles(pane)
                _events.send(ExplorerEvent.ShowSnackbar("已删除 ${file.name}"))
            } else if (result is PanActionResult.Error) {
                _events.send(ExplorerEvent.ShowSnackbar("删除失败: ${result.message}"))
            }
            selectedFileForAction = null
        }
    }

    // --- 辅助功能 ---

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

    private fun updatePaneError(pane: PaneIndex, message: String) {
        if (pane == PaneIndex.LEFT) leftPane.error = message else rightPane.error = message
    }
}