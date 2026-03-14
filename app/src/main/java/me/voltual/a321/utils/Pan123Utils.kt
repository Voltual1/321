package me.voltual.a321.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

object Pan123Utils {
    const val ANDROID_APP_VERSION = "61"
    const val ANDROID_X_APP_VERSION = "3.1.3"
    const val ANDROID_DEVICE_BRAND = "Xiaomi"

    private val DEVICE_TYPES = listOf("24075RP89G", "M2012K11AG", "2201116PG", "23122PCD1G")
    private val OS_VERSIONS = listOf("Android_11", "Android_12", "Android_13")

    fun generateLoginUuid(): String = UUID.randomUUID().toString().replace("-", "")

    fun getRandomDeviceType(): String = DEVICE_TYPES.random()

    fun getRandomOsVersion(): String = OS_VERSIONS.random()

    /**
     * 计算文件的 MD5 值（用于秒传检查）
     */
    fun calculateMd5(file: File): String {
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
     * 格式化 User-Agent
     */
    fun getUserAgent(osVersion: String): String {
        return "123pan/v$ANDROID_X_APP_VERSION($osVersion;$ANDROID_DEVICE_BRAND)"
    }
}