package me.voltual.a321.utils

import android.content.Context
import android.net.Uri
import android.os.Build
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

  /** 获取设备型号，例如 "24075RP89G" */
  fun getDeviceModel(): String = Build.MODEL

  /** 获取设备品牌/厂商，例如 "Xiaomi" 或 "Samsung" */
  fun getDeviceBrand(): String = Build.MANUFACTURER

  /** 获取系统的 Android 版本名，例如 "13" 或 "14" */
  fun getOsVersion(): String = Build.VERSION.RELEASE

  // 更详细的格式如 "Android_13"
  fun getFormattedOsVersion(): String = "Android_${Build.VERSION.RELEASE}"
}
