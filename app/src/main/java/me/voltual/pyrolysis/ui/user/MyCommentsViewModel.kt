//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.feature.store.repository.IAppStoreRepository
import me.voltual.pyrolysis.data.unified.UnifiedComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.android.annotation.KoinViewModel
import kotlinx.coroutines.launch

@KoinViewModel
class MyCommentsViewModel(
    application: Application,
    private val repositories: Map<AppStore, IAppStoreRepository>
) : AndroidViewModel(application) {

    private val _comments = MutableStateFlow<List<UnifiedComment>>(emptyList())
    val comments: StateFlow<List<UnifiedComment>> = _comments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentPage = 1
    private var isLoadingMore = false
    private var hasMore = true

    // 硬编码为弦应用商店，因为只有它支持我的评论功能
    private val _selectedStore = MutableStateFlow(AppStore.SIENE_SHOP)
    val selectedStore: StateFlow<AppStore> = _selectedStore.asStateFlow()

    // 新增：评论删除确认对话框状态
    private val _showDeleteCommentDialog = MutableStateFlow<String?>(null)
    val showDeleteCommentDialog: StateFlow<String?> = _showDeleteCommentDialog.asStateFlow()

    private fun getRepository(): IAppStoreRepository {
        return repositories[selectedStore.value] ?: 
            throw IllegalStateException("Repository not found for ${selectedStore.value}")
    }

    fun initialize() {
        loadComments()
    }

    private fun loadComments() {
        if (isLoading.value || isLoadingMore) return

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val result = getRepository().getMyComments(currentPage)
                if (result.isSuccess) {
                    val data = result.getOrNull()
                    if (data != null) {
                        val (newComments, totalPages) = data
                        if (currentPage == 1) {
                            _comments.value = newComments
                        } else {
                            _comments.value = _comments.value + newComments
                        }
                        hasMore = currentPage < totalPages
                    }
                } else {
                    _error.value = "加载评论失败: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _error.value = "加载评论失败: ${e.message}"
            } finally {
                _isLoading.value = false
                isLoadingMore = false
            }
        }
    }

    fun loadMore() {
        if (!hasMore || isLoading.value || isLoadingMore) return
        
        isLoadingMore = true
        currentPage++
        loadComments()
    }

    fun refresh() {
        currentPage = 1
        hasMore = true
        loadComments()
    }

    // 显示删除评论确认对话框
    fun showDeleteCommentDialog(commentId: String) {
        _showDeleteCommentDialog.value = commentId
    }

    // 隐藏删除评论确认对话框
    fun hideDeleteCommentDialog() {
        _showDeleteCommentDialog.value = null
    }

    // 删除评论
    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            try {
                val result = getRepository().deleteComment(commentId)
                if (result.isSuccess) {
                    // 从列表中移除已删除的评论
                    _comments.value = _comments.value.filter { it.id != commentId }
                    _error.value = null
                } else {
                    _error.value = "删除评论失败: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _error.value = "删除评论失败: ${e.message}"
            } finally {
                hideDeleteCommentDialog()
            }
        }
    }
}