//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
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

    private val loginUrl = "https://login.123pan.com/centerlogin?redirect_url=https%3A%2F%2Fwww.123pan.com%2F%3Fnotoken%3D1&source_page=website"

    fun getInitialUrl() = loginUrl

    /**
     * 核心逻辑：检查 URL 并提取 Cookie
     */
    fun checkAndExtractToken(url: String) {
        if (url.contains("https://www.123pan.com/?notoken=1")) {
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie(url) ?: return
            
            val token = cookies.split(";")
                .map { it.trim() }
                .find { it.startsWith("sso-token=") }
                ?.substringAfter("sso-token=")

            if (!token.isNullOrBlank()) {
                saveToken(token)
            }
        }
    }

    private fun saveToken(token: String) {
        viewModelScope.launch {
            // 调用你定义的 AuthManager 保存到 DataStore
            AuthManager.saveCredentials(getApplication(), token)
            _isLoginSuccess.value = true
        }
    }
}