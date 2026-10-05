// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.Flow
import me.voltual.a321.core.proto.UserCredentials
import me.voltual.a321.core.proto.UserCredentialsSerializer
import me.voltual.a321.data.unified.PanPlatform

private val Context.credentialsStore: DataStore<UserCredentials> by
  dataStore(
    fileName = "user_credentials_v2.pb",
    serializer = UserCredentialsSerializer(AuthManager.getAead()),
  )

object AuthManager {
  private lateinit var aead: Aead
  private const val KEYSET_NAME = "master_keyset"
  private const val PREF_FILE_NAME = "tink_auth_prefs"
  private const val MASTER_KEY_URI = "android-keystore://auth_master_key"

  fun getAead(): Aead = aead

  fun initialize(context: Context) {
    AeadConfig.register()

    val keysetHandle =
      AndroidKeysetManager.Builder()
        .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri(MASTER_KEY_URI)
        .build()
        .keysetHandle

    aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
  }

  suspend fun saveCredentials(context: Context, platform: PanPlatform, rawToken: String) {
    val fullToken = "${platform.id}|$rawToken"
    context.credentialsStore.updateData { current ->
      val builder = current.toBuilder()
        .setToken(fullToken)
        .setActivePlatform(platform.id)

      when (platform) {
        PanPlatform.PAN123 -> builder.setToken123Pan(rawToken)
        PanPlatform.CLOUD139 -> builder.setTokenCloud139(rawToken)
      }

      builder.build()
    }
    // 过河拆桥：保存 Token 后立即擦除 WebView 中的 Cookie 痕迹，防止二次进入登录页时发生自动重定向
    clearWebViewCookies()
  }

  suspend fun switchPlatform(context: Context, platform: PanPlatform) {
    context.credentialsStore.updateData { current ->
      val targetToken = when (platform) {
        PanPlatform.PAN123 -> current.token123Pan
        PanPlatform.CLOUD139 -> current.tokenCloud139
      }
      val fullToken = if (targetToken.isNotEmpty()) "${platform.id}|$targetToken" else ""
      current.toBuilder()
        .setToken(fullToken)
        .setActivePlatform(platform.id)
        .build()
    }
  }

  fun getCredentials(context: Context): Flow<UserCredentials> = context.credentialsStore.data

  suspend fun clearCredentials(context: Context) {
    context.credentialsStore.updateData { UserCredentials.getDefaultInstance() }
    clearWebViewCookies()
  }

  fun clearWebViewCookies() {
    val cookieManager = CookieManager.getInstance()
    cookieManager.removeSessionCookies {}
    cookieManager.removeAllCookies {
      cookieManager.flush()
    }
  }
}