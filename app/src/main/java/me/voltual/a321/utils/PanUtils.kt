package me.voltual.a321.utils

import android.content.Context
import android.net.Uri
import java.util.UUID
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.source

object PanUtils {
  /** 使用 Okio 高效计算 Uri 的 MD5 */
  fun calcMd5(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
    val hashingSource = HashingSource.md5(inputStream.source())
    hashingSource.buffer().use { it.readAll(blackholeSink()) }
    return hashingSource.hash.hex()
  }

  fun generateLoginUuid(): String = UUID.randomUUID().toString().replace("-", "")

  private val DEVICE_TYPES = listOf("24075RP89G", "M2012K11AG", "22021211RG", "21121210G")

  fun getRandomDeviceType(): String = DEVICE_TYPES.random()
}
