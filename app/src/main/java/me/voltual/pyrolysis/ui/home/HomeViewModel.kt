//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient 
import me.voltual.pyrolysis.SineShopClient
import me.voltual.pyrolysis.data.SignInSettingsDataStore
import me.voltual.pyrolysis.core.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.voltual.pyrolysis.LingMarketClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.res.stringResource
import me.voltual.pyrolysis.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import org.koin.android.annotation.KoinViewModel

sealed class DataLoadState {
    object NotLoaded : DataLoadState()
    object Loading : DataLoadState()
    object Loaded : DataLoadState()
    object Error : DataLoadState()
}

data class HomeUiState(
    val showLoginPrompt: Boolean = true,
    val isLoading: Boolean = false,
    val avatarUrl: String? = null,
    val nickname: String = "You",
    val level: String = "LV0",
    val coins: String = "null",
    val userId: String = "null",
    val followersCount: String = "?",
    val fansCount: String = "?",
    val postsCount: String = "?",
    val signToday: Boolean = false,
    val likesCount: String = "?",
    val seriesDays: Int = 0,
    val signStatusMessage: String? = null,
    val createTime: String = "",
    val exp: Int = 0, 
    val lastSignTime: String = "",
    val displayDaysDiff: Int = 0,
    val dataLoadState: DataLoadState = DataLoadState.NotLoaded,
    val sineShopUserInfo: SineShopClient.SineShopUserInfo? = null,
    val sineShopLoginPrompt: Boolean = true,
    val lingMarketUserInfo: LingMarketClient.LingMarketUser? = null,
    val lingMarketLoginPrompt: Boolean = true
)

@KoinViewModel
class HomeViewModel : ViewModel() {
    var uiState = mutableStateOf(HomeUiState())
        private set
    var snackbarHostState = mutableStateOf<SnackbarHostState?>(null)
        private set

    fun setSnackbarHostState(hostState: SnackbarHostState) {
        snackbarHostState.value = hostState
    }

    fun loadUserData(context: Context, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val userCredentialsFlow = AuthManager.getCredentials(context)
            val userCredentials = userCredentialsFlow.first()

            // 检查用户是否已登录
if (userCredentials.userId == 0L || userCredentials.token.isEmpty()) {
                uiState.value = uiState.value.copy(
                    showLoginPrompt = true,
                    isLoading = false,
                    dataLoadState = DataLoadState.NotLoaded
                )
                return@launch // 未登录，直接返回
            }

            // 如果数据已经加载且不是强制刷新，则跳过
            if (!forceRefresh && uiState.value.dataLoadState == DataLoadState.Loaded) {
                return@launch
            }

            try {
                uiState.value = uiState.value.copy(
                    isLoading = true,
                    dataLoadState = DataLoadState.Loading
                )

                // 使用 KtorClient 发起网络请求
                val response = withContext(Dispatchers.IO) {
                    KtorClient.ApiServiceImpl.getUserInfo(token = userCredentials.token)
                }

                response.onSuccess { result ->
                    result.data.let { userData ->
                        // 计算时间差（创建时间到上次签到时间）
                        val daysDiff = calculateDaysDiff(
                            userData.create_time,
                            userData.signlasttime
                        )
                        
                        // 根据 sign_today 字段设置签到状态消息
                val signStatusMessage = if (!userData.sign_today) {
                    "点这里签到领经验和硬币哦"
                } else {
                    null // 已签到则显示 null，会显示默认文案
                }

                        uiState.value = uiState.value.copy(
                            showLoginPrompt = false,
                            avatarUrl = userData.usertx,
                            nickname = userData.nickname,
                            level = userData.hierarchy,
                            coins = userData.money.toString(),
                            userId = userData.username,
                            followersCount = userData.followerscount.toString(),
                            fansCount = userData.fanscount.toString(),
                            postsCount = userData.postcount.toString(),
                            likesCount = userData.likecount.toString(),
                            seriesDays = userData.series_days,
                            createTime = userData.create_time,
                            lastSignTime = userData.signlasttime,
                            displayDaysDiff = daysDiff,
                            isLoading = false,
                            exp = userData.exp,
                            signToday = userData.sign_today,
                    signStatusMessage = signStatusMessage,
                            dataLoadState = DataLoadState.Loaded
                        )
                        
                        // 检查自动签到设置，如果今日未签到且自动签到开启，则自动签到
                        checkAndAutoSignIn(context)
                    }
                }.onFailure { _ ->
                    uiState.value = uiState.value.copy(
                        isLoading = false,
                        dataLoadState = DataLoadState.Error
                    )
                }
            } catch (e: Exception) {
                uiState.value = uiState.value.copy(
                    isLoading = false,
                    dataLoadState = DataLoadState.Error
                )
            }
        }
    }
    
    private fun loadLingMarketUserInfo(context: Context) {
    viewModelScope.launch {
        try {
            // 获取灵应用商店token
            val lingMarketTokenFlow = AuthManager.getLingMarketToken(context)
            val lingMarketToken = lingMarketTokenFlow.first()

            // 如果没有token，则显示登录提示
            if (lingMarketToken.isNullOrEmpty()) {
                uiState.value = uiState.value.copy(
                    lingMarketLoginPrompt = true,
                    lingMarketUserInfo = null
                )
                return@launch
            }

            // 调用获取用户个人资料的API - 现在直接返回 LingMarketUser
            val userProfileResult = withContext(Dispatchers.IO) {
                LingMarketClient.LingMarketApiServiceImpl.getUserProfile()
            }

            userProfileResult.onSuccess { user ->
                // 成功获取用户信息
                uiState.value = uiState.value.copy(
                    lingMarketUserInfo = user,
                    lingMarketLoginPrompt = false
                )
                println("✅ LingMarket user info loaded: ${user.nickname}")
            }.onFailure { e ->
                println("❌ Failed to load LingMarket user info: ${e.message}")
                uiState.value = uiState.value.copy(
                    lingMarketLoginPrompt = true,
                    lingMarketUserInfo = null
                )
            }
        } catch (e: Exception) {
            println("❌ Error loading LingMarket user info: ${e.message}")
            uiState.value = uiState.value.copy(
                lingMarketLoginPrompt = true,
                lingMarketUserInfo = null
            )
        }
    }
}
    
    //显式检查和更新灵应用商店登录状态的方法
    fun checkAndUpdateLingMarketLoginState(context: Context) {
        viewModelScope.launch {
            try {
                // 获取灵应用商店token
                val lingMarketTokenFlow = AuthManager.getLingMarketToken(context)
                val lingMarketToken = lingMarketTokenFlow.first()

                // 更新登录状态
                val isLoggedIn = !lingMarketToken.isNullOrEmpty()
                uiState.value = uiState.value.copy(lingMarketLoginPrompt = !isLoggedIn)

                // 如果已登录，加载用户信息
                if (isLoggedIn) {
                    loadLingMarketUserInfo(context)
                }
            } catch (e: Exception) {
                println("Error checking LingMarket login state: ${e.message}")
                uiState.value = uiState.value.copy(
                    lingMarketLoginPrompt = true,
                    lingMarketUserInfo = null
                )
            }
        }
    }
    
    // 检查并执行自动签到
    private fun checkAndAutoSignIn(context: Context) {
        viewModelScope.launch {
            // 检查今日是否已签到
            if (uiState.value.signToday) {
                return@launch // 已签到，无需执行
            }
            
            // 检查自动签到设置
            val autoSignInEnabled = SignInSettingsDataStore.autoSignIn.first()
            if (autoSignInEnabled) {
                // 自动签到已开启，执行签到
                signIn(context, true) // true 表示是自动签到
            }
        }
    }
    
    // 修改签到方法，添加 isAutoSignIn 参数
    fun signIn(context: Context, isAutoSignIn: Boolean = false) {
        // 将实际的签到逻辑移到一个协程中
        viewModelScope.launch {
            val userCredentialsFlow = AuthManager.getCredentials(context)
            val userCredentials = userCredentialsFlow.first()
            val token = userCredentials?.token ?: ""

            
            try {
                uiState.value = uiState.value.copy(isLoading = true)

                // 使用 KtorClient 发起网络请求
                val response = withContext(Dispatchers.IO) {
                    //RetrofitClient.instance.userSignIn(token = token)
                    KtorClient.ApiServiceImpl.userSignIn(token = token)
                }

                response.onSuccess { result ->
                    // 处理服务器返回的401错误
                    if (result.code == 401) {
                        uiState.value = uiState.value.copy(
                            signStatusMessage = "登录已过期，请长按头像刷新",
                            showLoginPrompt = true,
                            signToday = true, // 标记为已签到
                            isLoading = false
                        )
                        // 重置加载状态，因为登录已过期
                        resetLoadState()
                    } else {
                        // 如果是自动签到，显示特定消息
                        val message = if (isAutoSignIn) {
                            "自动签到成功: ${result.msg}"
                        } else {
                            result.msg
                        }
                        
                        uiState.value = uiState.value.copy(
                            signStatusMessage = message,
                            isLoading = false
                        )

                        // 2秒后清除状态消息
                        launch {
                            delay(2000)
                            uiState.value = uiState.value.copy(signStatusMessage = null)
                        }

                        // 重新加载用户数据（强制刷新）
                        refreshUserData(context)
                    }
                }.onFailure { _ ->
                    val message = if (isAutoSignIn) {
                        "自动签到失败: 网络请求错误"
                    } else {
                        "签到失败: 网络请求错误"
                    }
                    
                    uiState.value = uiState.value.copy(
                        signStatusMessage = message,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                val message = if (isAutoSignIn) {
                    "自动签到网络错误: ${e.message}"
                } else {
                    "网络错误: ${e.message}"
                }
                
                uiState.value = uiState.value.copy(
                    signStatusMessage = message,
                    isLoading = false
                )
            }
        }
    }
       
    // 加载弦应用商店用户信息
    private fun loadSineShopUserInfo(context: Context) {
        viewModelScope.launch {
            try {
                // 获取弦应用商店token
                val sineShopTokenFlow = AuthManager.getSineMarketToken(context)
                val sineShopToken = sineShopTokenFlow.first()

                // 如果没有token，则显示登录提示
                if (sineShopToken.isNullOrEmpty()) {
                    uiState.value = uiState.value.copy(
                        sineShopLoginPrompt = true,
                        sineShopUserInfo = null
                    )
                    return@launch
                }

                val sineShopUserInfoResult = withContext(Dispatchers.IO) {
                    SineShopClient.getUserInfo(token=sineShopToken)
                }

                sineShopUserInfoResult.onSuccess { userInfo ->
                    uiState.value = uiState.value.copy(
                        sineShopUserInfo = userInfo,
                        sineShopLoginPrompt = false
                    )
                }.onFailure { e ->
                    // 处理失败情况，例如显示错误信息
                    println("Failed to load SineShop user info: ${e.message}")
                    uiState.value = uiState.value.copy(
                        sineShopLoginPrompt = true,
                        sineShopUserInfo = null
                    )
                }
            } catch (e: Exception) {
                println("Error loading SineShop user info: ${e.message}")
                uiState.value = uiState.value.copy(
                    sineShopLoginPrompt = true,
                    sineShopUserInfo = null
                )
            }
        }
    }

    // 更新弦应用商店登录提示状态
    fun updateSineShopLoginState(isLoggedIn: Boolean) {
        uiState.value = uiState.value.copy(sineShopLoginPrompt = !isLoggedIn)
    }

    // 强制刷新用户数据
    fun refreshUserData(context: Context) {
        loadUserData(context, forceRefresh = true)
    }

    // 重置加载状态（用于用户登出等情况）
    fun resetLoadState() {
        uiState.value = uiState.value.copy(
            dataLoadState = DataLoadState.NotLoaded,
            showLoginPrompt = true
        )
    }

    // 计算两个日期之间的天数差
    fun calculateDaysDiff(startDate: String, endDate: String): Int {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val start = format.parse(startDate) ?: Date()
            val end = format.parse(endDate) ?: Date()
            val diff = end.time - start.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            0
        }
    }

    // 重新计算时间差（创建时间到当前时间）
    fun recalculateDaysDiff() {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        val daysDiff = calculateDaysDiff(uiState.value.createTime, currentTime)
        uiState.value = uiState.value.copy(displayDaysDiff = daysDiff)
    }

    fun toggleDarkMode() {
        ThemeManager.toggleTheme()
    }

    fun updateLoginState(isLoggedIn: Boolean) {
        uiState.value = uiState.value.copy(showLoginPrompt = !isLoggedIn)
        // 如果用户登出，重置加载状态
        if (!isLoggedIn) {
            resetLoadState()
        }
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            snackbarHostState.value?.showSnackbar(message)
        }
    }

    fun checkAndUpdateSineShopLoginState(context: Context) {
        viewModelScope.launch {
            try {
                // 获取弦应用商店token
                val sineShopTokenFlow = AuthManager.getSineMarketToken(context)
                val sineShopToken = sineShopTokenFlow.first()

                // 更新登录状态
                val isLoggedIn = !sineShopToken.isNullOrEmpty()
                uiState.value = uiState.value.copy(sineShopLoginPrompt = !isLoggedIn)

                // 如果已登录，加载用户信息
                if (isLoggedIn) {
                    loadSineShopUserInfo(context)
                }
            } catch (e: Exception) {
                println("Error checking SineShop login state: ${e.message}")
                uiState.value = uiState.value.copy(
                    sineShopLoginPrompt = true,
                    sineShopUserInfo = null
                )
            }
        }
    }
}
