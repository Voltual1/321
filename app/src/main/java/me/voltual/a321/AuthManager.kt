//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.voltual.a321.core.proto.UserCredentials
import me.voltual.a321.core.proto.UserCredentialsSerializer

// 定义 DataStore 扩展属性
private val Context.credentialsStore: DataStore<UserCredentials> by dataStore(
    fileName = "user_credentials_v2.pb",
    serializer = UserCredentialsSerializer(AuthManager.getAead())
)

object AuthManager {
    private lateinit var aead: Aead
    private const val KEYSET_NAME = "master_keyset"
    private const val PREF_FILE_NAME = "tink_auth_prefs"
    private const val MASTER_KEY_URI = "android-keystore://auth_master_key"

    fun getAead(): Aead = aead

    /**
     * 初始化 Tink 加密环境 (必须在 Application.onCreate 中调用)
     */
    fun initialize(context: Context) {
        AeadConfig.register()
        
        // Android 下管理 Keyset 的标准做法
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

        aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    // --- 1. 保存逻辑 ---

    suspend fun saveCredentials(
        context: Context,
        token: String,
    ) {
        context.credentialsStore.updateData { current ->
            current.toBuilder()
                .setToken(token)
                .build()
        }
    }    

    // --- 2. 读取逻辑 ---

    fun getCredentials(context: Context): Flow<UserCredentials> = context.credentialsStore.data       

    // --- 3. 清理逻辑 ---

    suspend fun clearCredentials(context: Context) {
        context.credentialsStore.updateData { UserCredentials.getDefaultInstance() }
    }

    private fun generateDeviceId(): String = (1..15).map { (0..9).random() }.joinToString("")
}