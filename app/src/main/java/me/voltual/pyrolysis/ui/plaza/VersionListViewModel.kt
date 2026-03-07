//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.plaza

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.feature.store.repository.IAppStoreRepository
import me.voltual.pyrolysis.data.unified.UnifiedAppItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class VersionListViewModel(
    application: Application,
    private val repositories: Map<AppStore, IAppStoreRepository>
) : AndroidViewModel(application) {

    private val _versions = MutableStateFlow<List<UnifiedAppItem>>(emptyList())
    val versions: StateFlow<List<UnifiedAppItem>> = _versions

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var currentPackageName: String = ""
    private var currentStore: AppStore = AppStore.XIAOQU_SPACE
    
    // 记录当前页码
    private var currentPage = 1

    fun loadVersions(packageName: String, store: AppStore, page: Int = 1) {
        // 避免重复加载
        if (currentPackageName == packageName && currentStore == store && 
            _versions.value.isNotEmpty() && page == currentPage) {
            return
        }

        currentPackageName = packageName
        currentStore = store
        currentPage = page
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val repository = repositories[store]
                    ?: throw IllegalArgumentException("Unsupported store: $store")

                // 统一逻辑：使用带 page 的重载，并提取 List 部分
                val result: Result<List<UnifiedAppItem>> = repository
                    .getAppVersionsByPackageName(packageName, page)
                    .map { it.first }

                if (result.isSuccess) {
                    val newItems = result.getOrThrow()
                    // 目前 UI 逻辑：直接覆盖当前列表
                    _versions.value = newItems
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to load versions"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "An error occurred"
            } finally {
                _isLoading.value = false
            }
        }
    }
}