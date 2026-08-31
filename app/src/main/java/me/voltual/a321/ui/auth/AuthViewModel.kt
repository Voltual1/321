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
  
  // 缓存当前的二维码图片和会话 ID
  private var currentBitmap: Bitmap? = null
  private var currentUniID: String? = null

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
   * 扫码端：开启扫码流程
   */
  fun startQrLoginFlow() {
    pollingJob?.cancel()
    _qrLoginState.value = QrLoginState.Loading

    viewModelScope.launch {
      KtorClient.ApiServiceImpl.generateQrCode()
        .onSuccess { response ->
          val data = response.data
          if (response.isSuccess && data != null) {
            val finalQrUrl = "https://yun.123pan.cn/wx-app-login.html?env=production&uniID=${data.uniID}&source=123pan&type=login"
            val bitmap = generateQrCodeBitmap(finalQrUrl)
            if (bitmap != null) {
              currentBitmap = bitmap
              currentUniID = data.uniID
              _qrLoginState.value = QrLoginState.QrReady(bitmap, data.uniID)
              startPolling()
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
    currentBitmap = null
    currentUniID = null
    _qrLoginState.value = QrLoginState.Idle
  }

  /**
   * 开启后台自动轮询
   */
  private fun startPolling() {
    val uniID = currentUniID ?: return
    val bitmap = currentBitmap ?: return

    pollingJob = viewModelScope.launch {
      var count = 0
      val maxPollingCount = 120 // 约 3 分钟

      while (count < maxPollingCount) {
        delay(1500)
        count++

        KtorClient.ApiServiceImpl.getQrCodeResult(uniID)
          .onSuccess { response ->
            val result = response.data
            if (response.isSuccess && result != null) {
              when (result.loginStatus) {
                0 -> {
                  _qrLoginState.value = QrLoginState.QrReady(bitmap, uniID)
                }
                1 -> {
                  _qrLoginState.value = QrLoginState.Scanned(bitmap, uniID)
                }
                3 -> {
                  // 关键修复：直接调用换取凭证并退出协程，坚决不在协程内部提前调用 cancel() 导致自我毁灭
                  handleAuthorizationSuccess(uniID, result)
                  return@launch
                }
              }
            }
          }
          .onFailure {
            // 忽略单次网络抖动
          }
      }
      _qrLoginState.value = QrLoginState.Error("二维码已过期，请刷新")
    }
  }

  /**
   * 提供给 UI 层的手动同步按钮点击事件：双重保障逻辑
   */
  fun checkQrStatusManually(onResult: (Boolean) -> Unit) {
    val uniID = currentUniID
    if (uniID.isNullOrBlank()) {
      onResult(false)
      return
    }

    viewModelScope.launch {
      KtorClient.ApiServiceImpl.getQrCodeResult(uniID)
        .onSuccess { response ->
          val result = response.data
          if (response.isSuccess && result != null && result.loginStatus == 3) {
            // 确认用户已经同意登录：此时我们在手动触发的新协程中，可以安全地取消原轮询 Job
            pollingJob?.cancel()
            handleAuthorizationSuccess(uniID, result)
            onResult(true)
          } else {
            onResult(false)
          }
        }
        .onFailure {
          onResult(false)
        }
    }
  }

  /**
   * 统一处理确认授权后的身份换取
   */
  private suspend fun handleAuthorizationSuccess(uniID: String, result: KtorClient.QrResultData) {
    when (result.scanPlatform) {
      7 -> {
        val token = result.token
        if (!token.isNullOrBlank()) {
          _qrLoginState.value = QrLoginState.Success(token)
          saveToken(token)
        } else {
          _qrLoginState.value = QrLoginState.Error("App 确认登录成功，但服务器未下发 Token")
        }
      }
      4 -> {
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
              _qrLoginState.value = QrLoginState.Error("获取微信凭证失败: ${codeRes.message}")
            }
          }
          .onFailure { err ->
            _qrLoginState.value = QrLoginState.Error("微信扫码握手失败: ${err.localizedMessage}")
          }
      }
      else -> {
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