//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.core.proto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

/**
 * 使用 Tink AEAD 加密保护的 Protobuf 序列化器
 */
class UserCredentialsSerializer(private val aead: Aead) : Serializer<UserCredentials> {
    
    override val defaultValue: UserCredentials = UserCredentials.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): UserCredentials {
        return try {
            val encryptedData = input.readBytes()
            if (encryptedData.isEmpty()) {
                return defaultValue
            }
            
            // 使用 Tink 解密
            val decryptedData = aead.decrypt(encryptedData, null)
            
            // 使用 Full Protobuf 的解析方式
            UserCredentials.parseFrom(decryptedData)
        } catch (exception: Exception) {
            when (exception) {
                is InvalidProtocolBufferException, is GeneralSecurityException -> {
                    throw CorruptionException("解密失败或数据格式错误", exception)
                }
                else -> throw exception
            }
        }
    }

    override suspend fun writeTo(t: UserCredentials, output: OutputStream) {
        val rawBytes = t.toByteArray()
        val encryptedData = aead.encrypt(rawBytes, null)
        output.write(encryptedData)
    }
}