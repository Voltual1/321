//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。

package me.voltual.pyrolysis.ui.user

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

enum class UserListType {
    FOLLOWERS,  // 关注列表
    FANS        // 粉丝列表
}

data class UserListState(
    val users: List<KtorClient.UserItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val listType: UserListType = UserListType.FOLLOWERS
)

@KoinViewModel
class UserListViewModel(application: Application) : AndroidViewModel(application) {

    private var currentListType: UserListType = UserListType.FOLLOWERS
    private val apiService = KtorClient.ApiServiceImpl

    private val _uiState = MutableStateFlow(UserListState())
    val uiState = _uiState.asStateFlow()

    /**
     * 设置列表类型并重置状态
     * 注意：这不会自动加载数据，需要手动调用 loadInitialData()
     */
    fun setListType(type: UserListType) {
        if (currentListType != type) {
            currentListType = type
            // 重置状态但保留类型
            _uiState.value = UserListState(listType = type)
        }
    }

    /**
     * 手动初始化数据（由 UI 调用，而不是在 init 中自动调用）
     */
    fun loadInitialData() {
        if (_uiState.value.users.isEmpty()) {
            loadData(1)
        }
    }

    fun refresh() {
        loadData(1)
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (!currentState.isLoading && currentState.currentPage < currentState.totalPages) {
            loadData(currentState.currentPage + 1)
        }
    }

    private fun loadData(page: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val context = getApplication<Application>().applicationContext
            val userCredentialsFlow = AuthManager.getCredentials(context)
            val userCredentials = userCredentialsFlow.first()
            val token = userCredentials?.token ?: ""

            try {
                val result = when (currentListType) {
                    UserListType.FOLLOWERS -> apiService.getFollowList(token = token, limit = 10, page = page)
                    UserListType.FANS -> apiService.getFanList(token = token, limit = 10, page = page)
                }

                when (val response = result.getOrNull()) {
                    is KtorClient.UserListResponse -> {
                        if (response.code == 1) {
                            val data = response.data
                            _uiState.value = _uiState.value.copy(
                                users = if (page == 1) data.list else _uiState.value.users + data.list,
                                totalPages = data.pagecount,
                                currentPage = page,
                                isLoading = false,
                                listType = currentListType
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = response.msg,
                                isLoading = false
                            )
                        }
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = result.exceptionOrNull()?.message,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message,
                    isLoading = false
                )
            }
        }
    }
}