package me.voltual.a321.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.source

/**
 * 包装输入流，限制最多读取 [bytesRemaining] 字节。
 * [closeDelegate] 控制当本流关闭时是否同时关闭底层传入的 [delegate] 流。
 */
class LimitedInputStream(
  private val delegate: InputStream,
  private var bytesRemaining: Long,
  private val closeDelegate: Boolean = false,
) : InputStream() {

  override fun read(): Int {
    if (bytesRemaining <= 0) return -1
    val result = delegate.read()
    if (result != -1) {
      bytesRemaining--
    }
    return result
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if (bytesRemaining <= 0) return -1
    val maxToRead = minOf(len.toLong(), bytesRemaining).toInt()
    val bytesRead = delegate.read(b, off, maxToRead)
    if (bytesRead > 0) {
      bytesRemaining -= bytesRead
    }
    return bytesRead
  }

  override fun available(): Int {
    return minOf(delegate.available().toLong(), bytesRemaining).toInt()
  }

  override fun close() {
    if (closeDelegate) {
      delegate.close()
    }
  }
}

object PanUtils {
  /** 使用 Okio 高效计算 Uri 的 MD5 */
  fun calcMd5(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
    val hashingSource = HashingSource.md5(inputStream.source())
    hashingSource.buffer().use { it.readAll(blackholeSink()) }
    return hashingSource.hash.hex()
  }

  /** 使用 Okio 高效计算 Uri 的 SHA-256 (用于中国移动云盘秒传与分片确认) */
  fun calcSha256(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
    val hashingSource = HashingSource.sha256(inputStream.source())
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

  /**
   * 创建指定长度的流式 Ktor OutgoingContent，解决大文件/大分片上传时的 OOM 问题。
   */
  fun createStreamContent(
    inputStream: InputStream,
    length: Long,
    closeStreamOnClose: Boolean = false,
  ): OutgoingContent.ReadChannelContent {
    val limitedStream = LimitedInputStream(inputStream, length, closeDelegate = closeStreamOnClose)
    return object : OutgoingContent.ReadChannelContent() {
      override val contentLength: Long = length
      override val contentType: ContentType = ContentType.Application.OctetStream
      override fun readFrom(): ByteReadChannel = limitedStream.toByteReadChannel(Dispatchers.IO)
    }
  }
}