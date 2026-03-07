// File: /app/src/main/java/cc/bbq/xq/ui/auth/LoginViewModel.kt
//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.auth

import android.app.Application
import androidx.lifecycle.*
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.OpenMarketSineWorldClient
import me.voltual.pyrolysis.SineShopClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import org.koin.android.annotation.KoinViewModel
import me.voltual.pyrolysis.LingMarketClient // 确保导入

@KoinViewModel
class LoginViewModel(
    application: Application
) : AndroidViewModel(application) {

    // --- 商店选择状态 ---
    private val _selectedStore = MutableStateFlow(AppStore.XIAOQU_SPACE)
    val selectedStore: StateFlow<AppStore> = _selectedStore.asStateFlow()

    fun onStoreSelected(store: AppStore) {
        _selectedStore.value = store
    }

    // --- 通用状态 ---
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    // --- 登录状态 ---
    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    // --- 注册状态 ---
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _captcha = MutableStateFlow("")
    val captcha: StateFlow<String> = _captcha.asStateFlow()
    
    private val _verificationCodeUrl = MutableStateFlow<String?>(null)
    val verificationCodeUrl: StateFlow<String?> = _verificationCodeUrl.asStateFlow()

    // --- 事件处理 ---
    fun onUsernameChange(newUsername: String) { _username.value = newUsername }
    fun onPasswordChange(newPassword: String) { _password.value = newPassword }
    fun onEmailChange(newEmail: String) { _email.value = newEmail }
    fun onCaptchaChange(newCaptcha: String) { _captcha.value = newCaptcha }

    // --- 业务逻辑 ---

    fun login() {
    if (_username.value.isBlank() || _password.value.isBlank()) {
        _errorMessage.value = "用户名和密码不能为空"
        return
    }
    viewModelScope.launch {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            when (_selectedStore.value) {
                AppStore.XIAOQU_SPACE -> loginXiaoqu()
                AppStore.SIENE_SHOP -> loginSineShop()
                AppStore.SINE_OPEN_MARKET -> loginSineOpenMarket()
                AppStore.LING_MARKET -> loginLingMarket() // 新增灵应用商店
                else -> {
                    _errorMessage.value = "不支持登录"
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "登录异常: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}

    // --- 平台特定登录逻辑 ---

    /**
     * 小趣空间登录逻辑
     */
    private suspend fun loginXiaoqu() {
        try {
            val context: Application = getApplication()
            val deviceIdFlow: Flow<String> = AuthManager.getDeviceId(context)
            val deviceId = deviceIdFlow.first()
            
            val loginResult = KtorClient.ApiServiceImpl.login(
                username = _username.value,
                password = _password.value,
                device = deviceId
            )
            
            if (loginResult.isSuccess) {
                val loginResponse = loginResult.getOrNull()
                if (loginResponse?.code == 1) {
                    loginResponse.data?.let { 
                        // 小趣空间是主账号，使用 saveCredentials 保存完整信息
                        saveCredentialsAndNotifySuccess(
                            usertoken = it.usertoken,
                            userId = it.id
                        )
                    } ?: run {
                        _errorMessage.value = "登录失败: 无法获取用户信息"
                    }
                } else {
                    _errorMessage.value = loginResponse?.msg ?: "登录失败"
                }
            } else {
                _errorMessage.value = loginResult.exceptionOrNull()?.message ?: "登录失败"
            }
        } catch (e: Exception) {
            _errorMessage.value = "网络错误: ${e.message}"
        }
    }
    
    /**
 * 灵应用商店登录逻辑
 */
private suspend fun loginLingMarket() {
    try {
        val loginResult = LingMarketClient.login(
            username = _username.value,
            password = _password.value
        )
        
        if (loginResult.isSuccess) {
            val response = loginResult.getOrNull()
            // 根据抓包：成功时 code 为 null 或 200/201
            if (response != null && response.isSuccess && response.token != null) {
                val context: Application = getApplication()
                // 调用之前在 AuthManager 中新增的保存方法
                AuthManager.saveLingMarketToken(context, response.token)
                _loginSuccess.value = true
            } else {
                _errorMessage.value = response?.msg ?: "灵应用商店登录失败：未知错误"
            }
        } else {
            _errorMessage.value = "灵应用商店登录失败: ${loginResult.exceptionOrNull()?.message}"
        }
    } catch (e: Exception) {
        _errorMessage.value = "灵应用商店连接失败: ${e.message}"
    }
}

    /**
     * 弦应用商店登录逻辑
     */
    private suspend fun loginSineShop() {
        try {
            val loginResult = SineShopClient.login(
                username = _username.value,
                password = _password.value
            )
            if (loginResult.isSuccess) {
                val token = loginResult.getOrNull() ?: ""
                if (token.isNotEmpty()) {
                    // 保存弦应用商店专用 Token
                    val context: Application = getApplication()
                    AuthManager.saveSineMarketToken(context, token)
                    _loginSuccess.value = true // 登录成功，触发导航
                } else {
                    _errorMessage.value = "弦应用商店登录失败: 无法获取token"
                }
            } else {
                _errorMessage.value = "弦应用商店登录失败: ${loginResult.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            _errorMessage.value = "弦应用商店登录失败: ${e.message}"
        }
    }

    /**
     * 弦-开放平台登录逻辑
     */
    private suspend fun loginSineOpenMarket() {
        try {
            val loginResult = OpenMarketSineWorldClient.login(
                username = _username.value,
                password = _password.value
            )
            
            if (loginResult.isSuccess) {
                val loginData = loginResult.getOrNull()
                if (loginData != null && loginData.token.isNotEmpty()) {
                    // 保存弦-开放平台专用 Token
                    val context: Application = getApplication()
                    AuthManager.saveSineOpenMarketToken(context, loginData.token)
                    _loginSuccess.value = true // 登录成功，触发导航
                } else {
                    _errorMessage.value = "开放平台登录失败: 返回数据无效"
                }
            } else {
                _errorMessage.value = "开放平台登录失败: ${loginResult.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            _errorMessage.value = "开放平台登录失败: ${e.message}"
        }
    }

    // --- 注册逻辑 (仅限小趣空间) ---

    fun register() {
        if (_selectedStore.value != AppStore.XIAOQU_SPACE) {
            _errorMessage.value = "当前平台不支持在此注册"
            return
        }

        if (_username.value.isBlank() || _password.value.isBlank() || _email.value.isBlank() || _captcha.value.isBlank()) {
            _errorMessage.value = "请填写所有注册信息"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val context: Application = getApplication()
                val deviceIdFlow: Flow<String> = AuthManager.getDeviceId(context)
                val deviceId = deviceIdFlow.first()

                val registerResult = KtorClient.ApiServiceImpl.register(
                    username = _username.value,
                    password = _password.value,
                    email = _email.value,
                    device = deviceId,
                    captcha = _captcha.value
                )
                if (registerResult.isSuccess) {
                    val registerResponse = registerResult.getOrNull()
                    if (registerResponse?.code == 1) {
                        // 注册成功，自动登录
                        loginAfterRegister()
                    } else {
                        _errorMessage.value = registerResponse?.msg ?: "注册失败"
                        loadVerificationCode()
                    }
                } else {
                    _errorMessage.value = registerResult.exceptionOrNull()?.message ?: "注册失败"
                    loadVerificationCode()
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loginAfterRegister() {
        try {
            val context: Application = getApplication()
             val deviceIdFlow: Flow<String> = AuthManager.getDeviceId(context)
                val deviceId = deviceIdFlow.first()
            val loginResult = KtorClient.ApiServiceImpl.login(
                username = _username.value,
                password = _password.value,
                device = deviceId
            )
            if (loginResult.isSuccess) {
                 val loginResponse = loginResult.getOrNull()
                if (loginResponse?.code == 1) {
                    loginResponse.data?.let { 
                        saveCredentialsAndNotifySuccess(
                            usertoken = it.usertoken,
                            userId = it.id
                        )
                    } ?: run {
                        _errorMessage.value = "登录失败: 无法获取用户信息"
                    }
                } else {
                    _errorMessage.value = "注册成功，但自动登录失败: ${loginResponse?.msg}"
                }
            } else {
                _errorMessage.value = "注册成功，但自动登录时网络错误: ${loginResult.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            _errorMessage.value = "注册成功，但自动登录时网络错误: ${e.message}"
        }
    }

    fun loadVerificationCode() {
        _verificationCodeUrl.value = "http://apk.xiaoqu.online/api/get_image_verification_code?appid=1&type=2&t=${System.currentTimeMillis()}"
    }

    private suspend fun saveCredentialsAndNotifySuccess(usertoken: String, userId: Long) {
        val context: Application = getApplication()
        AuthManager.saveCredentials(
            context, _username.value, _password.value, usertoken, userId
        )
        _loginSuccess.value = true
    }
}