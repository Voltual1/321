//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.


package me.voltual.pyrolysis.core.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object FileUtil {
    fun getRealPathFromURI(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                var path: String? = null
                context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        path = cursor.getString(columnIndex)
                    }
                }
                path
            }
            "file" -> uri.path
            else -> null
        }
    }
}