// AuthViewModel.kt
package me.voltual.a321.ui.auth

import android.app.Application
import android.graphics.Bitmap
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.voltual.a321.AuthManager
import me.voltual.a321.KtorClient

sealed interface QrLoginState {
  object Idle : QrLoginState
  object Loading : QrLoginState
  data class QrReady(val bitmap: Bitmap, val uniID: String) : QrLoginState
  data class Success(val token: String) : QrLoginState
  data class Error(val message: String) : QrLoginState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

  private val _isLoginSuccess = MutableStateFlow(false)
  val isLoginSuccess = _isLoginSuccess.asStateFlow()

  // 扫码状态机
  private val _qrLoginState = MutableStateFlow<QrLoginState>(QrLoginState.Idle)
  val qrLoginState = _qrLoginState.asStateFlow()

  private var pollingJob: Job? = null

  // 默认网页登录页
  private val loginUrl =
    "https://login.123pan.com/centerlogin?redirect_url=https%3A%2F%2Fwww.123pan.com%2F%3Fnotoken%3D1&source_page=website"

  // 强制提取 Cookie 的目标 URL
  private val targetCookieUrl = "https://user.123pan.cn/"

  fun getInitialUrl() = loginUrl

  /**
   * 网页端：提取并校验 Cookie 中的 Token
   */
  fun checkAndExtractToken(manual: Boolean = false): Boolean {
    val cookieManager = CookieManager.getInstance()
    val cookies = cookieManager.getCookie(targetCookieUrl) ?: ""

    val token =
      cookies
        .split(";")
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

  /**
   * 扫码端：开启扫码流程（生成包含参数的正确登录授权二维码）
   */
  fun startQrLoginFlow() {
    pollingJob?.cancel()
    _qrLoginState.value = QrLoginState.Loading

    viewModelScope.launch {
      KtorClient.ApiServiceImpl.generateQrCode()
        .onSuccess { response ->
          val data = response.data
          if (response.isSuccess && data != null) {
            // 关键修复：拼接出官方 App 可识别的特定安全登录 URL
            val baseUrl = data.url
            val separator = if (baseUrl.contains("?")) "&" else "?"
            val finalQrUrl = "${baseUrl}${separator}env=production&uniID=${data.uniID}&source=123pan&type=login"

            val bitmap = generateQrCodeBitmap(finalQrUrl)
            if (bitmap != null) {
              _qrLoginState.value = QrLoginState.QrReady(bitmap, data.uniID)
              startPolling(data.uniID)
            } else {
              _qrLoginState.value = QrLoginState.Error("生成二维码图片失败")
            }
          } else {
            _qrLoginState.value = QrLoginState.Error(response.message)
          }
        }
        .onFailure { error ->
          _qrLoginState.value = QrLoginState.Error(error.localizedMessage ?: "网络错误，请稍后重试")
        }
    }
  }

  /**
   * 停止轮询
   */
  fun stopQrLoginFlow() {
    pollingJob?.cancel()
    pollingJob = null
    _qrLoginState.value = QrLoginState.Idle
  }

  /**
   * 开启轮询，自动支持 App 扫码直登 与 微信扫码授权二次置换
   */
  private fun startPolling(uniID: String) {
    pollingJob = viewModelScope.launch {
      var count = 0
      val maxPollingCount = 120 // 约 3 分钟有效期

      while (count < maxPollingCount) {
        delay(1500) // 1.5 秒轮询一次
        count++

        KtorClient.ApiServiceImpl.getQrCodeResult(uniID)
          .onSuccess { response ->
            val result = response.data
            if (response.isSuccess && result != null) {
              when (result.loginStatus) {
                0 -> {
                  // 继续等待扫码
                }
                3 -> {
                  // 扫码授权成功！开始兑换 Token
                  pollingJob?.cancel() // 停止轮询
                  handleAuthorizationSuccess(uniID, result)
                  return@launch
                }
                else -> {
                  // 其他非预期状态，退出
                  pollingJob?.cancel()
                  _qrLoginState.value = QrLoginState.Error("二维码授权状态异常")
                  return@launch
                }
              }
            }
          }
          .onFailure {
            // 轮询异常不直接阻断，继续尝试
          }
      }
      _qrLoginState.value = QrLoginState.Error("二维码已过期，请刷新")
    }
  }

  /**
   * 统一处理确认授权后的身份换取
   */
  private suspend fun handleAuthorizationSuccess(uniID: String, result: KtorClient.QrResultData) {
    // 情况 A：123云盘 App 扫码 (scanPlatform == 7 / login_type == 7) -> 轮询结果中直接有 token
    if (result.scanPlatform == 7 && !result.token.isNullOrBlank()) {
      _qrLoginState.value = QrLoginState.Success(result.token)
      saveToken(result.token)
      return
    }

    // 情况 B：微信扫码 (scanPlatform == 4) -> 需要走 微信Code 换 Token 的置换流
    KtorClient.ApiServiceImpl.getWxCode(uniID)
      .onSuccess { codeRes ->
        val wxData = codeRes.data
        if (codeRes.isSuccess && wxData != null) {
          // 用 wxCode 登录
          KtorClient.ApiServiceImpl.loginWithWechatCode(wxData.wxCode)
            .onSuccess { loginRes ->
              val token = loginRes.data?.token
              if (loginRes.isSuccess && !token.isNullOrBlank()) {
                _qrLoginState.value = QrLoginState.Success(token)
                saveToken(token)
              } else {
                _qrLoginState.value = QrLoginState.Error(loginRes.message)
              }
            }
            .onFailure { err ->
              _qrLoginState.value = QrLoginState.Error("微信一键登录失败: ${err.localizedMessage}")
            }
        } else {
          _qrLoginState.value = QrLoginState.Error("获取微信凭证失败: ${codeRes.message}")
        }
      }
      .onFailure { err ->
        _qrLoginState.value = QrLoginState.Error("获取微信临时凭证失败: ${err.localizedMessage}")
      }
  }

  /**
   * 使用 ZXing 生成二维码 Bitmap 图像
   */
  private fun generateQrCodeBitmap(content: String, width: Int = 512, height: Int = 512): Bitmap? {
    return try {
      val bitMatrix: BitMatrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        width,
        height
      )
      val qrWidth = bitMatrix.width
      val qrHeight = bitMatrix.height
      val pixels = IntArray(qrWidth * qrHeight)
      for (y in 0 until qrHeight) {
        val offset = y * qrWidth
        for (x in 0 until qrWidth) {
          pixels[offset + x] = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
      }
      Bitmap.createBitmap(qrWidth, qrHeight, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, qrWidth, 0, 0, qrWidth, qrHeight)
      }
    } catch (e: Exception) {
      null
    }
  }

  private fun saveToken(token: String) {
    viewModelScope.launch {
      AuthManager.saveCredentials(getApplication(), token)
      _isLoginSuccess.value = true
    }
  }

  override fun onCleared() {
    super.onCleared()
    pollingJob?.cancel()
  }
}