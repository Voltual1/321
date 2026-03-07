//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）の条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//此段代码的原始版本来源自https://github.com/Droid-ify/client

package me.voltual.pyrolysis.core.utils

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import me.voltual.pyrolysis.core.utils.SdkCheck // 确保导入 SdkCheck
import java.security.MessageDigest

object SdkCheck {
    val isPie: Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
    val isTiramisu: Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
}

val PackageInfo.versionCodeCompat: Long
    get() = if (SdkCheck.isPie) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()

fun PackageManager.isSystemApplication(packageName: String): Boolean = try {
    ((getApplicationInfoCompat(packageName).flags) and ApplicationInfo.FLAG_SYSTEM) != 0
} catch (e: Exception) {
    false
}

fun PackageManager.getApplicationInfoCompat(
    packageName: String
): ApplicationInfo = if (SdkCheck.isTiramisu) {
    getApplicationInfo(
        packageName,
        PackageManager.ApplicationInfoFlags.of(0L)
    )
} else {
    @Suppress("DEPRECATION")
    getApplicationInfo(packageName, 0)
}

@Suppress("DEPRECATION")
private val signaturesFlagCompat: Int
    get() = (if (SdkCheck.isPie) PackageManager.GET_SIGNING_CERTIFICATES else 0) or PackageManager.GET_SIGNATURES

fun PackageManager.getPackageInfoCompat(
    packageName: String,
    signatureFlag: Int = signaturesFlagCompat
): PackageInfo? = try {
    if (SdkCheck.isTiramisu) {
        getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(signatureFlag.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, signatureFlag)
    }
} catch (e: Exception) {
    null
}

fun PackageManager.getInstalledPackagesCompat(
    signatureFlag: Int = signaturesFlagCompat
): List<PackageInfo> = try {
    if (SdkCheck.isTiramisu) {
        getInstalledPackages(PackageManager.PackageInfoFlags.of(signatureFlag.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getInstalledPackages(signatureFlag)
    }
} catch (e: Exception) {
    emptyList() // 返回空列表而不是 null
}