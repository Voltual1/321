//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.core.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

data class ApkInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int,        
    val targetSdkVersion: Int,    
    val sizeInMb: Double,
    val tempApkFile: File,
    val tempIconFile: File?,
    val tempIconFileUri: Uri?
)

object ApkParser {

    private fun generateUniqueFileName(prefix: String, extension: String): String {
        val timestamp = System.currentTimeMillis()
        val randomSuffix = (100..999).random()
        return "${prefix}_${timestamp}_${randomSuffix}.$extension"
    }

    fun parse(context: Context, apkUri: Uri): ApkInfo? {
        val tempApkFileName = generateUniqueFileName("release", "apk")
        val tempApkFile = uriToTempFile(context, apkUri, tempApkFileName) ?: return null
        val archivePath = tempApkFile.absolutePath

        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_META_DATA
            val packageInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(archivePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(archivePath, flags)
            }
            val appInfo = packageInfo?.applicationInfo
            if (appInfo == null) {
                tempApkFile.delete()
                return null
            }

            appInfo.sourceDir = archivePath
            appInfo.publicSourceDir = archivePath

            val appName = appInfo.loadLabel(pm).toString()
            val packageName = packageInfo.packageName
            val versionName = packageInfo.versionName ?: "N/A"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            
            val minSdkVersion = packageInfo.applicationInfo?.minSdkVersion ?: 0
            val targetSdkVersion = packageInfo.applicationInfo?.targetSdkVersion ?: 0
            val iconDrawable = appInfo.loadIcon(pm)
            val tempIconFileName = generateUniqueFileName("icon", "png")
            val tempIconFile = drawableToTempFile(context, iconDrawable, tempIconFileName)
            val tempIconFileUri = tempIconFile?.toUri()

            val sizeInBytes = tempApkFile.length()
            val sizeInMb = (sizeInBytes / 1024.0 / 1024.0 * 100).roundToInt() / 100.0

            return ApkInfo(
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                minSdkVersion = minSdkVersion,     
                targetSdkVersion = targetSdkVersion,
                sizeInMb = sizeInMb,
                tempApkFile = tempApkFile,
                tempIconFile = tempIconFile,
                tempIconFileUri = tempIconFileUri
            )
        } catch (e: Exception) {
            e.printStackTrace()
            tempApkFile.delete()
            return null
        }
    }
    
    private fun uriToTempFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.cacheDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024) // 4KB buffer
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawableToTempFile(context: Context, drawable: Drawable?, fileName: String): File? {
        if (drawable == null) return null
        return try {
            val bitmap = if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }

            val file = File(context.cacheDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}