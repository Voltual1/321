//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.billing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinViewModel

data class BillingState(
    val billings: List<KtorClient.BillingItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1
)

@KoinViewModel
class BillingViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = _state.asStateFlow()
    
    private val context = application.applicationContext

    fun loadBilling() {
        viewModelScope.launch {
            val context = context
            val userCredentialsFlow = AuthManager.getCredentials(context)
            val userCredentials = userCredentialsFlow.first()
            val token = userCredentials?.token ?: ""
        
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                currentPage = 1
            )
        
            try {
                val billingResult = KtorClient.ApiServiceImpl.getUserBilling(
                    token = token,
                    limit = 10,
                    page = 1
                )
        
                if (billingResult.isSuccess) {
                    billingResult.getOrNull()?.let { billingResponse ->
                        if (billingResponse.code == 1) {
                            val data = billingResponse.data
                            _state.value = BillingState(
                                billings = data.list,
                                currentPage = 1,
                                totalPages = data.pagecount,
                                isLoading = false
                            )
                        } else {
                            _state.value = _state.value.copy(
                                error = billingResponse.msg,
                                isLoading = false
                            )
                        }
                    } ?: run {
                        _state.value = _state.value.copy(
                            error = "加载账单失败: 响应为空",
                            isLoading = false
                        )
                    }
                } else {
                    val exceptionMessage = billingResult.exceptionOrNull()?.message
                    _state.value = _state.value.copy(
                        error = if (exceptionMessage != null) "加载账单失败: $exceptionMessage" else "加载账单失败",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "网络错误: ${e.message ?: "未知错误"}",
                    isLoading = false
                )
            }
        }
    }
    
    fun loadNextPage() {
        val nextPage = _state.value.currentPage + 1
        if (nextPage > _state.value.totalPages) return
        
        viewModelScope.launch {
            val context = context
            val userCredentialsFlow = AuthManager.getCredentials(context)
            val userCredentials = userCredentialsFlow.first()
            val token = userCredentials?.token ?: ""

            _state.value = _state.value.copy(isLoading = true)
        
            try {
                val billingResult = KtorClient.ApiServiceImpl.getUserBilling(
                    token = token,
                    limit = 10,
                    page = nextPage
                )
        
                if (billingResult.isSuccess) {
                    billingResult.getOrNull()?.let { billingResponse ->
                        if (billingResponse.code == 1) {
                            val data = billingResponse.data
                            _state.value = _state.value.copy(
                                billings = _state.value.billings + data.list,
                                currentPage = nextPage,
                                totalPages = data.pagecount,
                                isLoading = false
                            )
                        } else {
                            _state.value = _state.value.copy(
                                error = billingResponse.msg,
                                isLoading = false
                            )
                        }
                    } ?: run {
                        _state.value = _state.value.copy(
                            error = "加载更多失败: 响应为空",
                            isLoading = false
                        )
                    }
                } else {
                    val exceptionMessage = billingResult.exceptionOrNull()?.message
                    _state.value = _state.value.copy(
                        error = if (exceptionMessage != null) "加载更多失败: $exceptionMessage" else "加载更多失败",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "网络错误: ${e.message ?: "未知错误"}",
                    isLoading = false
                )
            }
        }
    }
}