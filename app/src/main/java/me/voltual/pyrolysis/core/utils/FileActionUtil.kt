//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.core.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

object FileActionUtil {

    /**
     * 打开文件
     * 自动识别文件类型并调用系统对应的应用打开
     * 如果是APK文件，则执行安装操作
     *
     * @param context 上下文
     * @param file 要打开的文件
     * @throws FileNotFoundException 当文件不存在时抛出
     * @throws ActivityNotFoundException 当没有应用可以打开此文件时抛出
     * @throws Exception 其他异常
     */
    @Throws(Exception::class)
    fun openFile(context: Context, file: File) {
        if (!file.exists()) {
            throw FileNotFoundException("文件不存在: ${file.absolutePath}")
        }

        val intent: Intent
        
        // 获取文件的 MIME 类型
        val mimeType = getMimeType(file)
        
        // 检查是否是APK文件
        val isApkFile = file.extension.equals("apk", ignoreCase = true) || 
                       mimeType == "application/vnd.android.package-archive"
        
        if (isApkFile) {
            // APK文件：使用 VIEW 意图进行安装
            intent = Intent(Intent.ACTION_VIEW)
            // 对于安装APK的特殊处理
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
        } else {
            // 其他文件：使用查看意图
            intent = Intent(Intent.ACTION_VIEW)
        }
        
        val uri: Uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0+ 需要使用 FileProvider
            val authority = "${context.packageName}.provider"
            uri = FileProvider.getUriForFile(context, authority, file)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            uri = Uri.fromFile(file)
        }

        // 设置Intent的数据和类型
        intent.setDataAndType(uri, mimeType)
        
        // 如果是安装APK，设置相关标志
        if (isApkFile) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // Android 8.0+ 需要额外的权限检查
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    throw SecurityException("需要允许安装来自此来源的应用")
                }
            }
            
            // Android 6.0+ 需要允许安装未知来源（已废弃，但对于兼容性保留）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    /**
     * 获取文件的 MIME 类型
     */
    private fun getMimeType(file: File): String {
        val extension = getExtension(file.name)
        if (extension.isNotEmpty()) {
            // 特殊处理APK文件
            if (extension.equals("apk", ignoreCase = true)) {
                return "application/vnd.android.package-archive"
            }
            
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
            if (mime != null) {
                return mime
            }
        }
        return "*/*" // 未知类型
    }

    /**
     * 获取文件扩展名
     */
    private fun getExtension(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0 && dotIndex < fileName.length - 1) {
            fileName.substring(dotIndex + 1)
        } else {
            ""
        }
    }
    
    /**
     * 检查是否有安装APK的权限（针对Android 8.0+）
     */
    fun checkInstallPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true // Android 8.0以下不需要此权限
        }
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(size: Long): String {
        val kb = 1024
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            size >= gb -> String.format(Locale.getDefault(), "%.2f GB", size.toFloat() / gb)
            size >= mb -> String.format(Locale.getDefault(), "%.2f MB", size.toFloat() / mb)
            size >= kb -> String.format(Locale.getDefault(), "%.2f KB", size.toFloat() / kb)
            else -> "$size B"
        }
    }
    
    /**
     * 自定义文件未找到异常
     */
    class FileNotFoundException(message: String) : Exception(message)
}