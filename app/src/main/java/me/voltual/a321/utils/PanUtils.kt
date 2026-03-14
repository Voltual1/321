package me.voltual.a321.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

object PanUtils {
    /**
     * 计算文件的 MD5 值
     */
    fun calcFileMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(65536)
        FileInputStream(file).use { fis ->
            var read = fis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 生成随机 LoginUuid
     */
    fun generateLoginUuid(): String = UUID.randomUUID().toString().replace("-", "")

    /**
     * 格式化文件大小
     */
    fun formatSize(sizeBytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var size = sizeBytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return "%.2f %s".format(size, units[unitIndex])
    }

    // 预设的安卓设备型号，参考 Python 原型
    val DEVICE_TYPES = listOf("24075RP89G", "M2012K11AG", "22021211RG", "23113RKC6G", "M2004J19PI")
    val OS_VERSIONS = listOf("Android_10", "Android_11", "Android_12", "Android_13")
}