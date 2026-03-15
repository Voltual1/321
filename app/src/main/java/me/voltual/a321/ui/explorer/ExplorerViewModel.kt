package me.voltual.a321.ui.explorer

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.voltual.a321.data.repository.PanRepository
import me.voltual.a321.core.utils.Util1DM
import me.voltual.a321.data.unified.PanFile
import me.voltual.a321.data.unified.PanPath

class ExplorerViewModel(
    private val repository: PanRepository
) : ViewModel() {

    var fileList by mutableStateOf<List<PanFile>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    // 路径栈
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

    fun loadFiles() {
        viewModelScope.launch {
            isLoading = true
            error = null
            repository.getFiles(currentPath.id)
                .onSuccess { fileList = it }
                .onFailure { error = it.message ?: "加载失败" }
            isLoading = false
        }
    }

    fun enterFolder(folder: PanFile) {
        if (folder.isDirectory) {
            pathStack = pathStack + PanPath(folder.id, folder.name)
            loadFiles()
        }
    }
    
    /**
 * 获取下载链接并调用 1DM+
 */
fun downloadFile(activity: android.app.Activity, file: PanFile) {
    viewModelScope.launch {
        // 1. 尝试获取直链
        val result = repository.getDownloadUrl(file)
        
        result.onSuccess { url ->
            try {
                // 2. 调用 1DM+ 工具类
                Util1DM.downloadFile(
                    activity = activity,
                    url = url,
                    secureUri = false,
                    askUserToInstall1DMIfNotInstalled = true
                )
            } catch (e: Exception) {
                error = "调用1DM失败: ${e.message}"
            }
        }.onFailure {
            error = "获取下载链接失败: ${it.message}"
        }
    }
}

    fun navigateBack(): Boolean {
        if (pathStack.size > 1) {
            pathStack = pathStack.dropLast(1)
            loadFiles()
            return true
        }
        return false
    }

    fun uploadFile(uri: Uri, name: String, size: Long) {
        viewModelScope.launch {
            isUploading = true
            uploadProgress = 0f
            uploadMessage = "准备上传..."

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
                uploadMessage = "上传完成"
                loadFiles()
            }.onFailure {
                uploadMessage = "上传失败: ${it.message}"
            }

            delay(3000)
            isUploading = false
        }
    }
}