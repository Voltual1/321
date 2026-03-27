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

            repository.getFilesWithTotal(state.currentPath.id, state.currentPage)
                .onSuccess { (total, newList) ->
                    state.totalCount = total

                    // 处理 ".." 返回目录逻辑：仅在第一页且非根目录时添加
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
            state.isLoading = false
        }
    }

    fun enterFolder(pane: PaneIndex, folder: PanFile) {
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

    // --- 弹窗控制逻辑 ---

    fun showActionMenu(file: PanFile) {
        selectedFileForAction = file
        isActionMenuVisible = true
    }

    fun hideActionMenu() { isActionMenuVisible = false }

    fun showRenameDialog() { isRenameDialogVisible = true }

    fun hideRenameDialog() { isRenameDialogVisible = false }

    fun confirmRename(newName: String, pane: PaneIndex) {
        val file = selectedFileForAction ?: return
        hideRenameDialog()

        viewModelScope.launch {
            val result = repository.renameFile(file.id, newName)
            if (result is PanActionResult.Success) {
                loadFiles(pane)
                _events.send(ExplorerEvent.ShowSnackbar("重命名成功"))
            } else if (result is PanActionResult.Error) {
                _events.send(ExplorerEvent.ShowSnackbar("重命名失败: ${result.message}"))
            }
            selectedFileForAction = null
        }
    }

    fun showPropertyDialog() { isPropertyDialogVisible = true }

    fun hidePropertyDialog() {
        isPropertyDialogVisible = false
        selectedFileForAction = null
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