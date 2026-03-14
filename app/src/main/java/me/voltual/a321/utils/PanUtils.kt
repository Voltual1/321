package me.voltual.a321.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

object PanUtils {
    /**
     * 计算文件的 MD5 值 (用于秒传)
     */
    fun calcFileMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(65536)
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 生成随机 LoginUuid
     */
    fun generateLoginUuid(): String = UUID.randomUUID().toString().replace("-", "")

    // Android 设备型号池
    private val DEVICE_TYPES = listOf("24075RP89G", "M2012K11AG", "22021211RG", "21121210G")
    
    fun getRandomDeviceType(): String = DEVICE_TYPES.random()
}