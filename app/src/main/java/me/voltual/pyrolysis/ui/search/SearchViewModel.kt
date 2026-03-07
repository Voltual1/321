//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。


package me.voltual.pyrolysis.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.BBQApplication
import me.voltual.pyrolysis.KtorClient 
import kotlinx.coroutines.flow.*
import org.koin.android.annotation.KoinViewModel
import me.voltual.pyrolysis.data.SearchHistoryDataStore
import me.voltual.pyrolysis.data.UserFilterDataStore
import kotlinx.coroutines.launch

enum class SearchMode {
    POSTS,
    HISTORY,
    LOGS
}

sealed class SearchResultItem {
    data class PostItem(val post: KtorClient.Post) : SearchResultItem()
    data class HistoryItem(val history: me.voltual.pyrolysis.ui.community.BrowseHistory) : SearchResultItem()
    data class LogItem(val log: me.voltual.pyrolysis.core.database.LogEntry) : SearchResultItem()
}

@KoinViewModel
class SearchViewModel(
    private val searchHistoryDataStore: SearchHistoryDataStore,
    private val userFilterDataStore: UserFilterDataStore 
) : ViewModel() {
    private val browseHistoryDao = BBQApplication.instance.database.browseHistoryDao()
    private val logDao = BBQApplication.instance.database.logDao()

    private val _searchMode = MutableStateFlow(SearchMode.POSTS)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    // 分页相关状态
    private var currentPage = 1
    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()
    
    // 用户筛选信息（多个用户）
    private val _allUserFilters = MutableStateFlow<Map<Long, String>>(emptyMap())
    val allUserFilters: StateFlow<Map<Long, String>> = _allUserFilters.asStateFlow()
    
    private val _activeUserId = MutableStateFlow<Long?>(null)
    val activeUserId: StateFlow<Long?> = _activeUserId.asStateFlow()
    
    private val _activeNickname = MutableStateFlow<String?>(null)
    val activeNickname: StateFlow<String?> = _activeNickname.asStateFlow()
    
    // 是否处于用户筛选模式
    private val _isUserFilterMode = MutableStateFlow(false)
    val isUserFilterMode: StateFlow<Boolean> = _isUserFilterMode.asStateFlow()
    
    private val _hasMoreData = MutableStateFlow(true)
    val hasMoreData: StateFlow<Boolean> = _hasMoreData.asStateFlow()

    val searchHistory: StateFlow<List<String>> = searchHistoryDataStore.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
    
    init {
        viewModelScope.launch {
            // 监听所有用户筛选信息变化
            userFilterDataStore.allUserFiltersFlow.collect { userMap ->
                _allUserFilters.value = userMap
                // 如果当前没有激活用户但有用户，设置第一个为激活用户
                if (_activeUserId.value == null && userMap.isNotEmpty()) {
                    val firstUser = userMap.entries.firstOrNull()
                    firstUser?.let { (userId, nickname) ->
                        _activeUserId.value = userId
                        _activeNickname.value = nickname
                        _isUserFilterMode.value = true
                    }
                }
            }
        }
        
        viewModelScope.launch {
            // 监听激活用户变化
            userFilterDataStore.activeUserIdFlow.collect { userId ->
                _activeUserId.value = userId
                if (userId != null) {
                    // 从用户映射中获取昵称
                    val nickname = _allUserFilters.value[userId]
                    _activeNickname.value = nickname
                    _isUserFilterMode.value = nickname != null
                } else {
                    _activeNickname.value = null
                    _isUserFilterMode.value = false
                }
            }
        }
    }
    
    // 设置激活的用户筛选
    fun setActiveUserFilter(userId: Long?) {
        viewModelScope.launch {
            userFilterDataStore.setActiveUserFilter(userId)
        }
    }
    
    // 移除用户筛选
    fun removeUserFilter(userId: Long) {
        viewModelScope.launch {
            userFilterDataStore.removeUserFilter(userId)
            // 如果移除的是当前激活用户，则设置另一个用户为激活
            if (_activeUserId.value == userId) {
                val nextUser = _allUserFilters.value.keys.firstOrNull { it != userId }
                userFilterDataStore.setActiveUserFilter(nextUser)
            }
        }
    }
    
    // 清除用户筛选（清除激活状态）
    fun clearUserFilter() {
        viewModelScope.launch {
            userFilterDataStore.setActiveUserFilter(null)
        }
    }
    
    // 新清除所有用户筛选
    fun clearAllUserFilters() {
        viewModelScope.launch {
            userFilterDataStore.clearAllUserFilters()
        }
    }
    
    // 从导航参数初始化用户筛选
    fun initFromNavArgs(userId: Long?, nickname: String?) {
        viewModelScope.launch {
            if (userId != null && nickname != null) {
                userFilterDataStore.addOrUpdateUserFilter(userId, nickname)
                userFilterDataStore.setActiveUserFilter(userId)
            }
        }
    }

    fun onSearchModeChange(newMode: SearchMode) {
        _searchMode.value = newMode
        // 切换模式时重置分页状态
        resetPagination()
        if (_query.value.isNotBlank()) {
            performSearch()
        }
    }

    fun submitSearch(submittedQuery: String) {
        val query = submittedQuery.ifBlank { _query.value }
        if (query.isBlank()) return

        onQueryChange(query)
        viewModelScope.launch {
            searchHistoryDataStore.addSearchQuery(query)
        }
        // 重置分页状态并执行搜索
        resetPagination()
        performSearch()
    }

    // 加载更多数据
    fun loadMore() {
        // 添加更严格的检查条件
        if (!_isLoading.value && _hasMoreData.value && _query.value.isNotBlank() && _searchMode.value == SearchMode.POSTS) {
            currentPage++
            performSearch(loadMore = true)
        }
    }

    // 跳转到指定页面
    fun jumpToPage(page: Int) {
        if (page in 1.._totalPages.value && !_isLoading.value) {
            currentPage = page
            _searchResults.value = emptyList() // 清空当前结果
            performSearch()
        }
    }

    // 重置分页状态
    private fun resetPagination() {
        currentPage = 1
        _searchResults.value = emptyList()
        _totalPages.value = 1
        _hasMoreData.value = true
        _errorMessage.value = null
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryDataStore.clearAllHistory()
        }
    }

    private fun performSearch(loadMore: Boolean = false) {
        // 防止重复搜索
        if (_isLoading.value) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                when (_searchMode.value) {
                    SearchMode.POSTS -> searchPosts(loadMore)
                    SearchMode.HISTORY -> searchLocalHistory()
                    SearchMode.LOGS -> searchLocalLogs()
                }
            } catch (e: Exception) {
                _errorMessage.value = "搜索时发生错误: ${e.message}"
                // 加载失败时回退页码
                if (loadMore) {
                    currentPage--
                    _hasMoreData.value = true // 重置为true以便重试
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun searchPosts(loadMore: Boolean = false) {
        val response = KtorClient.ApiServiceImpl.searchPosts(
            query = _query.value, 
            page = currentPage, 
            limit = PAGE_SIZE,
            userId = if (_isUserFilterMode.value) _activeUserId.value else null  // 只在筛选模式下添加 userId
        )
        
        response.onSuccess { result ->
            result.data.let { data ->
                _totalPages.value = data.pagecount
                _hasMoreData.value = currentPage < data.pagecount
                
                val newPosts = data.list.map { SearchResultItem.PostItem(it) }
                _searchResults.value = if (loadMore && _searchResults.value.isNotEmpty()) {
                    // 确保正确合并结果
                    _searchResults.value + newPosts
                } else {
                    newPosts
                }
                _errorMessage.value = null
            }
        }.onFailure { error ->
            val errorMsg = "帖子搜索失败: ${error.message ?: "未知错误"}"
            _errorMessage.value = errorMsg
            if (loadMore) currentPage-- // 回退页码
        }
    }

    private suspend fun searchLocalHistory() {
        // 本地搜索不分页，一次性加载所有
        val historyList = browseHistoryDao.searchHistory("%${_query.value}%").first()
        _searchResults.value = historyList.map { SearchResultItem.HistoryItem(it) }
        _hasMoreData.value = false
        _totalPages.value = 1
        _errorMessage.value = null
    }

    private suspend fun searchLocalLogs() {
        // 本地搜索不分页，一次性加载所有
        val logList = logDao.searchLogs("%${_query.value}%").first()
        _searchResults.value = logList.map { SearchResultItem.LogItem(it) }
        _hasMoreData.value = false
        _totalPages.value = 1
        _errorMessage.value = null
    }

    companion object {
        private const val PAGE_SIZE = 20 // 每页显示数量
    }
}