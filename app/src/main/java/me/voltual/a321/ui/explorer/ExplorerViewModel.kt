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

    class PaneState {
        var fileList by mutableStateOf<List<PanFile>>(emptyList())
        var isLoading by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
        var pathStack by mutableStateOf(listOf(PanPath(0, "/")))
        val currentPath: PanPath get() = pathStack.last()
        var isRecycleBin by mutableStateOf(false)

        var currentPage by mutableIntStateOf(1)
        var totalCount by mutableIntStateOf(0)
        val pageSize = 100
        val totalPages: Int get() = ceil(totalCount.toDouble() / pageSize).toInt().coerceAtLeast(1)

        // 多选状态
        val selectedIds = mutableStateListOf<Long>()
        val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
        var lastSelectedIndex by mutableIntStateOf(-1)

        fun clearSelection() {
            selectedIds.clear()
            lastSelectedIndex = -1
        }
    }

    val leftPane = PaneState()
    val rightPane = PaneState()
    var activePane by mutableStateOf(PaneIndex.LEFT)

    val recentlyModifiedIds = mutableStateListOf<Long>()

    var isUploading by mutableStateOf(false)
    var uploadProgress by mutableStateOf(0f)
    var uploadMessage by mutableStateOf("")

    var isActionMenuVisible by mutableStateOf(false); private set
    var isRenameDialogVisible by mutableStateOf(false); private set
    var isPropertyDialogVisible by mutableStateOf(false); private set
    var isShareSheetVisible by mutableStateOf(false); private set
    var isDeleteDialogVisible by mutableStateOf(false); private set
    var selectedFileForAction by mutableStateOf<PanFile?>(null); private set

    private val _events = Channel<ExplorerEvent>(Channel.BUFFERED)
    val events: Flow<ExplorerEvent> = _events.receiveAsFlow()

    init {
        loadFiles(PaneIndex.LEFT)
        loadFiles(PaneIndex.RIGHT)
    }

    fun setActive(pane: PaneIndex) {
        if (activePane != pane) activePane = pane
    }

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

    fun showActionMenu(file: PanFile) {
        selectedFileForAction = file
        isActionMenuVisible = true
    }

    fun hideRenameDialog() { isRenameDialogVisible = false }
    fun showRenameDialog() { isRenameDialogVisible = true }

    fun performAction(action: String, paneIndex: PaneIndex) {
        val file = selectedFileForAction ?: return
        val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
        hideActionMenu()

        viewModelScope.launch {
            when (action) {
                "info" -> showPropertyDialog()
                "share" -> {
                    if (state.isRecycleBin) {
                        _events.send(ExplorerEvent.ShowSnackbar("回收站文件需恢复后分享"))
                    } else {
                        showShareSheet()
                    }
                }
                "rename" -> showRenameDialog()
                "move" -> {
                    val targetPaneIndex = if (paneIndex == PaneIndex.LEFT) PaneIndex.RIGHT else PaneIndex.LEFT
                    val targetPathId = (if (targetPaneIndex == PaneIndex.LEFT) leftPane else rightPane).currentPath.id
                    executeMoveWorkflow(file, paneIndex, targetPathId)
                }
                "delete" -> {
                    if (state.isRecycleBin) {
                        confirmDeletePermanently(paneIndex)
                    } else {
                        showDeleteDialog()
                    }
                }
                "restore" -> {
                    executeRestore(file, paneIndex)
                }
            }
        }
    }

    fun hideActionMenu() { isActionMenuVisible = false }

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

    fun loadFiles(
        pane: PaneIndex,
        isNextPage: Boolean = false,
        highlightIds: List<Long>? = null
    ) {
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

            val result = if (state.isRecycleBin) {
                repository.getRecycleBinFiles(state.currentPage)
            } else {
                repository.getFilesWithTotal(state.currentPath.id, state.currentPage)
            }

            if (result.isSuccess) {
                if (!isNextPage) {
                    recentlyModifiedIds.clear()
                }

                val (total, newList) = if (state.isRecycleBin) {
                    val res = result.getOrThrow() as me.voltual.a321.data.unified.PanPageResult
                    res.totalCount to res.files
                } else {
                    result.getOrThrow() as Pair<Int, List<PanFile>>
                }

                state.totalCount = total

                val processedList = if (!state.isRecycleBin && state.pathStack.size > 1 && state.currentPage == 1) {
                    val upFolder = PanFile(id = -1, name = "..", isDirectory = true, size = 0, updateTime = "")
                    listOf(upFolder) + newList
                } else if (state.isRecycleBin && state.currentPage == 1) {
                    val backItem = PanFile(id = -2, name = ".. [退出回收站]", isDirectory = true, size = 0, updateTime = "")
                    listOf(backItem) + newList
                } else {
                    newList
                }

                if (isNextPage) {
                    state.fileList = state.fileList + processedList
                } else {
                    state.fileList = processedList
                }

                highlightIds?.let { recentlyModifiedIds.addAll(it) }

            } else {
                state.error = result.exceptionOrNull()?.message ?: "加载失败"
                if (isNextPage) state.currentPage--
            }
            state.isLoading = false
        }
    }

    fun toggleRecycleBin(pane: PaneIndex) {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
        if (!state.isRecycleBin) {
            state.isRecycleBin = true
            state.pathStack = listOf(PanPath(-2, "回收站"))
        } else {
            state.isRecycleBin = false
            state.pathStack = listOf(PanPath(0, "/"))
        }
        loadFiles(pane)
    }

    fun enterFolder(pane: PaneIndex, folder: PanFile) {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
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
            loadFiles(pane)
        }
    }

    // 多选核心逻辑
    fun toggleSelection(pane: PaneIndex, index: Int) {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
        val file = state.fileList.getOrNull(index) ?: return
        if (file.id <= 0) return // 排除 ".." 和回收站退出项

        val fileId = file.id

        if (state.selectedIds.isEmpty()) {
            // 第一个选中点
            state.selectedIds.add(fileId)
            state.lastSelectedIndex = index
        } else {
            // 区间选择逻辑
            val start = state.lastSelectedIndex
            val end = index
            if (start != -1 && start != end) {
                val range = if (start < end) start..end else end..start
                range.forEach { i ->
                    val f = state.fileList.getOrNull(i)
                    if (f != null && f.id > 0 && !state.selectedIds.contains(f.id)) {
                        state.selectedIds.add(f.id)
                    }
                }
                state.lastSelectedIndex = end
            } else {
                // 单点反选
                if (state.selectedIds.contains(fileId)) {
                    state.selectedIds.remove(fileId)
                    if (state.selectedIds.isEmpty()) state.lastSelectedIndex = -1
                } else {
                    state.selectedIds.add(fileId)
                    state.lastSelectedIndex = index
                }
            }
        }
    }

    fun deleteSelectedFiles(pane: PaneIndex) {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
        val ids = state.selectedIds.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            val result = if (state.isRecycleBin) {
                repository.deleteFilesPermanently(ids)
            } else {
                repository.deleteFiles(ids)
            }

            if (result is PanActionResult.Success) {
                _events.send(ExplorerEvent.ShowSnackbar("成功操作 ${ids.size} 个文件"))
                state.clearSelection()
                loadFiles(pane)
            } else if (result is PanActionResult.Error) {
                _events.send(ExplorerEvent.ShowSnackbar("操作失败: ${result.message}"))
            }
        }
    }

    fun moveSelectedFiles(sourcePane: PaneIndex) {
        val sourceState = if (sourcePane == PaneIndex.LEFT) leftPane else rightPane
        val targetPane = if (sourcePane == PaneIndex.LEFT) rightPane else leftPane
        val ids = sourceState.selectedIds.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            val result = repository.moveFiles(ids, targetPane.currentPath.id)
            if (result is PanActionResult.Success) {
                if (sourceState.isRecycleBin) {
                    repository.restoreFiles(ids)
                    _events.send(ExplorerEvent.ShowSnackbar("已从回收站移出并恢复 ${ids.size} 个文件"))
                } else {
                    _events.send(ExplorerEvent.ShowSnackbar("已移动 ${ids.size} 个文件"))
                }
                sourceState.clearSelection()
                loadFiles(PaneIndex.LEFT)
                loadFiles(PaneIndex.RIGHT)
            } else if (result is PanActionResult.Error) {
                _events.send(ExplorerEvent.ShowSnackbar("移动失败: ${result.message}"))
            }
        }
    }

    // 以下为原有方法保持兼容
    private suspend fun executeMoveWorkflow(file: PanFile, sourcePane: PaneIndex, targetPathId: Long) {
        val state = if (sourcePane == PaneIndex.LEFT) leftPane else rightPane
        val moveResult = repository.moveFiles(listOf(file.id), targetPathId)

        if (moveResult is PanActionResult.Success) {
            if (state.isRecycleBin) {
                repository.restoreFiles(listOf(file.id))
                _events.send(ExplorerEvent.ShowSnackbar("已从回收站移出并恢复"))
            } else {
                _events.send(ExplorerEvent.ShowSnackbar("移动成功"))
            }
            loadFiles(PaneIndex.LEFT, highlightIds = listOf(file.id))
            loadFiles(PaneIndex.RIGHT, highlightIds = listOf(file.id))
        } else if (moveResult is PanActionResult.Error) {
            _events.send(ExplorerEvent.ShowSnackbar("移动失败: ${moveResult.message}"))
        }
        selectedFileForAction = null
    }

    private suspend fun executeRestore(file: PanFile, paneIndex: PaneIndex) {
        repository.restoreFiles(listOf(file.id))
        _events.send(ExplorerEvent.ShowSnackbar("文件已恢复至原位置"))
        loadFiles(paneIndex, highlightIds = listOf(file.id))
    }

    fun confirmRename(newName: String, paneIndex: PaneIndex) {
        val file = selectedFileForAction ?: return
        val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
        hideRenameDialog()

        viewModelScope.launch {
            val renameRes = repository.renameFile(file.id, newName)
            if (renameRes is PanActionResult.Success) {
                if (state.isRecycleBin) {
                    repository.restoreFiles(listOf(file.id))
                    _events.send(ExplorerEvent.ShowSnackbar("已重命名并恢复文件"))
                } else {
                    _events.send(ExplorerEvent.ShowSnackbar("重命名成功"))
                }
                loadFiles(paneIndex, highlightIds = listOf(file.id))
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