// AuthViewModel.kt
package me.voltual.a321.ui.auth

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.voltual.a321.AuthManager

class AuthViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val _isLoginSuccess = MutableStateFlow(false)
    val isLoginSuccess = _isLoginSuccess.asStateFlow()

    // 默认登录页
    private val loginUrl = "https://login.123pan.com/centerlogin?redirect_url=https%3A%2F%2Fwww.123pan.com%2F%3Fnotoken%3D1&source_page=website"
    
    // 强制提取 Cookie 的目标 URL
    private val targetCookieUrl = "https://login.123pan.com/"

    fun getInitialUrl() = loginUrl

    /**
     * 核心逻辑：提取并校验 Token
     * @param manual 是否由用户点击按钮触发
     */
    fun checkAndExtractToken(manual: Boolean = false): Boolean {
        val cookieManager = CookieManager.getInstance()
        //直接获取 login.123pan.com 的 Cookie
        val cookies = cookieManager.getCookie(targetCookieUrl) ?: ""
        
        val token = cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("sso-token=") }
            ?.substringAfter("sso-token=")

        return if (!token.isNullOrBlank()) {
            saveToken(token)
            true
        } else {
            false
        }
    }

    private fun saveToken(token: String) {
        viewModelScope.launch {
            AuthManager.saveCredentials(getApplication(), token)
            _isLoginSuccess.value = true
        }
    }
}