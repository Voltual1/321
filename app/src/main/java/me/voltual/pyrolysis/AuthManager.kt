//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
package me.voltual.pyrolysis

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
import me.voltual.pyrolysis.core.proto.UserCredentials
import me.voltual.pyrolysis.core.proto.UserCredentialsSerializer

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
        username: String,
        password: String,
        token: String,
        userId: Long
    ) {
        context.credentialsStore.updateData { current ->
            current.toBuilder()
                .setUsername(username)
                .setPassword(password)
                .setToken(token)
                .setUserId(userId)
                // 如果设备 ID 为空则生成一个新的
                .setDeviceId(current.deviceId.ifEmpty { generateDeviceId() })
                .build()
        }
    }

    suspend fun saveSineMarketToken(context: Context, token: String) {
        context.credentialsStore.updateData { it.toBuilder().setSineMarketToken(token).build() }
    }

    suspend fun saveSineOpenMarketToken(context: Context, token: String) {
        context.credentialsStore.updateData { it.toBuilder().setSineOpenMarketToken(token).build() }
    }

    suspend fun saveLingMarketToken(context: Context, token: String) {
        context.credentialsStore.updateData { it.toBuilder().setLingMarketToken(token).build() }
    }

    // --- 2. 读取逻辑 ---

    fun getCredentials(context: Context): Flow<UserCredentials> = context.credentialsStore.data

    fun getSineMarketToken(context: Context): Flow<String> = 
        getCredentials(context).map { it.sineMarketToken }

    fun getSineOpenMarketToken(context: Context): Flow<String> = 
        getCredentials(context).map { it.sineOpenMarketToken }

    fun getLingMarketToken(context: Context): Flow<String> = 
        getCredentials(context).map { it.lingMarketToken }

    fun getUserId(context: Context): Flow<Long> = 
        getCredentials(context).map { it.userId }

    fun getDeviceId(context: Context): Flow<String> = 
        getCredentials(context).map { it.deviceId.ifEmpty { generateDeviceId() } }

    // --- 3. 清理逻辑 ---

    suspend fun clearCredentials(context: Context) {
        context.credentialsStore.updateData { UserCredentials.getDefaultInstance() }
    }

    private fun generateDeviceId(): String = (1..15).map { (0..9).random() }.joinToString("")
}