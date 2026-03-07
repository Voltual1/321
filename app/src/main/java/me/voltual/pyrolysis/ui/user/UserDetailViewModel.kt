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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.data.unified.toUnifiedUserDetail  
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.SineShopClient
import me.voltual.pyrolysis.BBQApplication
import me.voltual.pyrolysis.data.unified.UnifiedUserDetail 
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class UserDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _userData = MutableStateFlow<UnifiedUserDetail?>(null)
    val userData: StateFlow<UnifiedUserDetail?> = _userData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 添加状态跟踪
    private var _isInitialized = false
    private var _currentUserId: Long = -1L
    private var _currentStore: AppStore = AppStore.XIAOQU_SPACE
    private val apiService = KtorClient.ApiServiceImpl

    fun loadUserDetails(userId: Long, store: AppStore = AppStore.XIAOQU_SPACE) {
        // 只有当用户ID或商店真正改变时才重新加载
        if (this._currentUserId != userId || this._currentStore != store) {
            this._currentUserId = userId
            this._currentStore = store
            this._isInitialized = false
            resetState()
            loadDataIfNeeded()
        } else {
            // 相同的用户ID和商店，确保数据已加载
            loadDataIfNeeded()
        }
    }

    private fun resetState() {
        _userData.value = null
        _errorMessage.value = null
        _isLoading.value = false
    }

    // 内部方法：只在需要时加载数据
    private fun loadDataIfNeeded() {
        if (!_isInitialized && _currentUserId != -1L && !_isLoading.value) {
            _isInitialized = true
            loadData()
        }
    }

    // 提供手动刷新方法
    fun refresh() {
    if (_currentUserId != -1L) {
        _isLoading.value = false
        loadData()
    }
}
        
    // 关注用户
    fun followUser(targetUserId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = getToken()
                if (token.isEmpty()) {
                    _errorMessage.value = "请先登录"
                    return@launch
                }
                
                val result = apiService.followUser(
                    token = token,
                    followedId = targetUserId
                )
                
                when (val response = result.getOrNull()) {
                    is KtorClient.BaseResponse -> {
                        if (response.code == 1) {
                            // 关注成功，刷新用户数据
                            refresh()
                            // 这里可以使用 Snackbar 提示，需要在 UI 层处理
                        } else {
                            _errorMessage.value = "关注失败: ${response.msg}"
                        }
                    }
                    else -> {
                        _errorMessage.value = "网络错误"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "关注失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 新增：取消关注用户（使用相同的 API）
    fun unfollowUser(targetUserId: Long) {
        // 注意：根据接口文档，关注和取消关注使用同一个 API
        followUser(targetUserId)
    }
    
    // 获取 Token 的辅助函数
    private suspend fun getToken(): String {
        val context = getApplication<Application>()
        val userCredentialsFlow = AuthManager.getCredentials(context)
        val userCredentials = userCredentialsFlow.first()
        return userCredentials?.token ?: ""
    }

    private fun loadData() {
        viewModelScope.launch {
            val context = getApplication<Application>()
/*            val userCredentialsFlow = AuthManager.getCredentials(context)
            val userCredentials = userCredentialsFlow.first()*/
            val token = getToken()

            // 检查是否已经在加载
            if (_isLoading.value) return@launch

            _isLoading.value = true

            try {
                val result = when (_currentStore) {
                    AppStore.XIAOQU_SPACE -> {
                        // 小趣空间 API
                        apiService.getUserInformation(
                            userId = _currentUserId,
                            token = token
                        )
                    }
                    AppStore.SIENE_SHOP -> {
                        // 弦应用商店 API
                        SineShopClient.getUserInfoById(_currentUserId)
                    }
                    else -> {
                        // 其他应用商店：不支持用户详情，直接返回失败
                        Result.failure(IllegalArgumentException("当前应用商店不支持用户详情"))
                    }
                }

                // 只有在前两个分支时才处理响应
                if (_currentStore == AppStore.XIAOQU_SPACE || _currentStore == AppStore.SIENE_SHOP) {
                    when (val response = result.getOrNull()) {
                        is KtorClient.UserInformationResponse -> {
                            // 小趣空间响应
                            if (response.code == 1) {
                                _userData.value = response.data.toUnifiedUserDetail()
                                _errorMessage.value = null
                            } else {
                                _errorMessage.value = "加载失败: ${response.msg}"
                            }
                        }
                        is SineShopClient.SineShopUserInfo -> {
                            // 弦应用商店响应：直接是数据对象
                            _userData.value = response.toUnifiedUserDetail()
                            _errorMessage.value = null
                        }
                        else -> {
                            _errorMessage.value = "加载失败: ${result.exceptionOrNull()?.message ?: "网络错误"}"
                        }
                    }
                } else {
                    // 其他商店的情况
                    _errorMessage.value = "当前应用商店不支持用户详情"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}