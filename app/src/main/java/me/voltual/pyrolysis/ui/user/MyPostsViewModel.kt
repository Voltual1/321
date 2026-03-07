//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.KtorClient
import java.io.IOException
import org.koin.android.annotation.KoinViewModel
import me.voltual.pyrolysis.data.UserFilterDataStore

@KoinViewModel
class MyPostsViewModel(private val userFilterDataStore: UserFilterDataStore) : ViewModel() {
    private val _posts = MutableStateFlow<List<KtorClient.Post>>(emptyList())
    val posts: StateFlow<List<KtorClient.Post>> = _posts.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private var _nickname: String? = null
    
    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()
    
    private var currentPage = 1
    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()
    
    // 添加状态跟踪，避免重复初始化
    private var _currentUserId: Long? = null
    private var _isInitialized = false
    
    // 设置用户ID
    fun setUserId(userId: Long) {
        setUserInfo(userId, "用户")
    }
    
    // 修改接收 nickname
    fun setUserInfo(userId: Long, nickname: String) {
        // 只有当用户ID真正改变时才重置状态
        if (_currentUserId != userId || _nickname != nickname) {
            _currentUserId = userId
            _nickname = nickname
            _isInitialized = false
            resetState()
            
            // 将用户筛选信息存储到 DataStore - 修复这里的方法名
            viewModelScope.launch {
                userFilterDataStore.addOrUpdateUserFilter(userId, nickname)
                // 同时设置为激活用户
                userFilterDataStore.setActiveUserFilter(userId)
            }
            
            loadDataIfNeeded()
        }
    }
    
    private fun resetState() {
        _posts.value = emptyList()
        currentPage = 1
        _totalPages.value = 1
        _errorMessage.value = ""
    }
    
    // 只在需要时加载数据
    private fun loadDataIfNeeded() {
        if (!_isInitialized && _currentUserId != null && !_isLoading.value) {
            _isInitialized = true
            loadMyPosts()
        }
    }
    
    fun jumpToPage(page: Int) {
        if (page in 1..totalPages.value) {
            _posts.value = emptyList()
            currentPage = page
            loadMyPosts()
        }
    }
    
    fun loadNextPage() {
        if (currentPage < totalPages.value && !_isLoading.value) {
            currentPage++
            loadMyPosts()
        }
    }
    
    fun refresh() {
        // 刷新时重置页面但不重置初始化状态
        currentPage = 1
        loadMyPosts()
    }
    
    private fun loadMyPosts() {
        if (_isLoading.value || _currentUserId == null) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            
            try {
                val myPostsResult = KtorClient.ApiServiceImpl.getPostsList(
                    limit = PAGE_SIZE,
                    page = currentPage,
                    userId = _currentUserId!!
                )
                
                if (myPostsResult.isSuccess) {
                    myPostsResult.getOrNull()?.let { response ->
                        if (response.code == 1) {
                            val data = response.data
                            _totalPages.value = data.pagecount
                            
                            val newPosts = if (currentPage == 1) {
                                data.list
                            } else {
                                _posts.value + data.list
                            }
                            
                            // 数据去重
                            val distinctPosts = newPosts.distinctBy { it.postid }
                            _posts.value = distinctPosts
                            _errorMessage.value = ""
                        } else {
                            _errorMessage.value = "操作失败: ${if (response.msg.isNotEmpty()) response.msg else "服务器错误"}"
                        }
                    } ?: run {
                        _errorMessage.value = "加载失败: 响应为空"
                    }
                } else {
                    val exceptionMessage = myPostsResult.exceptionOrNull()?.message
                    _errorMessage.value = "加载失败: ${exceptionMessage ?: "未知错误"}"
                }
            } catch (e: IOException) {
                _errorMessage.value = "网络异常: ${e.message ?: "未知错误"}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    companion object {
        private const val PAGE_SIZE = 10
    }
}