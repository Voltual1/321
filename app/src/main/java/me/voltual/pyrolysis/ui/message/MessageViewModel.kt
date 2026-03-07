//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.message

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinViewModel
import kotlinx.coroutines.Dispatchers

data class MessageState(
    val messages: List<KtorClient.MessageNotification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val isInitialized: Boolean = false
)

@KoinViewModel
class MessageViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(MessageState())
    val state: StateFlow<MessageState> = _state.asStateFlow()    
    
    // 使用 ViewModel 的生命周期来管理初始化，而不是 Compose 的重组
    private var _isInitialized = false

    // 公开的初始化方法，但只在真正需要时加载
    fun initializeIfNeeded(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            _isInitialized = false
        }
        
        if (!_isInitialized && !_state.value.isLoading) {
            _isInitialized = true
            loadPage(1)
        }
    }

    // 改进的加载逻辑，保持当前页面状态
    fun loadPage(page: Int = 1) {
        // 如果已经在加载，则避免重复加载
        if (_state.value.isLoading) {
            return
        }

        _state.value = _state.value.copy(
            isLoading = true,
            error = null
        )
        
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val userCredentialsFlow = AuthManager.getCredentials(context)
                val userCredentials = userCredentialsFlow.first()
                val token = userCredentials?.token ?: ""

                val messageNotificationsResult = KtorClient.ApiServiceImpl.getMessageNotifications(
                    token = token,
                    limit = 5,
                    page = page
                )
                
                if (messageNotificationsResult.isSuccess) {
                    messageNotificationsResult.getOrNull()?.let { messageNotificationsResponse ->
                        if (messageNotificationsResponse.code == 1) {
                            messageNotificationsResponse.data.let { data ->
                                _state.value = MessageState(
                                    messages = data.list,
                                    currentPage = page,
                                    totalPages = data.pagecount,
                                    isInitialized = true,
                                    isLoading = false 
                                )
                            }
                        } else {
                            _state.value = _state.value.copy(
                                error = "加载失败: ${messageNotificationsResponse.msg}",
                                isLoading = false
                            )
                        }
                    } ?: run {
                        _state.value = _state.value.copy(
                            error = "加载失败: 响应为空",
                            isLoading = false
                        )
                    }
                } else {
                    _state.value = _state.value.copy(
                        error = "加载失败: ${messageNotificationsResult.exceptionOrNull()?.message}",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "网络错误: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    // 下一页
    fun nextPage() {
        val nextPage = _state.value.currentPage + 1
        if (nextPage > _state.value.totalPages) return
        loadPage(nextPage)
    }
    
    // 上一页
    fun prevPage() {
        val prevPage = _state.value.currentPage - 1
        if (prevPage < 1) return
        loadPage(prevPage)
    }
    
    // 跳转到指定页
    fun goToPage(page: Int) {
        if (page in 1.._state.value.totalPages) {
            loadPage(page)
        }
    }

    // 重置（用于下拉刷新等场景）
    fun reset() {
        _isInitialized = false
        _state.value = MessageState()
        initializeIfNeeded()
    }
}