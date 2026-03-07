//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.settings.storage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.data.StorageSettingsDataStore
import me.voltual.pyrolysis.data.content.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import java.io.File

@KoinViewModel
class StoreManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val storageSettingsDataStore = StorageSettingsDataStore(application)

    // 缓存大小状态（格式化后的字符串，如 "12.5 MB"）
    private val _cacheSize = MutableStateFlow("0 B")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _isSuperCacheEnabled = MutableStateFlow(false)
    val isSuperCacheEnabled: StateFlow<Boolean> = _isSuperCacheEnabled.asStateFlow()

    init {
        updateCacheSize()
        viewModelScope.launch {
            storageSettingsDataStore.isSuperCacheEnabledFlow.collect { isEnabled ->
                _isSuperCacheEnabled.value = isEnabled
            }
        }
    }

    /**
     * 计算 Cache 类涉及的所有目录总大小
     */
    fun updateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val dirs = listOf(
                Cache.getImagesDir(context),
                Cache.getTemporaryFile(context).parentFile,
                File(context.externalCacheDir, "partial"),
                File(context.externalCacheDir, "releases"),
                File(context.externalCacheDir, "download_stats"),
                File(context.cacheDir, "index")
            ).filterNotNull()

            var totalBytes = 0L
            dirs.forEach { totalBytes += getFolderSize(it) }
            
            _cacheSize.value = formatFileSize(totalBytes)
        }
    }

    /**
     * 清理所有应用定义的缓存
     */
    fun clearAppCache(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            // 清理内部和外部缓存目录下的所有内容
            context.cacheDir.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()
            
            // 重新计算大小并回调 UI
            updateCacheSize()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (!file.isDirectory) return file.length()
        return file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

}