//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.community

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.core.database.BrowseHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlinx.coroutines.flow.first

@KoinViewModel
class PostDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _postDetail = MutableStateFlow<KtorClient.PostDetail?>(null)
    val postDetail: StateFlow<KtorClient.PostDetail?> = _postDetail.asStateFlow()

    private val _comments = MutableStateFlow<List<KtorClient.Comment>>(emptyList())
    val comments: StateFlow<List<KtorClient.Comment>> = _comments.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    private val _deleteCommentSuccess = MutableStateFlow(false)
    val deleteCommentSuccess: StateFlow<Boolean> = _deleteCommentSuccess.asStateFlow()

    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked.asStateFlow()

    private val _likeCount = MutableStateFlow(0)
    val likeCount: StateFlow<Int> = _likeCount.asStateFlow()

    private val _commentCount = MutableStateFlow(0)
    val commentCount: StateFlow<Int> = _commentCount.asStateFlow()

    private val _showCommentDialog = MutableStateFlow(false)
    val showCommentDialog: StateFlow<Boolean> = _showCommentDialog.asStateFlow()

    private val _showReplyDialog = MutableStateFlow(false)
    val showReplyDialog: StateFlow<Boolean> = _showReplyDialog.asStateFlow()

    private val _currentReplyComment = MutableStateFlow<KtorClient.Comment?>(null)
    val currentReplyComment: StateFlow<KtorClient.Comment?> = _currentReplyComment.asStateFlow()

    private val _showMoreMenu = MutableStateFlow(false)
    val showMoreMenu: StateFlow<Boolean> = _showMoreMenu.asStateFlow()

    private val _currentCommentPage = MutableStateFlow(1)
    val currentCommentPage: StateFlow<Int> = _currentCommentPage.asStateFlow()

    private val _totalCommentPages = MutableStateFlow(1)
    val totalCommentPages: StateFlow<Int> = _totalCommentPages.asStateFlow()

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

    private val _hasMoreComments = MutableStateFlow(true)
    val hasMoreComments: StateFlow<Boolean> = _hasMoreComments.asStateFlow()

    private val browseHistoryRepository = BrowseHistoryRepository()

    fun loadPostDetail(postId: Long) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val userCredentialsFlow = AuthManager.getCredentials(context)
                val userCredentials = userCredentialsFlow.first()!!

                val result = KtorClient.ApiServiceImpl.getPostDetail(
                    token = userCredentials.token,
                    postId = postId
                )

                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    if (response.code == 1) {
                        val post = response.data
                        _postDetail.value = post
                        post.let {
                            browseHistoryRepository.addHistory(
                                BrowseHistory(
                                    postId = it.id,
                                    title = it.title,
                                    previewContent = it.content.take(100)
                                )
                            )

                            _isLiked.value = it.is_thumbs == 1
                            _likeCount.value = it.thumbs.toIntOrNull() ?: 0
                            _commentCount.value = it.comment.toIntOrNull() ?: 0
                        }
                    } else {
                        _errorMessage.value = "加载失败: ${response.msg}"
                    }
                } else {
                    val exceptionMessage = result.exceptionOrNull()?.message
                    _errorMessage.value = "加载失败: ${exceptionMessage ?: "未知错误"}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            }
        }
    }

    fun loadComments(postId: Long, page: Int = 1) {
        if (_isLoadingComments.value) return
        if (page > _totalCommentPages.value && page > 1) return

        _isLoadingComments.value = true

        viewModelScope.launch {
            try {
                val result = KtorClient.ApiServiceImpl.getPostComments(
                    postId = postId,
                    limit = 20,
                    page = page
                )

                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    if (response.code == 1) {
                        val newComments = response.data.list
                        val totalPages = response.data.pagecount

                        _totalCommentPages.value = totalPages
                        _hasMoreComments.value = page < totalPages

                        _comments.value = if (page == 1) {
                            newComments
                        } else {
                            _comments.value + newComments
                        }
                        
                        if (page != _currentCommentPage.value) {
                            _currentCommentPage.value = page
                        }
                    } else {
                        _errorMessage.value = "加载评论失败: ${response.msg}"
                    }
                } else {
                    val exceptionMessage = result.exceptionOrNull()?.message
                    _errorMessage.value = "加载评论失败: ${exceptionMessage ?: "未知错误"}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载评论失败: ${e.message}"
            } finally {
                _isLoadingComments.value = false
            }
        }
    }

    fun loadNextPageComments(postId: Long) {
        val nextPage = _currentCommentPage.value + 1
        if (nextPage <= _totalCommentPages.value) {
            _currentCommentPage.value = nextPage
            loadComments(postId, nextPage)
        }
    }

    fun refreshComments(postId: Long) {
        _currentCommentPage.value = 1
        _comments.value = emptyList()
        loadComments(postId, 1)
    }

    fun clearErrorMessage() {
        _errorMessage.value = ""
    }

    fun toggleLike() {
        viewModelScope.launch {
            val postId = postDetail.value?.id ?: return@launch
            val isCurrentlyLiked = _isLiked.value

            try {
                 val context = getApplication<Application>().applicationContext
                val userCredentialsFlow = AuthManager.getCredentials(context)
                val userCredentials = userCredentialsFlow.first()!!

                val result = KtorClient.ApiServiceImpl.likePost(
                    token = userCredentials.token,
                    postId = postId
                )

                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    if (response.code == 1) {
                        _isLiked.value = !isCurrentlyLiked
                        _likeCount.value = if (isCurrentlyLiked) {
                            _likeCount.value - 1
                        } else {
                            _likeCount.value + 1
                        }
                    } else {
                        _errorMessage.value = "操作失败: ${response.msg}"
                    }
                } else {
                    val exceptionMessage = result.exceptionOrNull()?.message
                    _errorMessage.value = "操作失败: ${exceptionMessage ?: "未知错误"}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "操作失败: ${e.message}"
            }
        }
    }

    fun openCommentDialog() {
        _showCommentDialog.value = true
        _currentReplyComment.value = null
    }

    fun closeCommentDialog() {
        _showCommentDialog.value = false
    }

    fun openReplyDialog(comment: KtorClient.Comment) {
        _currentReplyComment.value = comment
        _showReplyDialog.value = true
    }

    fun closeReplyDialog() {
        _showReplyDialog.value = false
        _currentReplyComment.value = null
    }

    fun submitComment(content: String, imageUrl: String? = null) {
        viewModelScope.launch {
            try {
                 val context = getApplication<Application>().applicationContext
                val userCredentialsFlow = AuthManager.getCredentials(context)
                val userCredentials = userCredentialsFlow.first()!!
                val postId = postDetail.value?.id ?: return@launch
                val parentId = _currentReplyComment.value?.id ?: 0L

                val result = KtorClient.ApiServiceImpl.postComment(
                    token = userCredentials.token,
                    content = content,
                    postId = postId,
                    parentId = parentId,
                    imageUrl = imageUrl
                )

                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    if (response.code == 1) {
                        refreshComments(postId)
                        if (parentId == 0L) _commentCount.value += 1
                        closeCommentDialog()
                        closeReplyDialog()
                    } else {
                        _errorMessage.value = "提交失败: ${response.msg}"
                    }
                } else {
                    val exceptionMessage = result.exceptionOrNull()?.message
                    _errorMessage.value = "提交失败: ${exceptionMessage ?: "未知错误"}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "提交失败: ${e.message}"
            }
        }
    }

    fun deletePost() {
        viewModelScope.launch {
            val postId = postDetail.value?.id ?: return@launch
            val context = getApplication<Application>().applicationContext
               val userCredentialsFlow = AuthManager.getCredentials(context)
                val userCredentials = userCredentialsFlow.first()!!

            try {
                val result = KtorClient.ApiServiceImpl.deletePost(
                    token = userCredentials.token,
                    postId = postId
                )

                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    if (response.code == 1) {
                        _deleteSuccess.value = true
                    } else {
                        _errorMessage.value = "删除失败: ${response.msg}"
                    }
                } else {
                    val exceptionMessage = result.exceptionOrNull()?.message
                    _errorMessage.value = "删除失败: ${exceptionMessage ?: "未知错误"}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "删除失败: ${e.message}"
            }
        }
    }

    fun deleteComment(commentId: Long) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
             val userCredentialsFlow = AuthManager.getCredentials(context)
                val userCredentials = userCredentialsFlow.first()!!

            try {
                val result = KtorClient.ApiServiceImpl.deleteComment(
                    token = userCredentials.token,
                    commentId = commentId
                )

                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    if (response.code == 1) {
                        postDetail.value?.id?.let { loadComments(it) }
                        _commentCount.value = maxOf(0, _commentCount.value - 1)
                    } else {
                        _errorMessage.value = "删除失败: ${response.msg}"
                    }
                } else {
                    val exceptionMessage = result.exceptionOrNull()?.message
                    _errorMessage.value = "删除失败: ${exceptionMessage ?: "未知错误"}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "删除失败: ${e.message}"
            }
        }
    }

    fun toggleMoreMenu() {
        _showMoreMenu.value = !_showMoreMenu.value
    }
}