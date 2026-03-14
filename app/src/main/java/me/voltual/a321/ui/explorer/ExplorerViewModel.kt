package me.voltual.a321.ui.explorer

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.voltual.a321.data.repository.PanRepository
import me.voltual.a321.data.unified.PanFile
import me.voltual.a321.data.unified.PanPath

class ExplorerViewModel(
    private val repository: PanRepository
) : ViewModel() {

    // 文件列表状态
    var fileList by mutableStateOf<List<PanFile>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    // 路径栈（第一个元素通常是“根目录”，id = 0）
    var pathStack by mutableStateOf(listOf(PanPath(0, "全部文件")))
        private set

    val currentPath: PanPath get() = pathStack.last()

    // 上传状态
    var isUploading by mutableStateOf(false)
        private set
    var uploadProgress by mutableStateOf(0f)
        private set
    var uploadMessage by mutableStateOf("")
        private set

    init {
        loadFiles()
    }

    /**
     * 加载当前目录文件
     */
    fun loadFiles() {
        viewModelScope.launch {
            isLoading = true
            repository.getFiles(currentPath.id)
                .onSuccess { fileList = it }
                .onFailure { /* 处理错误，例如通过 Channel 发送 Toast 事件 */ }
            isLoading = false
        }
    }

    /**
     * 进入文件夹
     */
    fun enterFolder(folder: PanFile) {
        if (folder.isDirectory) {
            pathStack = pathStack + PanPath(folder.id, folder.name)
            loadFiles()
        }
    }

    /**
     * 返回上级
     */
    fun navigateBack(): Boolean {
        if (pathStack.size > 1) {
            pathStack = pathStack.dropLast(1)
            loadFiles()
            return true
        }
        return false
    }

    /**
     * 执行上传
     * 注意：fileName 和 fileSize 建议从 SimpleStorage 的 DocumentFile 中获取后传入
     */
    fun uploadFile(uri: Uri, name: String, size: Long) {
        viewModelScope.launch {
            isUploading = true
            uploadProgress = 0f
            uploadMessage = "正在准备上传..."

            repository.uploadFile(
                uri = uri,
                fileName = name,
                fileSize = size,
                parentId = currentPath.id,
                onProgress = { progress ->
                    uploadProgress = progress
                    uploadMessage = "正在上传: ${(progress * 100).toInt()}%"
                }
            ).onSuccess {
                uploadMessage = "上传成功"
                loadFiles() // 刷新列表
            }.onFailure {
                uploadMessage = "上传失败: ${it.message}"
            }

            // 延迟关闭进度条显示
            kotlinx.coroutines.delay(2000)
            isUploading = false
        }
    }
}