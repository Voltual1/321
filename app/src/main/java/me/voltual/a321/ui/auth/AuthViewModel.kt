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
import me.voltual.a321.Cloud139Client
import me.voltual.a321.KtorClient
import me.voltual.a321.data.unified.PanPlatform

enum class LoginUrlConfig(val platform: PanPlatform, val loginUrl: String) {
  PAN123(
    PanPlatform.PAN123,
    "https://login.123pan.com/centerlogin?redirect_url=https%3A%2F%2Fwww.123pan.com%2F%3Fnotoken%3D1&source_page=website"
  ),
  CLOUD139(
    PanPlatform.CLOUD139,
    "https://yun.139.com/m/#/login"
  );

  companion object {
    fun getUrl(platform: PanPlatform): String {
      return entries.find { it.platform == platform }?.loginUrl ?: PAN123.loginUrl
    }
  }
}

sealed interface QrLoginState {
  object Idle : QrLoginState
  object Loading : QrLoginState
  data class QrReady(val bitmap: Bitmap, val uniID: String) : QrLoginState
  data class Scanned(val bitmap: Bitmap, val uniID: String) : QrLoginState 
  data class Success(val token: String) : QrLoginState
  data class Error(val message: String) : QrLoginState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

  private val _selectedPlatform = MutableStateFlow(PanPlatform.PAN123)
  val selectedPlatform = _selectedPlatform.asStateFlow()

  private val _isLoginSuccess = MutableStateFlow(false)
  val isLoginSuccess = _isLoginSuccess.asStateFlow()

  private val _qrLoginState = MutableStateFlow<QrLoginState>(QrLoginState.Idle)
  val qrLoginState = _qrLoginState.asStateFlow()

  private var pollingJob: Job? = null
  private var currentBitmap: Bitmap? = null
  private var currentUniID: String? = null

  private val target123CookieUrl = "https://user.123pan.cn/"
  private val target139CookieUrl = "https://yun.139.com/"

  fun selectPlatform(platform: PanPlatform) {
    _selectedPlatform.value = platform
    stopQrLoginFlow()
  }

  fun getInitialUrl(): String {
    return LoginUrlConfig.getUrl(_selectedPlatform.value)
  }

  /**
   * 检查 Cookie 并提取 Token
   */
  fun checkAndExtractToken(manual: Boolean = false): Boolean {
    val platform = _selectedPlatform.value
    val cookieManager = CookieManager.getInstance()

    return when (platform) {
      PanPlatform.PAN123 -> {
        val cookies = cookieManager.getCookie(target123CookieUrl) ?: ""
        val token = cookies
          .split(";")
          .map { it.trim() }
          .find { it.startsWith("sso-token=") }
          ?.substringAfter("sso-token=")

        if (!token.isNullOrBlank()) {
          saveToken(platform, token)
          true
        } else {
          false
        }
      }
      PanPlatform.CLOUD139 -> {
        val cookies = cookieManager.getCookie(target139CookieUrl) ?: ""
        val rawAuth = cookies
          .split(";")
          .map { it.trim() }
          .find { it.startsWith("authorization=") }
          ?.substringAfter("authorization=")

        if (!rawAuth.isNullOrBlank()) {
          Cloud139Client.login(rawAuth)
          saveToken(platform, rawAuth)
          true
        } else {
          false
        }
      }
    }
  }

  fun startQrLoginFlow() {
    if (_selectedPlatform.value != PanPlatform.PAN123) return

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

  fun stopQrLoginFlow() {
    pollingJob?.cancel()
    pollingJob = null
    currentBitmap = null
    currentUniID = null
    _qrLoginState.value = QrLoginState.Idle
  }

  private fun startPolling() {
    val uniID = currentUniID ?: return
    val bitmap = currentBitmap ?: return

    pollingJob = viewModelScope.launch {
      var count = 0
      val maxPollingCount = 120

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
                  handleAuthorizationSuccess(uniID, result)
                  return@launch
                }
              }
            }
          }
          .onFailure { }
      }
      _qrLoginState.value = QrLoginState.Error("二维码已过期，请刷新")
    }
  }

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

  private suspend fun handleAuthorizationSuccess(uniID: String, result: KtorClient.QrResultData) {
    when (result.scanPlatform) {
      7 -> {
        val token = result.token
        if (!token.isNullOrBlank()) {
          _qrLoginState.value = QrLoginState.Success(token)
          saveToken(PanPlatform.PAN123, token)
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
                    saveToken(PanPlatform.PAN123, token)
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

  private fun saveToken(platform: PanPlatform, rawToken: String) {
    viewModelScope.launch {
      AuthManager.saveCredentials(getApplication(), platform, rawToken)
      _isLoginSuccess.value = true
    }
  }

  override fun onCleared() {
    super.onCleared()
    pollingJob?.cancel()
  }
}