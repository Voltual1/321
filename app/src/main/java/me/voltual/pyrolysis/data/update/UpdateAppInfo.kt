//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。

package me.voltual.pyrolysis.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 表示服务器返回的需要更新的应用信息
 */
@Serializable
data class UpdateAppInfo(
    val id: Int,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("version_code") val versionCode: Long,
    @SerialName("version_name") val versionName: String,
    @SerialName("app_type") val appType: String,
    @SerialName("app_version_type") val appVersionType: String,
    @SerialName("app_icon") val appIcon: String,
    @SerialName("app_abi") val appAbi: Int,
    @SerialName("app_sdk_min") val appSdkMin: Int,
    @SerialName("old_version_code") val oldVersionCode: Long,
    @SerialName("old_version_name") val oldVersionName: String
)