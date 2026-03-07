//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.plaza

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.feature.store.repository.IAppStoreRepository
import me.voltual.pyrolysis.data.unified.*
import kotlinx.coroutines.flow.MutableSharedFlow
import me.voltual.pyrolysis.KtorClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import android.content.Context
import android.content.Intent
import me.voltual.pyrolysis.core.utils.DownloadManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import me.voltual.pyrolysis.LingMarketClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@KoinViewModel
class AppDetailComposeViewModel(
    application: Application,
    private val repositories: Map<AppStore, IAppStoreRepository>
) : AndroidViewModel(application) {

    private val _appDetail = MutableStateFlow<UnifiedAppDetail?>(null)
    val appDetail: StateFlow<UnifiedAppDetail?> = _appDetail.asStateFlow()

    private val _comments = MutableStateFlow<List<UnifiedComment>>(emptyList())
    val comments: StateFlow<List<UnifiedComment>> = _comments.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _showCommentDialog = MutableStateFlow(false)
    val showCommentDialog: StateFlow<Boolean> = _showCommentDialog.asStateFlow()

    private val _showReplyDialog = MutableStateFlow(false)
    val showReplyDialog: StateFlow<Boolean> = _showReplyDialog.asStateFlow()

    private val _currentReplyComment = MutableStateFlow<UnifiedComment?>(null)
    val currentReplyComment: StateFlow<UnifiedComment?> = _currentReplyComment.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentStore: AppStore = AppStore.XIAOQU_SPACE
    private var currentAppId: String = ""
    private var currentVersionId: Long = 0L

    private val _downloadSources = MutableStateFlow<List<UnifiedDownloadSource>>(emptyList())
    val downloadSources: StateFlow<List<UnifiedDownloadSource>> = _downloadSources.asStateFlow()

    private val _showDownloadDrawer = MutableStateFlow(false)
    val showDownloadDrawer: StateFlow<Boolean> = _showDownloadDrawer.asStateFlow()

    // Snackbar 事件
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()
    
    // 导航事件
    private val _navigateToDownloadEvent = MutableSharedFlow<Boolean>()
    val navigateToDownloadEvent: SharedFlow<Boolean> = _navigateToDownloadEvent.asSharedFlow()

    // 用于发送一次性事件（如打开浏览器）
    private val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    private val _currentTab = MutableStateFlow(0) // 0: 详情, 1: 版本列表
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()
    
    // 分享链接
    private val _shareLink = MutableStateFlow<String?>(null)
    val shareLink: StateFlow<String?> = _shareLink.asStateFlow()

    private val repository: IAppStoreRepository
        get() = repositories[currentStore] ?: throw IllegalStateException("Repository not found")
        
    // 退币事件
    private val _refundEvent = MutableSharedFlow<RefundInfo>()
    val refundEvent: SharedFlow<RefundInfo> = _refundEvent.asSharedFlow()

    // 更新事件
    private val _updateEvent = MutableSharedFlow<String>()
    val updateEvent: SharedFlow<String> = _updateEvent.asSharedFlow()
    
    // 支付相关状态和方法
    private val _navigateToPaymentEvent = MutableSharedFlow<PaymentInfo>()
    val navigateToPaymentEvent: SharedFlow<PaymentInfo> = _navigateToPaymentEvent.asSharedFlow()
    
    // 下载事件
    data class DownloadEvent(
        val url: String,
        val fileName: String,
        val headers: Map<String, String>? = null
    )

    private val _downloadEvent = MutableSharedFlow<DownloadEvent>()
    val downloadEvent: SharedFlow<DownloadEvent> = _downloadEvent.asSharedFlow()


    // 支付信息数据类
    data class PaymentInfo(
        val appId: Long,
        val appName: String,
        val versionId: Long,
        val price: Int,
        val iconUrl: String,
        val previewContent: String
    )

    // 退款信息数据类
    data class RefundInfo(
        val appId: String,
        val versionId: Long,
        val appName: String,
        val payMoney: Int
    )    

    // 新增：获取设备SDK版本
    val deviceSdkVersion: Int
        get() = Build.VERSION.SDK_INT

    fun initializeData(appId: String, versionId: Long, storeName: String) {
        val store = try {
            AppStore.valueOf(storeName)
        } catch (e: Exception) {
            AppStore.XIAOQU_SPACE
        }

        if (currentAppId == appId && currentVersionId == versionId && currentStore == store && _appDetail.value != null) {
            return
        }

        currentAppId = appId
        currentVersionId = versionId
        currentStore = store

        loadData()
    }
    
    // 请求退款功能（仅小趣空间）
    fun requestRefund() {
        viewModelScope.launch {
            val detail = _appDetail.value ?: return@launch
            
            if (detail.store == AppStore.XIAOQU_SPACE) {
                val raw = detail.raw as? me.voltual.pyrolysis.KtorClient.AppDetail
                if (raw != null) {
                    _refundEvent.emit(
                        RefundInfo(
                            appId = currentAppId,
                            versionId = currentVersionId,
                            appName = detail.name,
                            payMoney = raw.pay_money
                        )
                    )
                }
            } else {
                _snackbarEvent.tryEmit("该商店不支持退币功能")
            }
        }
    }

    // 请求更新功能（仅小趣空间）
    fun requestUpdate() {
        viewModelScope.launch {
            val detail = _appDetail.value ?: return@launch
            
            if (detail.store == AppStore.XIAOQU_SPACE) {
                val raw = detail.raw as? me.voltual.pyrolysis.KtorClient.AppDetail
                if (raw != null) {
                    // 使用 KtorClient 的 JsonConverter 将 AppDetail 转换为 JSON 字符串
                    val appDetailJson = KtorClient.JsonConverter.toJson(raw)
                    _updateEvent.emit(appDetailJson)
                }
            } else {
                _snackbarEvent.tryEmit("该商店不支持更新功能")
            }
        }
    }        

    fun refresh() {
        loadData()
    }

    // 检查是否需要购买
    fun checkPurchaseNeeded(): PaymentInfo? {
        val detail = _appDetail.value ?: return null
        
        // 仅小趣空间应用需要检查购买状态
        if (detail.store == AppStore.XIAOQU_SPACE) {
            val raw = detail.raw as? me.voltual.pyrolysis.KtorClient.AppDetail
            if (raw != null && raw.is_pay == 1 && raw.pay_money > 0 && raw.is_user_pay != true) {
                return PaymentInfo(
                    appId = raw.id,
                    appName = raw.appname,
                    versionId = raw.apps_version_id,
                    price = raw.pay_money,
                    iconUrl = raw.app_icon,
                    previewContent = raw.app_introduce?.take(30) ?: ""
                )
            }
        }
        return null
    }
    
/**
 * 切换收藏状态
 */
fun toggleFavorite() {
    val currentDetail = _appDetail.value ?: return
    val targetState = currentDetail.isFavorite 

    viewModelScope.launch {
        // 调用仓库执行 收藏/取消收藏 操作
        val result = repository.toggleFavorite(currentAppId, currentDetail.isFavorite)

        if (result.isSuccess) {
            // 获取操作后的实际状态
            val finalFavoriteState = result.getOrNull() ?: targetState
            
            // 计算新的收藏数
            val newFavoriteCount = if (currentDetail.store == AppStore.LING_MARKET) {
                // 如果是灵应用商店，保持原样，不手动增减
                currentDetail.favoriteCount
            } else {
                // 其他商店，手动计算增减
                if (finalFavoriteState) {
                    (currentDetail.favoriteCount ?: 0) + 1
                } else {
                    maxOf(0, (currentDetail.favoriteCount ?: 1) - 1)
                }
            }

            // 更新本地 StateFlow 以立即刷新 UI
            _appDetail.value = currentDetail.copy(
                isFavorite = finalFavoriteState,
                favoriteCount = newFavoriteCount
            )
            
            // 发送 Snackbar 提示
            val message = if (finalFavoriteState) "已添加到收藏" else "已取消收藏"
            _snackbarEvent.emit(message)
        } else {
            val error = result.exceptionOrNull()?.message ?: "操作失败"
            _snackbarEvent.emit("收藏失败: $error")
        }
    }
}

    fun handleDownloadClick() {
    viewModelScope.launch {
        val paymentInfo = checkPurchaseNeeded()
        if (paymentInfo != null) {
            // 需要购买，触发支付事件
            _navigateToPaymentEvent.emit(paymentInfo)
        } else {
            // 不需要购买，继续原有下载逻辑
            _isLoading.value = true
            
            // 获取当前应用详情
            val detail = _appDetail.value
            
            // 检查是否为灵应用商店且是否有直接下载URL
            if (detail != null && detail.store == AppStore.LING_MARKET) {
                // 对于灵应用商店，我们已经在 getAppDetail 中尝试获取了直接URL
                if (detail.downloadUrl != null) {
                    // 有直接下载URL，直接使用
                    startDownload(detail.downloadUrl!!)
                } else {
                    // 如果没有直接下载URL，尝试从原始数据获取apkKey并请求下载URL
                    handleLingMarketDownload(detail)
                }
            } else {
                // 其他商店，使用原有逻辑
                val result = repository.getAppDownloadSources(currentAppId, currentVersionId)
                _isLoading.value = false

                if (result.isSuccess) {
                    val sources = result.getOrThrow()
                    if (sources.isEmpty()) {
                        _errorMessage.value = "未找到下载源"
                    } else if (sources.size == 1) {
                        // 只有一个源，直接触发下载
                        startDownload(sources.first().url)
                    } else {
                        // 多个源，显示抽屉
                        _downloadSources.value = sources
                        _showDownloadDrawer.value = true
                    }
                } else {
                    _errorMessage.value = "获取下载链接失败: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }
}

/**
 * 处理灵应用商店的下载逻辑
 */
private suspend fun handleLingMarketDownload(detail: UnifiedAppDetail) {
    try {
        // 从原始数据获取apkKey
        val raw = detail.raw as? LingMarketClient.LingMarketApp
        if (raw != null && raw.apkKey.isNotEmpty()) {
            // 获取直接下载URL
            val result = LingMarketClient.getFileDownloadUrl(raw.apkKey)
            _isLoading.value = false
            
            if (result.isSuccess) {
                val downloadUrl = result.getOrThrow().url
                startDownload(downloadUrl)
            } else {
                _errorMessage.value = "获取下载链接失败: ${result.exceptionOrNull()?.message}你可能没有登录灵应用商店哦"
            }
        } else {
            _isLoading.value = false
            _errorMessage.value = "未找到应用文件信息"
        }
    } catch (e: Exception) {
        _isLoading.value = false
        _errorMessage.value = "处理下载时出错: ${e.message}"
    }
}

    fun closeDownloadDrawer() {
        _showDownloadDrawer.value = false
    }

    private fun loadData() {
    _isLoading.value = true
    _errorMessage.value = ""

    viewModelScope.launch {
        try {
            // 使用 coroutineScope 确保内部的 async 能正常工作
            coroutineScope {
                // 并行发起请求
                val detailDeferred = async { repository.getAppDetail(currentAppId, currentVersionId) }
                val favoriteDeferred = async { repository.getFavoriteState(currentAppId) }

                // 等待结果
                val detailResult = detailDeferred.await()
                val favoriteResult = favoriteDeferred.await()

                if (detailResult.isSuccess) {
                    // 明确指定类型，避免编译器的类型推断错误
                    var detail: UnifiedAppDetail = detailResult.getOrThrow()
                    
                    // 获取收藏状态并更新
                    favoriteResult.onSuccess { state ->
                        detail = detail.copy(isFavorite = state.isFavorite)
                    }

                    _appDetail.value = detail
                    loadComments()
                } else {
                    _errorMessage.value = "加载详情失败: ${detailResult.exceptionOrNull()?.message}"
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "网络错误: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}

    private fun loadComments() {
        viewModelScope.launch {
            // 传入 currentVersionId
            val result = repository.getAppComments(currentAppId, currentVersionId, 1)
            if (result.isSuccess) {
                _comments.value = result.getOrThrow().first
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

    fun openReplyDialog(comment: UnifiedComment) {
        _currentReplyComment.value = comment
        _showReplyDialog.value = true
    }

    fun closeReplyDialog() {
        _showReplyDialog.value = false
        _currentReplyComment.value = null
    }

    fun submitComment(content: String) {
    viewModelScope.launch {
        val parentId = _currentReplyComment.value?.id
        val result = repository.postComment(currentAppId, currentVersionId, content, parentId, null)

        if (result.isSuccess) {
            loadComments()
            if (parentId == null) closeCommentDialog() else closeReplyDialog()
        } else {
            _errorMessage.value = "提交失败: ${result.exceptionOrNull()?.message}"
        }
    }
}

    fun deleteComment(commentId: String) {
    viewModelScope.launch {
        // 判断是否为灵应用商店，如果是则使用新方法
        val result = if (currentStore == AppStore.LING_MARKET) {
            repository.deleteComment(currentAppId, commentId)
        } else {
            repository.deleteComment(commentId)
        }
        
        if (result.isSuccess) {
            loadComments()
        } else {
            _errorMessage.value = "删除失败: ${result.exceptionOrNull()?.message}"
        }
    }
}

    // 删除应用（无需鉴权，服务器会处理）
    fun deleteApp(onSuccess: () -> Unit) {
        viewModelScope.launch {
            // 直接调用仓库的删除方法，服务器会自行鉴权
            val result = repository.deleteApp(currentAppId, currentVersionId)
            if (result.isSuccess) {
                onSuccess()
            } else {
                val error = result.exceptionOrNull()?.message ?: "删除失败"
                _errorMessage.value = error
            }
        }
    }
        
    // 启动下载
 fun startDownload(downloadUrl: String) {
        viewModelScope.launch {
            try {
                // 获取应用名称用于文件名
                val appName = "${_appDetail.value?.name ?: "未命名应用"}.apk"
                
                // 发送下载事件，包含 URL 和文件名
                _downloadEvent.emit(DownloadEvent(downloadUrl, appName))                
                
            } catch (e: Exception) {
                _errorMessage.value = "启动下载失败: ${e.message}"
            }
        }
    }

}