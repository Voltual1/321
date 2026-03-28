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
        
        val isSelectionMode: Boolean get() = selectedIds.size > 1
        
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
    var isBatchActionMenu by mutableStateOf(false); private set
    
    // 新增：批量分享专用
    var sharingFileIds by mutableStateOf<List<Long>>(emptyList())
    var sharingDisplayName by mutableStateOf("")
    
    var isCreateFileDialogVisible by mutableStateOf(false); private set
    var pendingUploadName by mutableStateOf("")

    private val _events = Channel<ExplorerEvent>(Channel.BUFFERED)
    val events: Flow<ExplorerEvent> = _events.receiveAsFlow()

    init {
        loadFiles(PaneIndex.LEFT)
        loadFiles(PaneIndex.RIGHT)
    }

    fun setActive(pane: PaneIndex) {
        if (activePane != pane) activePane = pane
    }

    fun showActionMenu(file: PanFile, paneIndex: PaneIndex) {
        val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
        if (state.selectedIds.contains(file.id)) {
            isBatchActionMenu = state.selectedIds.size > 1
            selectedFileForAction = file
        } else {
            state.clearSelection()
            state.selectedIds.add(file.id)
            isBatchActionMenu = false
            selectedFileForAction = file
        }
        isActionMenuVisible = true
    }

    fun hideActionMenu() {
        isActionMenuVisible = false
    }

    fun performAction(action: String, paneIndex: PaneIndex) {
        val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
        val file = selectedFileForAction ?: return
        val isBatch = isBatchActionMenu && state.selectedIds.size > 1
        val targetIds = if (isBatch) state.selectedIds.toList() else listOf(file.id)

        hideActionMenu()

        viewModelScope.launch {
            when (action) {
                "info" -> showPropertyDialog()
                "share" -> {
                    if (state.isRecycleBin) {
                        _events.send(ExplorerEvent.ShowSnackbar("回收站文件需恢复后分享"))
                    } else {
                        // 设置分享数据
                        sharingFileIds = targetIds
                        sharingDisplayName = if (isBatch) "已选择 ${targetIds.size} 个项目" else file.name
                        showShareSheet()
                    }
                }
                "rename" -> showRenameDialog()
                "move" -> {
                    val targetPaneIndex = if (paneIndex == PaneIndex.LEFT) PaneIndex.RIGHT else PaneIndex.LEFT
                    val targetPathId = (if (targetPaneIndex == PaneIndex.LEFT) leftPane else rightPane).currentPath.id
                    executeMoveWorkflow(targetIds, paneIndex, targetPathId)
                }
                "delete" -> {
                    if (state.isRecycleBin) {
                        confirmDeletePermanently(paneIndex)
                    } else {
                        showDeleteDialog()
                    }
                }
                "restore" -> {
                    executeRestore(targetIds, paneIndex)
                }
            }
        }
    }

    private suspend fun executeMoveWorkflow(ids: List<Long>, sourcePane: PaneIndex, targetPathId: Long) {
        val state = if (sourcePane == PaneIndex.LEFT) leftPane else rightPane
        val moveResult = repository.moveFiles(ids, targetPathId)

        if (moveResult is PanActionResult.Success) {
            if (state.isRecycleBin) {
                repository.restoreFiles(ids)
                _events.send(ExplorerEvent.ShowSnackbar("已恢复并移动 ${ids.size} 个文件"))
            } else {
                _events.send(ExplorerEvent.ShowSnackbar("成功移动 ${ids.size} 个文件"))
            }
            state.clearSelection()
            loadFiles(PaneIndex.LEFT, highlightIds = ids)
            loadFiles(PaneIndex.RIGHT, highlightIds = ids)
        } else if (moveResult is PanActionResult.Error) {
            _events.send(ExplorerEvent.ShowSnackbar("移动失败: ${moveResult.message}"))
        }
    }

    private suspend fun executeRestore(ids: List<Long>, paneIndex: PaneIndex) {
        val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
        repository.restoreFiles(ids)
        _events.send(ExplorerEvent.ShowSnackbar("${ids.size} 个文件已恢复至原位置"))
        state.clearSelection()
        loadFiles(paneIndex, highlightIds = ids)
    }

    fun confirmDelete(paneIndex: PaneIndex) {
        val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
        val isBatch = isBatchActionMenu && state.selectedIds.size > 1
        val ids = if (isBatch) state.selectedIds.toList() else listOfNotNull(selectedFileForAction?.id)

        hideDeleteDialog()
        viewModelScope.launch {
            val result = repository.deleteFiles(ids)
            if (result is PanActionResult.Success) {
                _events.send(ExplorerEvent.ShowSnackbar("已删除 ${ids.size} 个文件"))
                state.clearSelection()
                loadFiles(paneIndex)
            } else if (result is PanActionResult.Error) {
                _events.send(ExplorerEvent.ShowSnackbar("删除失败: ${result.message}"))
            }
        }
    }

    fun confirmDeletePermanently(paneIndex: PaneIndex) {
        val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
        val isBatch = isBatchActionMenu && state.selectedIds.size > 1
        val ids = if (isBatch) state.selectedIds.toList() else listOfNotNull(selectedFileForAction?.id)

        hideDeleteDialog()
        viewModelScope.launch {
            val result = repository.deleteFilesPermanently(ids)
            if (result is PanActionResult.Success) {
                _events.send(ExplorerEvent.ShowSnackbar("已永久删除 ${ids.size} 个文件"))
                state.clearSelection()
                loadFiles(paneIndex)
            }
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
    
    fun showCreateFileDialog() {
        pendingUploadName = ""
        isCreateFileDialogVisible = true
    }

    fun hideCreateFileDialog() {
        isCreateFileDialogVisible = false
    }

    fun prepareUpload(customName: String) {
        pendingUploadName = customName
        hideCreateFileDialog()
    }

    fun confirmCreateFolder(name: String, paneIndex: PaneIndex) {
        if (name.isBlank()) return
        
        val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
        hideCreateFileDialog()

        viewModelScope.launch {
            val result = repository.createFolder(name, state.currentPath.id)
            if (result is PanActionResult.Success) {
                _events.send(ExplorerEvent.ShowSnackbar("文件夹 '$name' 创建成功"))
                loadFiles(paneIndex)
            } else if (result is PanActionResult.Error) {
                _events.send(ExplorerEvent.ShowSnackbar("创建失败: ${result.message}"))
            }
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

    fun toggleSelection(pane: PaneIndex, index: Int) {
        val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
        val file = state.fileList.getOrNull(index) ?: return
        if (file.id <= 0) return

        val fileId = file.id

        if (state.selectedIds.isEmpty()) {
            state.selectedIds.add(fileId)
            state.lastSelectedIndex = index
        } else {
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
                state.clearSelection()
                loadFiles(paneIndex, highlightIds = listOf(file.id))
            } else {
                _events.send(ExplorerEvent.ShowSnackbar("操作失败"))
            }
        }
    }

    fun confirmShare(password: String, expiration: String) {
        val ids = sharingFileIds
        if (ids.isEmpty()) return
        
        hideShareSheet()

        viewModelScope.launch {
            repository.shareFiles(ids, password, expiration)
                .onSuccess { shareKey ->
                    val fullUrl = "https://www.123pan.com/s/$shareKey"
                    _events.send(ExplorerEvent.ShowSnackbar("分享成功：$fullUrl", "复制"))
                    leftPane.clearSelection()
                    rightPane.clearSelection()
                }
                .onFailure {
                    _events.send(ExplorerEvent.ShowSnackbar("分享失败：${it.message}"))
                }
        }
    }

    fun uploadFile(uri: Uri, defaultName: String, size: Long, targetPane: PaneIndex) {
        val state = if (targetPane == PaneIndex.LEFT) leftPane else rightPane
        
        val finalName = if (pendingUploadName.isNotBlank()) {
            pendingUploadName
        } else {
            defaultName
        }

        viewModelScope.launch {
            isUploading = true
            uploadProgress = 0f
            uploadMessage = "上传 $finalName 至 ${state.currentPath.name}..."

            repository.uploadFile(
                uri = uri,
                fileName = finalName,
                fileSize = size,
                parentId = state.currentPath.id,
                onProgress = { uploadProgress = it }
            ).onSuccess {
                loadFiles(targetPane)
                _events.send(ExplorerEvent.ShowSnackbar("上传成功: $finalName"))
            }.onFailure {
                _events.send(ExplorerEvent.ShowSnackbar("上传失败: ${it.message}"))
            }
            delay(2000)
            isUploading = false
            pendingUploadName = ""
        }
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

    fun hideRenameDialog() { isRenameDialogVisible = false }
    fun showRenameDialog() { isRenameDialogVisible = true }
    fun showPropertyDialog() { isPropertyDialogVisible = true }
    fun hidePropertyDialog() { isPropertyDialogVisible = false }
    fun showShareSheet() { isShareSheetVisible = true }
    fun hideShareSheet() { isShareSheetVisible = false }
    fun showDeleteDialog() { isDeleteDialogVisible = true }
    fun hideDeleteDialog() { isDeleteDialogVisible = false }

    private fun updatePaneError(pane: PaneIndex, message: String) {
        if (pane == PaneIndex.LEFT) leftPane.error = message else rightPane.error = message
    }
}