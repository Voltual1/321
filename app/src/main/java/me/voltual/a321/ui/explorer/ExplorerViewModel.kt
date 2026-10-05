// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321.ui.explorer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.ceil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.voltual.a321.core.utils.Util1DM
import me.voltual.a321.data.DownloadSettingsDataStore
import me.voltual.a321.data.repository.PanRepository
import me.voltual.a321.data.unified.PanActionResult
import me.voltual.a321.data.unified.PanFile
import me.voltual.a321.data.unified.PanPageResult
import me.voltual.a321.data.unified.PanPath
import me.voltual.a321.data.unified.PanPlatform

enum class PaneIndex {
  LEFT,
  RIGHT,
}

sealed class ExplorerEvent {
  data class ShowSnackbar(val message: String, val actionLabel: String? = null) : ExplorerEvent()
}

class ExplorerViewModel(
  private val repository: PanRepository,
  private val downloadSettingsDataStore: DownloadSettingsDataStore
) : ViewModel() {

  class PaneState {
    var fileList by mutableStateOf<List<PanFile>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var pathStack by mutableStateOf(listOf(PanPath("0", "/")))
    val currentPath: PanPath
      get() = pathStack.last()

    var isRecycleBin by mutableStateOf(false)
    var isSearchMode by mutableStateOf(false)
    var searchKeyword by mutableStateOf("")
    var isShareListMode by mutableStateOf(false)

    var isExternalShareMode by mutableStateOf(false)
    var externalShareKey by mutableStateOf("")
    var externalSharePwd by mutableStateOf("")

    var shareNextMarker by mutableStateOf<String?>(null)
    var currentPage by mutableIntStateOf(1)
    var totalCount by mutableIntStateOf(0)
    val pageSize = 100
    val totalPages: Int
      get() = ceil(totalCount.toDouble() / pageSize).toInt().coerceAtLeast(1)

    val selectedIds = mutableStateListOf<String>()
    val isSelectionMode: Boolean
      get() = selectedIds.size > 1

    var lastSelectedIndex by mutableIntStateOf(-1)

    fun clearSelection() {
      selectedIds.clear()
      lastSelectedIndex = -1
    }

    fun resetToRoot() {
      fileList = emptyList()
      isLoading = false
      error = null
      pathStack = listOf(PanPath("0", "/"))
      isRecycleBin = false
      isSearchMode = false
      searchKeyword = ""
      isShareListMode = false
      isExternalShareMode = false
      externalShareKey = ""
      externalSharePwd = ""
      shareNextMarker = null
      currentPage = 1
      totalCount = 0
      clearSelection()
    }
  }

  val leftPane = PaneState()
  val rightPane = PaneState()
  var activePane by mutableStateOf(PaneIndex.LEFT)

  var currentPlatform by mutableStateOf(PanPlatform.PAN123)
    private set

  val recentlyModifiedIds = mutableStateListOf<String>()

  var isUploading by mutableStateOf(false)
  var uploadProgress by mutableStateOf(0f)
  var uploadMessage by mutableStateOf("")

  var isActionMenuVisible by mutableStateOf(false)
    private set

  var isRenameDialogVisible by mutableStateOf(false)
    private set

  var isPropertyDialogVisible by mutableStateOf(false)
    private set

  var isShareSheetVisible by mutableStateOf(false)
    private set

  var isDeleteDialogVisible by mutableStateOf(false)
    private set

  var selectedFileForAction by mutableStateOf<PanFile?>(null)
    private set

  var isBatchActionMenu by mutableStateOf(false)
    private set

  var sharingFileIds by mutableStateOf<List<String>>(emptyList())
  var sharingDisplayName by mutableStateOf("")

  var isCreateFileDialogVisible by mutableStateOf(false)
    private set

  var pendingUploadName by mutableStateOf("")

  var isSearchDialogVisible by mutableStateOf(false)
    private set

  var isLinkInputDialogVisible by mutableStateOf(false)
    private set

  private val _events = Channel<ExplorerEvent>(Channel.BUFFERED)
  val events: Flow<ExplorerEvent> = _events.receiveAsFlow()

  init {
    viewModelScope.launch {
      currentPlatform = repository.getActivePlatform()
      loadFiles(PaneIndex.LEFT)
      loadFiles(PaneIndex.RIGHT)
    }
  }

  fun switchPlatform(platform: PanPlatform) {
    if (currentPlatform == platform) return
    viewModelScope.launch {
      repository.switchPlatform(platform)
      currentPlatform = platform
      leftPane.resetToRoot()
      rightPane.resetToRoot()
      loadFiles(PaneIndex.LEFT)
      loadFiles(PaneIndex.RIGHT)
    }
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
        "copy_share_info" -> {
          val url = file.shareUrl ?: file.rawDownloadUrl
          val code = file.sharePwd
          val textToCopy = if (!code.isNullOrBlank()) "$url 提取码: $code" else url
          _events.send(ExplorerEvent.ShowSnackbar("分享链接及提取码：$textToCopy", "复制"))
        }
        "save_to_other" -> {
          val targetPaneIndex = if (paneIndex == PaneIndex.LEFT) PaneIndex.RIGHT else PaneIndex.LEFT
          val targetPane = if (targetPaneIndex == PaneIndex.LEFT) leftPane else rightPane

          if (
            targetPane.isExternalShareMode || targetPane.isRecycleBin || targetPane.isShareListMode
          ) {
            _events.send(ExplorerEvent.ShowSnackbar("目标窗口必须是常规目录"))
          } else {
            executeCopyExternalFiles(targetIds, paneIndex, targetPane.currentPath.id)
          }
        }
        "cancel_share" -> {
          executeCancelShare(targetIds, paneIndex)
        }
        "share" -> {
          if (state.isRecycleBin) {
            _events.send(ExplorerEvent.ShowSnackbar("回收站文件需恢复后分享"))
          } else {
            sharingFileIds = targetIds
            sharingDisplayName = if (isBatch) "已选择 ${targetIds.size} 个项目" else file.name
            showShareSheet()
          }
        }
        "rename" -> showRenameDialog()
        "move" -> {
          val targetPaneIndex = if (paneIndex == PaneIndex.LEFT) PaneIndex.RIGHT else PaneIndex.LEFT
          val targetPathId =
            (if (targetPaneIndex == PaneIndex.LEFT) leftPane else rightPane).currentPath.id
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

  private suspend fun executeCopyExternalFiles(
    ids: List<String>,
    sourcePaneIndex: PaneIndex,
    targetPathId: String,
  ) {
    val sourceState = if (sourcePaneIndex == PaneIndex.LEFT) leftPane else rightPane
    val targetPaneIndex = if (sourcePaneIndex == PaneIndex.LEFT) PaneIndex.RIGHT else PaneIndex.LEFT

    val selectedFiles = sourceState.fileList.filter { ids.contains(it.id) }

    val result =
      repository.copyShareFiles(
        shareKey = sourceState.externalShareKey,
        sharePwd = sourceState.externalSharePwd,
        targetParentId = targetPathId,
        files = selectedFiles,
      )

    if (result is PanActionResult.Success) {
      _events.send(ExplorerEvent.ShowSnackbar("成功保存 ${ids.size} 个文件到另一侧"))
      sourceState.clearSelection()
      loadFiles(targetPaneIndex)
    } else if (result is PanActionResult.Error) {
      _events.send(ExplorerEvent.ShowSnackbar("保存失败: ${result.message}"))
    }
  }

  private suspend fun executeCancelShare(ids: List<String>, paneIndex: PaneIndex) {
    val state = if (paneIndex == PaneIndex.LEFT) leftPane else rightPane
    var successCount = 0
    ids.forEach { id ->
      val result = repository.deleteShare(id)
      if (result is PanActionResult.Success) successCount++
    }
    if (successCount > 0) {
      _events.send(ExplorerEvent.ShowSnackbar("已取消 $successCount 个分享"))
      state.clearSelection()
      loadFiles(paneIndex)
    } else {
      _events.send(ExplorerEvent.ShowSnackbar("取消分享失败"))
    }
  }

  private suspend fun executeMoveWorkflow(
    ids: List<String>,
    sourcePane: PaneIndex,
    targetPathId: String,
  ) {
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

  private suspend fun executeRestore(ids: List<String>, paneIndex: PaneIndex) {
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

  fun loadFiles(pane: PaneIndex, isNextPage: Boolean = false, highlightIds: List<String>? = null) {
    val state = if (pane == PaneIndex.LEFT) leftPane else rightPane

    viewModelScope.launch {
      if (isNextPage) {
        if (state.isShareListMode || state.isExternalShareMode) {
          if (state.shareNextMarker == "-1") return@launch
        } else {
          if (state.currentPage >= state.totalPages) return@launch
          state.currentPage++
        }
      } else {
        state.isLoading = true
        state.currentPage = 1
        state.shareNextMarker =
          when {
            state.isExternalShareMode -> "1"
            state.isShareListMode -> "0"
            else -> null
          }
      }

      state.error = null

      val result: Result<PanPageResult> =
        when {
          state.isExternalShareMode -> {
            repository.getShareInfo(
              shareKey = state.externalShareKey,
              next = if (isNextPage) state.shareNextMarker else "1",
              parentId = state.currentPath.id,
              page = state.currentPage,
              sharePwd = state.externalSharePwd.takeIf { it.isNotBlank() },
            )
          }
          state.isRecycleBin -> {
            repository.getRecycleBinFiles(state.currentPage)
          }
          state.isSearchMode -> {
            val searchParentId =
              if (state.pathStack.size > 1) {
                state.pathStack[state.pathStack.size - 2].id
              } else "0"
            repository.searchFiles(
              keyword = state.searchKeyword,
              page = state.currentPage,
              parentId = searchParentId,
            )
          }
          state.isShareListMode -> {
            repository.getShareList(next = if (isNextPage) state.shareNextMarker else null)
          }
          else -> {
            repository.getFilesWithTotal(state.currentPath.id, state.currentPage)
          }
        }

      result
        .onSuccess { pageResult ->
          if (!isNextPage) {
            recentlyModifiedIds.clear()
          }

          state.totalCount = pageResult.totalCount
          state.shareNextMarker = pageResult.nextMarker

          val newList = pageResult.files

          val processedList =
            if (!isNextPage) {
              when {
                state.isExternalShareMode -> {
                  val prefix = if (state.pathStack.size > 1) ".. [share]" else ".. [exit_share]"
                  listOf(
                    PanFile(
                      id = "-5",
                      name = prefix,
                      isDirectory = true,
                      size = 0,
                      updateTime = "",
                      rawDownloadUrl = "",
                      etag = "",
                    )
                  ) + newList
                }
                state.isRecycleBin -> {
                  listOf(
                    PanFile(
                      id = "-2",
                      name = ".. [trash]",
                      isDirectory = true,
                      size = 0,
                      updateTime = "",
                      rawDownloadUrl = "",
                      etag = "",
                    )
                  ) + newList
                }
                state.isSearchMode -> {
                  listOf(
                    PanFile(
                      id = "-3",
                      name = ".. [search_results]",
                      isDirectory = true,
                      size = 0,
                      updateTime = "",
                      rawDownloadUrl = "",
                      etag = "",
                    )
                  ) + newList
                }
                state.isShareListMode -> {
                  listOf(
                    PanFile(
                      id = "-4",
                      name = ".. [shares]",
                      isDirectory = true,
                      size = 0,
                      updateTime = "",
                      rawDownloadUrl = "",
                      etag = "",
                    )
                  ) + newList
                }
                state.pathStack.size > 1 -> {
                  listOf(
                    PanFile(
                      id = "-1",
                      name = "..",
                      isDirectory = true,
                      size = 0,
                      updateTime = "",
                      rawDownloadUrl = "",
                      etag = "",
                    )
                  ) + newList
                }
                else -> newList
              }
            } else {
              newList
            }

          if (isNextPage) {
            state.fileList = state.fileList + processedList
          } else {
            state.fileList = processedList
          }

          highlightIds?.let { recentlyModifiedIds.addAll(it) }
          state.isLoading = false
        }
        .onFailure { exception ->
          state.error = exception.message ?: "加载失败"
          if (isNextPage && !state.isShareListMode && !state.isExternalShareMode) {
            state.currentPage--
          }
          state.isLoading = false
        }
    }
  }

  fun openExternalShare(url: String, pwd: String, pane: PaneIndex) {
    val key = when (currentPlatform) {
      PanPlatform.PAN123 -> url.substringAfterLast("/s/").substringBefore("?").substringBefore("/")
      PanPlatform.CLOUD139 -> url.substringAfterLast("/i/").substringBefore("?").substringBefore("/")
    }

    if (key.isBlank()) return

    val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
    state.isRecycleBin = false
    state.isSearchMode = false
    state.isShareListMode = false
    state.isExternalShareMode = true
    state.externalShareKey = key
    state.externalSharePwd = pwd
    state.pathStack = listOf(PanPath("0", "分享: $key"))
    loadFiles(pane)
  }

  fun exitExternalShare(pane: PaneIndex) {
    val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
    state.isExternalShareMode = false
    state.externalShareKey = ""
    state.externalSharePwd = ""
    state.pathStack = listOf(PanPath("0", "/"))
    loadFiles(pane)
  }

  fun toggleShareList(pane: PaneIndex) {
    val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
    if (!state.isShareListMode) {
      state.isRecycleBin = false
      state.isSearchMode = false
      state.isExternalShareMode = false
      state.isShareListMode = true
      state.pathStack = listOf(PanPath("-4", "我的分享"))
    } else {
      state.isShareListMode = false
      state.pathStack = listOf(PanPath("0", "/"))
    }
    loadFiles(pane)
  }

  fun toggleSearch(pane: PaneIndex, keyword: String) {
    val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
    if (keyword.isNotBlank()) {
      state.isRecycleBin = false
      state.isShareListMode = false
      state.isExternalShareMode = false
      state.isSearchMode = true
      state.searchKeyword = keyword
      state.pathStack = state.pathStack + PanPath("-3", "搜索: $keyword")
      loadFiles(pane)
    }
  }

  fun exitSearch(pane: PaneIndex) {
    val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
    if (state.isSearchMode) {
      state.isSearchMode = false
      state.searchKeyword = ""
      state.pathStack = state.pathStack.dropLast(1)
      loadFiles(pane)
    }
  }

  fun toggleRecycleBin(pane: PaneIndex) {
    val state = if (pane == PaneIndex.LEFT) leftPane else rightPane
    if (!state.isRecycleBin) {
      state.isShareListMode = false
      state.isSearchMode = false
      state.isExternalShareMode = false
      state.isRecycleBin = true
      state.pathStack = listOf(PanPath("-2", "回收站"))
    } else {
      state.isRecycleBin = false
      state.pathStack = listOf(PanPath("0", "/"))
    }
    loadFiles(pane)
  }

  fun enterFolder(pane: PaneIndex, folder: PanFile) {
    val state = if (pane == PaneIndex.LEFT) leftPane else rightPane

    if (state.isExternalShareMode && folder.id == "-5") {
      if (state.pathStack.size > 1) {
        navigateBack(pane)
      } else {
        exitExternalShare(pane)
      }
      return
    }
    if (state.isRecycleBin && folder.id == "-2") {
      toggleRecycleBin(pane)
      return
    }
    if (state.isSearchMode && folder.id == "-3") {
      exitSearch(pane)
      return
    }
    if (state.isShareListMode && folder.id == "-4") {
      toggleShareList(pane)
      return
    }

    if (folder.name == ".." && folder.id == "-1") {
      navigateBack(pane)
      return
    }

    if (folder.isDirectory) {
      state.pathStack = state.pathStack + PanPath(folder.id, folder.name)
      if (state.isSearchMode) {
        state.isSearchMode = false
        state.searchKeyword = ""
        val newStack = state.pathStack.toMutableList()
        if (newStack.size >= 2 && newStack[newStack.size - 2].id == "-3") {
          newStack.removeAt(newStack.size - 2)
          state.pathStack = newStack
        }
      }
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
    if (state.isSearchMode) {
      exitSearch(pane)
      return true
    }
    if (state.isRecycleBin) {
      toggleRecycleBin(pane)
      return true
    }
    if (state.isShareListMode) {
      toggleShareList(pane)
      return true
    }
    if (state.isExternalShareMode && state.pathStack.size == 1) {
      exitExternalShare(pane)
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
    if (file.id == "-1" || file.id == "-2" || file.id == "-3" || file.id == "-4" || file.id == "-5")
      return

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
          if (f != null && f.id != "-1" && f.id != "-2" && f.id != "-3" && f.id != "-4" && f.id != "-5" && !state.selectedIds.contains(f.id)) {
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
      repository
        .shareFiles(ids, password, expiration)
        .onSuccess { fullUrl ->
          _events.send(ExplorerEvent.ShowSnackbar("分享成功：$fullUrl", "复制"))
          leftPane.clearSelection()
          rightPane.clearSelection()
        }
        .onFailure { _events.send(ExplorerEvent.ShowSnackbar("分享失败：${it.message}")) }
    }
  }

  fun uploadFile(uri: Uri, defaultName: String, size: Long, targetPane: PaneIndex) {
    val state = if (targetPane == PaneIndex.LEFT) leftPane else rightPane

    val finalName =
      if (pendingUploadName.isNotBlank()) {
        pendingUploadName
      } else {
        defaultName
      }

    viewModelScope.launch {
      isUploading = true
      uploadProgress = 0f
      uploadMessage = "上传 $finalName 至 ${state.currentPath.name}..."

      repository
        .uploadFile(
          uri = uri,
          fileName = finalName,
          fileSize = size,
          parentId = state.currentPath.id,
          onProgress = { uploadProgress = it },
        )
        .onSuccess {
          loadFiles(targetPane)
          _events.send(ExplorerEvent.ShowSnackbar("上传成功: $finalName"))
        }
        .onFailure { _events.send(ExplorerEvent.ShowSnackbar("上传失败: ${it.message}")) }
      delay(2000)
      isUploading = false
      pendingUploadName = ""
    }
  }

  fun downloadFile(activity: android.app.Activity, file: PanFile) {
    if (file.id.startsWith("-")) return
    viewModelScope.launch {
      repository.getDownloadUrl(file).onSuccess { url ->
        val mode = downloadSettingsDataStore.loadDownloadMode()
        when (mode) {
          DownloadSettingsDataStore.MODE_1DM -> {
            runCatching {
              Util1DM.downloadFile(activity, url, false, true)
              _events.send(ExplorerEvent.ShowSnackbar("正在调起 1DM 下载..."))
            }.onFailure {
              updatePaneError(activePane, "1DM 调用失败")
              _events.send(ExplorerEvent.ShowSnackbar("调起 1DM 失败，请检查是否安装"))
            }
          }
          DownloadSettingsDataStore.MODE_COPY_LINK -> {
            runCatching {
              val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val clip = ClipData.newPlainText("Download Link", url)
              clipboard.setPrimaryClip(clip)
              _events.send(ExplorerEvent.ShowSnackbar("下载直链已成功复制到剪贴板"))
            }.onFailure {
              _events.send(ExplorerEvent.ShowSnackbar("直链复制失败"))
            }
          }
          DownloadSettingsDataStore.MODE_BROWSER -> {
            runCatching {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              activity.startActivity(intent)
              _events.send(ExplorerEvent.ShowSnackbar("正在调起浏览器打开链接..."))
            }.onFailure {
              _events.send(ExplorerEvent.ShowSnackbar("无法调起浏览器，打开失败"))
            }
          }
        }
      }.onFailure {
        _events.send(ExplorerEvent.ShowSnackbar("获取直链失败: ${it.message}"))
      }
    }
  }

  fun hideRenameDialog() {
    isRenameDialogVisible = false
  }

  fun showRenameDialog() {
    isRenameDialogVisible = true
  }

  fun showPropertyDialog() {
    isPropertyDialogVisible = true
  }

  fun hidePropertyDialog() {
    isPropertyDialogVisible = false
  }

  fun showShareSheet() {
    isShareSheetVisible = true
  }

  fun hideShareSheet() {
    isShareSheetVisible = false
  }

  fun showDeleteDialog() {
    isDeleteDialogVisible = true
  }

  fun hideDeleteDialog() {
    isDeleteDialogVisible = false
  }

  fun showSearchDialog() {
    isSearchDialogVisible = true
  }

  fun hideSearchDialog() {
    isSearchDialogVisible = false
  }

  fun showLinkInputDialog() {
    isLinkInputDialogVisible = true
  }

  fun hideLinkInputDialog() {
    isLinkInputDialogVisible = false
  }

  private fun updatePaneError(pane: PaneIndex, message: String) {
    if (pane == PaneIndex.LEFT) leftPane.error = message else rightPane.error = message
  }
}