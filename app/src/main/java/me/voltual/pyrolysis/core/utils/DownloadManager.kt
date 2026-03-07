//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.core.utils

import android.app.Activity

/**
 * 统一下载管理器
 * 封装第三方下载器的调用细节，解耦业务逻辑
 */
object DownloadManager {

    /**
     * 调起 1DM 下载文件
     * @param activity 上下文
     * @param url 下载地址
     * @param fileName 建议文件名
     * @param headers 自定义请求头（如：User-Agent, Cookie, Referer）
     */
    fun download(
        activity: Activity,
        url: String,
        fileName: String? = null,
        headers: Map<String, String>? = null
    ) {
        try {
            // 提取常用的 Header 字段，1DM 示例支持这些特定字段的简化传递
            val referer = headers?.get("Referer")
            val userAgent = headers?.get("User-Agent")
            val cookies = headers?.get("Cookie")

            // 调用 1DM 示例工具类
            Util1DM.downloadFile(
                activity = activity,
                url = url,
                referer = referer,
                fileName = fileName,
                userAgent = userAgent,
                cookies = cookies,
                headers = headers, // 也可以传递完整的 Map
                secureUri = false,
                askUserToInstall1DMIfNotInstalled = true // 如果没装，引导用户安装
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}