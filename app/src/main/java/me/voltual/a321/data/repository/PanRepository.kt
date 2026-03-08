//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321.data.repository

import android.content.Context
import kotlinx.coroutines.flow.first
import me.voltual.a321.AuthManager
import me.voltual.a321.KtorClient
import me.voltual.a321.data.UpdateInfo

class PanRepository(private val context: Context) {

    private val apiService = KtorClient.ApiServiceImpl

    /**
     * 获取文件列表：自动从 DataStore 获取最新的 Token
     */
    suspend fun getFiles(page: Int = 1) = runCatching {
        // 从 DataStore 获取加密保存的凭证
        val credentials = AuthManager.getCredentials(context).first()
        val token = credentials.token
        
        if (token.isEmpty()) throw Exception("Login required")
        
        val result = apiService.getFileList(token, page)
        result.getOrThrow()
    }

    /**
     * 兼容原有的更新检查逻辑
     */
    suspend fun getLatestRelease(url: String): Result<UpdateInfo> {
        return apiService.getLatestRelease(url)
    }
}