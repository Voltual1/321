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
  data class Scanned(val bitmap: Bitmap, val uniID: String) : QrLoginState 
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
   * 扫码端：开启扫码流程（硬编码规范路径，确保微信和小程序完美识别）
   */
  fun startQrLoginFlow() {
    pollingJob?.cancel()
    _qrLoginState.value = QrLoginState.Loading

    viewModelScope.launch {
      KtorClient.ApiServiceImpl.generateQrCode()
        .onSuccess { response ->
          val data = response.data
          if (response.isSuccess && data != null) {
            // 强制将基础域名指向官方标准的 wx-app-login.html 授权端点。
            // 绝不使用可能产生普通网页误导的 qr-scan-page 路径，保证微信直接拉起小程序。
            val finalQrUrl = "https://yun.123pan.cn/wx-app-login.html?env=production&uniID=${data.uniID}&source=123pan&type=login"

            val bitmap = generateQrCodeBitmap(finalQrUrl)
            if (bitmap != null) {
              _qrLoginState.value = QrLoginState.QrReady(bitmap, data.uniID)
              startPolling(data.uniID, bitmap)
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
  private fun startPolling(uniID: String, bitmap: Bitmap) {
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
                  // 回到等待扫码状态，直接恢复显示已有的二维码，不做任何网络重置！
                  _qrLoginState.value = QrLoginState.QrReady(bitmap, uniID)
                }
                1 -> {
                  // 已扫码，等待确认。在Scanned状态中同样传入并保留bitmap。
                  _qrLoginState.value = QrLoginState.Scanned(bitmap, uniID)
                }
                3 -> {
                  // 扫码授权成功！开始兑换 Token
                  pollingJob?.cancel() // 停止轮询
                  handleAuthorizationSuccess(uniID, result)
                  return@launch
                }
                else -> {
                  // 容错处理
                }
              }
            }
          }
          .onFailure {
            // 轮询单次请求异常不阻断，继续尝试
          }
      }
      _qrLoginState.value = QrLoginState.Error("二维码已过期，请刷新")
    }
  }

  /**
   * 统一处理确认授权后的身份换取
   * 关键重构：使用 when 语句对平台做强制分类判定，提供 100% 覆盖的安全保障
   */
  private suspend fun handleAuthorizationSuccess(uniID: String, result: KtorClient.QrResultData) {
    when (result.scanPlatform) {
      7 -> {
        // 1. 官方 App 扫码直登：直接解析返回的 Token
        val token = result.token
        if (!token.isNullOrBlank()) {
          _qrLoginState.value = QrLoginState.Success(token)
          saveToken(token)
        } else {
          _qrLoginState.value = QrLoginState.Error("App 确认登录成功，但服务器未下发身份凭证(Token)")
        }
      }
      4 -> {
        // 2. 微信扫码登录：需要通过微信特有的 WxCode 进行二次置换
        KtorClient.ApiServiceImpl.getWxCode(uniID)
          .onSuccess { codeRes ->
            val wxData = codeRes.data
            if (codeRes.isSuccess && wxData != null) {
              KtorClient.ApiServiceImpl.loginWithWechatCode(wxData.wxCode)
                .onSuccess { loginRes ->
                  val token = loginRes.data?.token
                  if (loginRes.isSuccess && !token.isNullOrBlank()) {
                    _qrLoginState.value = QrLoginState.Success(token)
                    saveToken(token)
                  } else {
                    _qrLoginState.value = QrLoginState.Error(
                      if (loginRes.message.isNotBlank()) loginRes.message else "微信授权登录失败，请稍后重试"
                    )
                  }
                }
                .onFailure { err ->
                  _qrLoginState.value = QrLoginState.Error("微信身份置换网络错误: ${err.localizedMessage}")
                }
            } else {
              _qrLoginState.value = QrLoginState.Error("获取微信虚拟凭证失败: ${codeRes.message}")
            }
          }
          .onFailure { err ->
            _qrLoginState.value = QrLoginState.Error("微信扫码握手失败: ${err.localizedMessage}")
          }
      }
      else -> {
        // 3. 安全防御分支：阻止任何未知平台导致的静默失败卡死
        _qrLoginState.value = QrLoginState.Error("暂不支持此平台的扫码授权 (Platform Code: ${result.scanPlatform})")
      }
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