//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.payment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import me.voltual.pyrolysis.core.proto.UserCredentials
import java.util.UUID
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.android.annotation.KoinViewModel
import android.content.Context 

private val Context.dataStore by preferencesDataStore(name = "payment_requests")

// 定义一个简单的包装类，传递下载所需信息
data class DownloadEvent(
    val url: String,
    val fileName: String,
    val headers: Map<String, String> = emptyMap()
)

@KoinViewModel
class PaymentViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.applicationContext.dataStore

    private val _isLoadingBalance = MutableStateFlow(false)
    val isLoadingBalance: StateFlow<Boolean> = _isLoadingBalance.asStateFlow()

    // 支付信息状态
    private val _paymentInfo = MutableStateFlow<PaymentInfo?>(null)
    val paymentInfo: StateFlow<PaymentInfo?> = _paymentInfo

    // 硬币余额状态
    private val _coinsBalance = MutableStateFlow<Int?>(null)
    val coinsBalance: StateFlow<Int?> = _coinsBalance

    // 验证状态
    private val _verificationResult = MutableStateFlow<String?>(null)
    val verificationResult: StateFlow<String?> = _verificationResult

    // 支付状态
    private val _paymentStatus = MutableStateFlow<PaymentStatus>(PaymentStatus.INITIAL)
    val paymentStatus: StateFlow<PaymentStatus> = _paymentStatus

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Payment Request ID
    private val _paymentRequestId = MutableStateFlow<String?>(null)
    val paymentRequestId: StateFlow<String?> = _paymentRequestId

    private var _downloadUrl: String? = null
    private var _downloadFileName: String? = null
    
    //  添加事件流
    private val _downloadEvent = MutableSharedFlow<DownloadEvent>()
    val downloadEvent = _downloadEvent.asSharedFlow()

    fun getDownloadUrl(): String? = _downloadUrl
    
    fun getDownloadFileName(): String? = _downloadFileName

    // 设置支付信息（更新参数）
    fun setPaymentInfo(
        type: PaymentType,
        appId: Long = 0L,
        appName: String = "",
        versionId: Long = 0L,
        price: Int = 0,
        postId: Long = 0L,
        postTitle: String = "",
        locked: Boolean = true,
        iconUrl: String = "",
        previewContent: String = "",
        authorName: String = "",
        authorAvatar: String = "",
        postTime: String = ""
    ) {
        _paymentInfo.value = PaymentInfo(
            type = type,
            appId = appId,
            appName = appName,
            versionId = versionId,
            price = price,
            postId = postId,
            postTitle = postTitle,
            locked = locked,
            iconUrl = iconUrl,
            previewContent = previewContent,
            authorName = authorName,
            authorAvatar = authorAvatar,
            postTime = postTime
        )
        
        // 设置默认下载文件名
        if (type == PaymentType.APP_PURCHASE && appName.isNotEmpty()) {
            _downloadFileName = "${appName.replace(" ", "_")}_v${versionId}.apk"
        }
    }
    
    fun startDownload(url: String, fileName: String?) {
        viewModelScope.launch {
            _downloadEvent.emit(
                DownloadEvent(
                    url = url,
                    fileName = fileName ?: "download_${System.currentTimeMillis()}.apk"
                )
            )
        }
    }

    fun loadPostInfo(postId: Long) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val userCredentialsFlow = AuthManager.getCredentials(context)
                val userCredentials = userCredentialsFlow.first()
                val token = userCredentials?.token ?: run {
                    _errorMessage.value = "用户未登录"
                    return@launch
                }

                // 使用 KtorClient 发起请求
                val response = withContext(Dispatchers.IO) {
                    KtorClient.ApiServiceImpl.getPostDetail(
                        token = token,
                        postId = postId
                    )
                }

                response.onSuccess { result ->
                    val post = result.data
                    _paymentInfo.value = PaymentInfo(
                        type = PaymentType.POST_REWARD,
                        postId = postId,
                        postTitle = post.title,
                        previewContent = post.content.take(30),
                        locked = false,
                        authorName = post.nickname,
                        authorAvatar = post.usertx,
                        postTime = post.create_time_ago
                    )
                }.onFailure { error ->
                    _errorMessage.value = "加载帖子失败: ${error.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载帖子失败: ${e.message}"
            }
        }
    }

    // 获取硬币余额
    fun fetchCoinsBalance() {
        // 只有用户点击时才加载
        viewModelScope.launch {
            _isLoadingBalance.value = true  // 开始加载
            _coinsBalance.value = null     // 重置余额显示
            try {
                val context = getApplication<Application>().applicationContext
                val userCredentialsFlow = AuthManager.getCredentials(context)
                val userCredentials = userCredentialsFlow.first() ?: run {
                    _errorMessage.value = "用户未登录"
                    return@launch
                }

                // 使用 KtorClient 发起请求
                val response = withContext(Dispatchers.IO) {
                    KtorClient.ApiServiceImpl.getUserInformation(
                        userId = userCredentials.userId,
                        token = userCredentials.token
                    )
                }

                response.onSuccess { result ->
                    _coinsBalance.value = result.data.money
                }.onFailure { error ->
                    _errorMessage.value = "获取余额失败: ${error.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                _isLoadingBalance.value = false  // 结束加载
            }
        }
    }

    // 验证支付信息
    fun verifyPaymentInfo() {
        viewModelScope.launch {
            val info = _paymentInfo.value ?: return@launch

            _verificationResult.value = null
            _errorMessage.value = null

            try {
                when (info.type) {
                    PaymentType.APP_PURCHASE -> {
                        // 使用 KtorClient 发起请求
                        val response = KtorClient.ApiServiceImpl.getAppsInformation(
                            token = "",
                            appsId = info.appId,
                            appsVersionId = info.versionId
                        )

                        response.onSuccess { result ->
                            val appDetail = result.data
                            _verificationResult.value =
                                "验证成功: ${appDetail.appname} (¥${appDetail.pay_money})"
                        }.onFailure { error ->
                            _errorMessage.value = "应用验证失败: ${error.message}"
                        }
                    }
                    PaymentType.POST_REWARD -> {
                        // 使用 KtorClient 发起请求
                        val response = KtorClient.ApiServiceImpl.getPostDetail(
                            token = "",
                            postId = info.postId
                        )

                        response.onSuccess { result ->
                            val post = result.data
                            _verificationResult.value =
                                "验证成功: ${post.title}"
                        }.onFailure { error ->
                            _errorMessage.value = "帖子验证失败: ${error.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "验证错误: ${e.message}"
            }
        }
    }

    // 执行支付
    fun executePayment(amount: Int) {
        viewModelScope.launch {
            val info = _paymentInfo.value ?: return@launch

            // 生成 paymentRequestId
            val requestId = UUID.randomUUID().toString()
            _paymentRequestId.value = requestId

            // 检查是否已经提交过相同的支付请求
            val existingRequestId = getPaymentRequestId(requestId)
            if (existingRequestId != null) {
                _errorMessage.value = "该支付请求已提交，请勿重复操作"
                _paymentStatus.value = PaymentStatus.FAILED
                _paymentRequestId.value = null
                return@launch
            }

            _paymentStatus.value = PaymentStatus.PROCESSING // 支付中
            _errorMessage.value = null

            try {
                // 保存 paymentRequestId 到 DataStore
                savePaymentRequestId(requestId)

                val context = getApplication<Application>().applicationContext
                val userCredentialsFlow = AuthManager.getCredentials(context)
                val userCredentials = userCredentialsFlow.first() ?: run {
                    _errorMessage.value = "用户未登录"
                    _paymentStatus.value = PaymentStatus.FAILED
                    removePaymentRequestId(requestId) // 移除 paymentRequestId
                    return@launch
                }

                // 使用 KtorClient 发起请求
                val response = when (info.type) {
                    PaymentType.APP_PURCHASE -> {
                        KtorClient.ApiServiceImpl.payForApp(
                            token = userCredentials.token,
                            appsId = info.appId,
                            appsVersionId = info.versionId,
                            money = amount,
                            type = 0 // 硬币支付
                        )
                    }
                    PaymentType.POST_REWARD -> {
                        KtorClient.ApiServiceImpl.rewardPost(
                            token = userCredentials.token,
                            postId = info.postId,
                            money = amount,
                            payment = 0 // 硬币支付
                        )
                    }
                }

                response.onSuccess { result ->
                    if (result.code == 1) {
                        _paymentStatus.value = PaymentStatus.SUCCESS
                        // 更新硬币余额
                        _coinsBalance.value = (_coinsBalance.value ?: 0) - amount

                        // 保存下载链接（如果是应用购买）
                        if (info.type == PaymentType.APP_PURCHASE) {
                            // 使用 getDownloadUrl() 方法获取下载链接
                            _downloadUrl = result.getDownloadUrl()
                        }
                    } else {
                        _errorMessage.value = "支付失败: ${result.msg}"
                        _paymentStatus.value = PaymentStatus.FAILED
                    }
                }.onFailure { error ->
                    _errorMessage.value = "支付错误: ${error.message}"
                    _paymentStatus.value = PaymentStatus.FAILED
                }
            } catch (e: Exception) {
                _errorMessage.value = "支付错误: ${e.message}"
                _paymentStatus.value = PaymentStatus.FAILED
            } finally {
                removePaymentRequestId(requestId) 
                _paymentRequestId.value = null // 清空 paymentRequestId
            }
        }
    }

    // 重置支付状态时也清除下载链接
    fun resetPaymentStatus() {
        _paymentStatus.value = PaymentStatus.INITIAL
        _errorMessage.value = null
        _downloadUrl = null
        _downloadFileName = null
    }

    // 保存 paymentRequestId 到 DataStore
    private suspend fun savePaymentRequestId(requestId: String) {
        dataStore.edit { mutablePreferences ->
            mutablePreferences[stringPreferencesKey(requestId)] = requestId
        }
    }

    // 从 DataStore 中获取 paymentRequestId
    private suspend fun getPaymentRequestId(requestId: String): String? {
        return dataStore.data
            .map { preferences ->
                preferences[stringPreferencesKey(requestId)]
            }
            .first()
    }

    // 从 DataStore 中移除 paymentRequestId
    private suspend fun removePaymentRequestId(requestId: String) {
        dataStore.edit { mutablePreferences ->
            mutablePreferences.remove(stringPreferencesKey(requestId))
        }
    }
}