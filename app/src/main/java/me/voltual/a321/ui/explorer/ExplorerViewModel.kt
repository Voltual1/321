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
import me.voltual.a321.data.unified.PanActionResult
import kotlinx.coroutines.flow.receiveAsFlow

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
                    selectedFileForAction = null // 操作完成后清理
                }
                "share" -> {showShareSheet()                }
                "rename" -> {
            hideActionMenu()
            showRenameDialog() // 触发显示输入框
        }
                "move" -> {
                // 1. 确定目标窗口：如果是从左往右移，目标就是右窗口；反之亦然
                val targetPane = if (pane == PaneIndex.LEFT) rightPane else leftPane
                val targetPathId = targetPane.currentPath.id
                
                // 2. 调用 Repository
                val result = repository.moveFiles(listOf(file.id), targetPathId)
                
                if (result is PanActionResult.Success) {
                    // 3. 移动成功后，两边都要刷新
                    loadFiles(PaneIndex.LEFT)
                    loadFiles(PaneIndex.RIGHT)
                    _events.send(ExplorerEvent.ShowSnackbar("已移动至 ${targetPane.currentPath.name}"))
                } else if (result is PanActionResult.Error) {
                    updatePaneError(pane, result.message)
                }
                selectedFileForAction = null
            }
                "info" -> {
                    // TODO: 显示文件详情弹窗
                }
            }
        }
    }
    
    // 控制重命名对话框显示
var isRenameDialogVisible by mutableStateOf(false)
    private set

fun showRenameDialog() {
    isRenameDialogVisible = true
}

fun hideRenameDialog() {
    isRenameDialogVisible = false
    // 注意：这里先不要清理 selectedFileForAction，因为对话框还需要它
}

/**
 * 提交重命名请求
 */
fun confirmRename(newName: String, pane: PaneIndex) {
    val file = selectedFileForAction ?: return
    hideRenameDialog()

    viewModelScope.launch {
        val result = repository.renameFile(file.id, newName)
        if (result is PanActionResult.Success) {
    loadFiles(pane)
    _events.send(ExplorerEvent.ShowSnackbar("重命名成功"))
}
// 失败时什么都不做
        selectedFileForAction = null // 流程结束，清理引用
    }
}    

var isShareSheetVisible by mutableStateOf(false)
    private set

fun showShareSheet() {
    // selectedFileForAction 已经在 showActionMenu 时赋值了
    isShareSheetVisible = true
}

fun hideShareSheet() {
    isShareSheetVisible = false
    selectedFileForAction = null // 在这里清理，因为分享流程彻底结束了
}

// 1. 明确 Channel 的类型
private val _events = kotlinx.coroutines.channels.Channel<ExplorerEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)

// 2. 修改 events 的声明方式，显式指定类型并调用扩展函数
val events: kotlinx.coroutines.flow.Flow<ExplorerEvent> = _events.receiveAsFlow()

// 最终提交分享的方法
fun confirmShare(password: String, expiration: String) {
    val file = selectedFileForAction ?: return
    hideShareSheet()

    viewModelScope.launch {
        repository.shareFiles(listOf(file.id), password, expiration)
            .onSuccess { shareKey ->
                val fullUrl = "https://www.123pan.com/s/$shareKey"
                // 发送事件：通知 UI 弹出 Snackbar 并包含链接
                _events.send(ExplorerEvent.ShowSnackbar("分享成功：$fullUrl", "复制"))
            }
            .onFailure { 
                _events.send(ExplorerEvent.ShowSnackbar("分享失败：${it.message}"))
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

// 定义一个事件包装类
sealed class ExplorerEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : ExplorerEvent()
}