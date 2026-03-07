//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.KtorClient
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class CommunityViewModel : ViewModel() {
    private val _posts = MutableStateFlow(emptyList<KtorClient.Post>())
    val posts: StateFlow<List<KtorClient.Post>> = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    private var currentPage = 1
    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    fun jumpToPage(page: Int) {
        if (page in 1..totalPages.value) {
            _posts.value = emptyList()
            currentPage = page
            loadPosts()
        }
    }

    fun loadInitialData() {
        loadPosts()
    }

    fun loadNextPage() {
        if (currentPage < totalPages.value && !_isLoading.value) {
            currentPage++
            loadPosts()
        }
    }

    fun refresh() {
        currentPage = 1
        loadPosts()
    }

    private fun loadPosts() {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            try {
                val postsResult = KtorClient.ApiServiceImpl.getPostsList(
                    limit = PAGE_SIZE,
                    page = currentPage
                )

                if (postsResult.isSuccess) {
                    postsResult.getOrNull()?.let { postsResponse ->
                        if (postsResponse.code == 1) {
                            val data = postsResponse.data
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
                            _errorMessage.value = "加载失败: ${if (postsResponse.msg.isNotEmpty()) postsResponse.msg else "未知错误"}"
                        }
                    } ?: run {
                        _errorMessage.value = "加载失败: 响应为空"
                    }
                } else {
                    val exceptionMessage = postsResult.exceptionOrNull()?.message
                    _errorMessage.value = "加载失败: ${exceptionMessage ?: "未知错误"}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message ?: "未知错误"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 10
    }
}