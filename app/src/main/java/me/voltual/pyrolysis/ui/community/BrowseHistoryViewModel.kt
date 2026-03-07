//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.ui.community

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.core.database.BrowseHistoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class BrowseHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val browseHistoryRepository = BrowseHistoryRepository()
    
    private val _historyList = MutableStateFlow<List<BrowseHistory>>(emptyList())
    val historyList: StateFlow<List<BrowseHistory>> = _historyList.asStateFlow()
    
    private val _selectedItems = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItems: StateFlow<Set<Long>> = _selectedItems.asStateFlow()
    
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _copyEvent = MutableSharedFlow<Pair<String, Int>>()
    val copyEvent: SharedFlow<Pair<String, Int>> = _copyEvent.asSharedFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            browseHistoryRepository.allHistory.collect { history ->
                _historyList.value = history
            }
        }
    }

    fun toggleSelection(itemId: Long) {
        val currentSelected = _selectedItems.value
        _selectedItems.value = if (currentSelected.contains(itemId)) {
            currentSelected - itemId
        } else {
            currentSelected + itemId
        }
        if (_selectedItems.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    // 核心修正 #4: 添加缺失的 startSelectionMode 方法
    fun startSelectionMode(initialItemId: Long) {
        _isSelectionMode.value = true
        toggleSelection(initialItemId)
    }

    fun selectAll() {
        _selectedItems.value = _historyList.value.map { it.postId }.toSet()
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
    }

    fun invertSelection() {
        val allIds = _historyList.value.map { it.postId }.toSet()
        _selectedItems.value = allIds - _selectedItems.value
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val ids = _selectedItems.value.toList()
            browseHistoryRepository.deleteHistories(ids)
            clearSelection()
        }
    }

    fun copyShareLinks() {
        viewModelScope.launch {
            val selectedIds = _selectedItems.value
            if (selectedIds.isEmpty()) return@launch
            val histories = _historyList.value.filter { it.postId in selectedIds }
            
            val links = histories.joinToString("\n") { history ->
                "${history.title}：http://apk.xiaoqu.online/post/${history.postId}.html"
            }
            
            _copyEvent.emit(links to histories.size)
        }
    }
}