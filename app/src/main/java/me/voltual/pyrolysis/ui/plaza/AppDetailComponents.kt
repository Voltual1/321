// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.ui.plaza

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.LingMarketClient
import me.voltual.pyrolysis.data.unified.UnifiedAppDetail
import me.voltual.pyrolysis.data.unified.UnifiedComment
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.ui.community.compose.CommentDialog
import me.voltual.pyrolysis.core.ui.components.LinkifyText
import me.voltual.pyrolysis.core.ui.theme.BBQDropdownMenu
import me.voltual.pyrolysis.core.ui.theme.DownloadSourceDrawer
import me.voltual.pyrolysis.core.utils.formatTimestamp
import kotlinx.coroutines.launch

// --- Composable 组件 ---

@Composable
fun XiaoquSpaceAppInfo(appDetail: UnifiedAppDetail) {
    val raw = appDetail.raw as? me.voltual.pyrolysis.KtorClient.AppDetail
    InfoRow(
        label = "应用类型",
        value = appDetail.type
    )
    InfoRow(
        label = "下载次数",
        value = "${appDetail.downloadCount} 次"
    )
    if (appDetail.size != null) {
        InfoRow(
            label = "安装包大小",
            value = appDetail.size
        )
    }
    InfoRow(
        label = "上传时间",
        value = raw?.create_time ?: "未知"
    )
    InfoRow(
        label = "更新时间",
        value = raw?.update_time ?: "未知"
    )
}

@Composable
fun SineShopAppInfo(appDetail: UnifiedAppDetail) {
    val raw = appDetail.raw as? me.voltual.pyrolysis.SineShopClient.SineShopAppDetail
    val deviceInfo = getDeviceInfo(raw?.app_sdk_min ?: 0)
    InfoRow(
        label = "应用类型",
        value = appDetail.type
    )
    InfoRow(
        label = "版本类型",
        value = raw?.app_version_type ?: "未知"
    )

    // 支持系统信息（包含最低SDK、目标SDK和设备兼容性）
    val supportSystem = buildString {
        append("最低SDK: ${raw?.app_sdk_min ?: "未知"}")
        if (raw?.app_sdk_target != null && raw.app_sdk_target != raw.app_sdk_min) {
            append(" (目标SDK: ${raw.app_sdk_target})")
        }
        append(" • ")
        append(deviceInfo)
    }
    InfoRow(
        label = "支持系统",
        value = supportSystem
    )

    if (appDetail.size != null) {
        InfoRow(
            label = "安装包大小",
            value = appDetail.size
        )
    }
    InfoRow(
        label = "下载次数",
        value = "${appDetail.downloadCount} 次"
    )
    InfoRow(
        label = "应用开发者",
        value = raw?.app_developer ?: "未知"
    )
    InfoRow(
        label = "应用来源",
        value = raw?.app_source ?: "未知"
    )
    InfoRow(
        label = "上传时间",
        value = if (raw?.upload_time != null) formatTimestamp(raw.upload_time) else "未知"
    )
    InfoRow(
        label = "资料时间",
        value = if (raw?.update_time != null) formatTimestamp(raw.update_time) else "未知"
    )

    // 显示应用标签
    if (!raw?.tags.isNullOrEmpty()) {
        InfoRow(
            label = "应用标签",
            value = raw?.tags?.joinToString(", ") { it.name } ?: ""
        )
    }

    // 显示审核状态（如果有审核失败的情况）
    if (raw?.audit_status == 0 && !raw.audit_reason.isNullOrEmpty()) {
        InfoRow(
            label = "审核状态",
            value = raw.audit_reason
        )
    }
}

@Composable
fun LingMarketAppInfo(appDetail: UnifiedAppDetail) {
    val raw = appDetail.raw as? LingMarketClient.LingMarketApp

    // SDK 信息
    val minSdk = raw?.minSdk
    val targetSdk = raw?.targetSdk
    if (minSdk != null && targetSdk != null) {
        InfoRow(
            label = "SDK",
            value = "Min $minSdk / Target $targetSdk"
        )
    } else if (minSdk != null) {
        InfoRow(
            label = "SDK",
            value = "Min $minSdk"
        )
    }

    // 架构信息
    val architectures = raw?.architectures
    val archText = architectures?.joinToString(", ") ?: "未知"
    InfoRow(
        label = "Arch",
        value = archText
    )

    // 下载量
    InfoRow(
        label = "下载量",
        value = "${appDetail.downloadCount ?: 0}"
    )

    // 创建时间
    val createdAt = raw?.createdAt
    createdAt?.let {
        // 尝试格式化日期 (2026-01-14T12:54:00.499Z -> 2026-01-14)
        val formattedDate = try {
            it.substring(0, 10)
        } catch (e: Exception) {
            it
        }
        InfoRow(
            label = "创建于",
            value = formattedDate
        )
    }

    // 包名
    val packageName = raw?.packageName
    InfoRow(
        label = "包名",
        value = packageName ?: "未知"
    )

    // 应用类型
    InfoRow(
        label = "应用类型",
        value = appDetail.type
    )

    // 安装包大小
    if (appDetail.size != null) {
        InfoRow(
            label = "安装包大小",
            value = appDetail.size
        )
    }

    // 支持设备类型
    val supportedDevices = raw?.supportedDevices
    val devicesText = supportedDevices?.joinToString(", ") ?: "未知"
    if (devicesText.isNotEmpty() && devicesText != "未知") {
        InfoRow(
            label = "支持设备",
            value = devicesText
        )
    }

    // 支持的屏幕密度
    val supportedDensities = raw?.supportedDensities
    supportedDensities?.takeIf { it.isNotEmpty() }?.let { densities ->
        InfoRow(
            label = "屏幕密度",
            value = densities.joinToString(", ")
        )
    }

    // 最后更新时间
    val updatedAt = raw?.updatedAt
    updatedAt?.let {
        val formattedDate = try {
            it.substring(0, 10)
        } catch (e: Exception) {
            it
        }
        InfoRow(
            label = "最后更新",
            value = formattedDate
        )
    }
}

// --- 通用 UI 组件 ---

// 新增：信息行组件
@Composable
fun InfoRow(label: String, value: String?) {
    if (!value.isNullOrEmpty() && value != "未知" && value != "") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
    }
}

// 新增：获取设备兼容性信息
@Composable
fun getDeviceInfo(minSdk: Int?): String {
    val deviceSdk = android.os.Build.VERSION.SDK_INT
    return buildString {
        append("当前设备SDK:  $deviceSdk")
        if (minSdk != null && deviceSdk >= minSdk) {
            append(" • 兼容")
        } else {
            append(" • 不兼容")
        }
    }
}

// --- 平台特定的头部信息组件 ---

@Composable
fun XiaoquSpaceAppHeader(
    appDetail: UnifiedAppDetail,
    onImagePreview: (String) -> Unit,
    onDownloadClick: () -> Unit,
    onMoreMenuClick: () -> Unit,
    onShareClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onRefundClick: () -> Unit,
    onDeleteAppClick: () -> Unit
) {
    // 小趣空间特有逻辑
    val raw = appDetail.raw as? me.voltual.pyrolysis.KtorClient.AppDetail
    var showMoreMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = appDetail.iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onImagePreview(appDetail.iconUrl) },
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    appDetail.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("版本: ${appDetail.versionName}", style = MaterialTheme.typography.bodyMedium)
                Text("大小: ${appDetail.size ?: "未知"}", style = MaterialTheme.typography.bodyMedium)
            }

            Box {
                IconButton(
                    onClick = { showMoreMenu = true }
                ) {
                    Icon(Icons.Default.MoreVert, "更多")
                }

                // 下拉菜单直接与按钮关联
                BBQDropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    modifier = Modifier.width(180.dp)
                ) {
                    // 分享选项
                    DropdownMenuItem(
                        text = { Text("分享应用") },
                        onClick = {
                            showMoreMenu = false
                            onShareClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                    )

                    // 更新选项（总是显示）
                    DropdownMenuItem(
                        text = { Text("更新应用") },
                        onClick = {
                            showMoreMenu = false
                            onUpdateClick()
                        }
                    )

                    // 退币选项（仅当应用是付费应用且用户已购买后时显示）
                    if (raw?.is_pay == 1 && raw.is_user_pay == true) {
                        DropdownMenuItem(
                            text = { Text("申请退币") },
                            onClick = {
                                showMoreMenu = false
                                onRefundClick()
                            }
                        )
                    }

                    // 删除选项
                    DropdownMenuItem(
                        text = { Text("删除应用") },
                        onClick = {
                            showMoreMenu = false
                            onDeleteAppClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 显示付费信息（如果是付费应用）
        Button(
            onClick = onDownloadClick, // 这里会触发购买检查
            modifier = Modifier.fillMaxWidth(),
            enabled = true // 总是启用，ViewModel会处理购买逻辑
        ) {
            Icon(Icons.Filled.Download, null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    raw?.is_pay == 1 && raw.pay_money > 0 && raw.is_user_pay != true ->
                        "购买并下载 (${raw.pay_money}硬币)"

                    raw?.is_pay == 1 && raw.is_user_pay == true ->
                        "下载应用 (已购买)"

                    else -> "下载应用"
                }
            )
        }
    }
}

@Composable
fun DefaultAppHeader(
    appDetail: UnifiedAppDetail,
    onImagePreview: (String) -> Unit,
    onDownloadClick: () -> Unit,
    onMoreMenuClick: () -> Unit,
    onShareClick: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = appDetail.iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onImagePreview(appDetail.iconUrl) },
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    appDetail.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("版本: ${appDetail.versionName}", style = MaterialTheme.typography.bodyMedium)
                Text("大小: ${appDetail.size ?: "未知"}", style = MaterialTheme.typography.bodyMedium)
            }

            Box {
                IconButton(
                    onClick = { showMoreMenu = true }
                ) {
                    Icon(Icons.Default.MoreVert, "更多")
                }

                BBQDropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    modifier = Modifier.width(180.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("分享应用") },
                        onClick = {
                            showMoreMenu = false
                            onShareClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onDownloadClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = true
        ) {
            Icon(Icons.Filled.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("下载应用")
        }
    }
}

// --- 平台特定的更新日志组件 ---
/**
 * 更新日志组件 - 使用 Navigation 3 的 Navigator 进行链接导航
 */
@Composable
fun UpdateLogSection(
    appDetail: UnifiedAppDetail,
    navigator: Navigator   // 由父组件传入，用于 LinkifyText 内部导航
) {
    val updateLog = appDetail.updateLog

    if (!updateLog.isNullOrEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "更新日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                // LinkifyText 已移除 navController 参数，内部使用 LocalNavigator
                // 但为了确保可测试性和依赖清晰，这里依然传递 navigator 作为 CompositionLocal 提供者？
                // 实际上 LinkifyText 内部直接使用 LocalNavigator.current，无需传递参数。
                // 所以这里直接调用无参版本。
                LinkifyText(
                    text = updateLog,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    }
}

// --- 平台特定的适配说明组件 (小趣空间) ---
@Composable
fun XiaoquSpaceExplainSection(
    appDetail: UnifiedAppDetail,
    navigator: Navigator   // 由父组件传入
) {
    if (appDetail.store == AppStore.XIAOQU_SPACE) {
        val appExplain = when (val raw = appDetail.raw) {
            is me.voltual.pyrolysis.KtorClient.AppDetail -> raw.app_explain
            else -> null
        }

        if (!appExplain.isNullOrEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "适配说明",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    LinkifyText(
                        text = appExplain,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        }
    }
}

// --- 平台特定的应用介绍组件 ---
@Composable
fun AppDescriptionSection(
    appDetail: UnifiedAppDetail,
    navigator: Navigator   // 由父组件传入
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "应用介绍",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            if (!appDetail.description.isNullOrEmpty()) {
                LinkifyText(
                    text = appDetail.description,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                )
            } else {
                Text("暂无介绍", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// --- 平台特定的应用截图组件 ---
@Composable
fun AppPreviewsSection(
    appDetail: UnifiedAppDetail,
    onImagePreview: (String) -> Unit
) {
    if (!appDetail.previews.isNullOrEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "应用截图",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(appDetail.previews) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImagePreview(url) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

// --- 平台特定的作者信息组件 ---
@Composable
fun AppAuthorSection(
    appDetail: UnifiedAppDetail,
    navigator: Navigator   // 由父组件传入，用于导航到用户详情
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "作者信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            when (appDetail.store) {
                AppStore.SIENE_SHOP -> {
                    // 弦应用商店：同时显示上传者和审核员
                    val raw = appDetail.raw as? me.voltual.pyrolysis.SineShopClient.SineShopAppDetail

                    // 上传者信息
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                val userId = raw?.user?.id
                                if (userId != null) {
                                    // 类型安全导航
                                    navigator.navigate(UserDetail(userId.toLong(), appDetail.store))
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(raw?.user?.userAvatar ?: "https://static.sineshop.xin/images/user_avatar/default_avatar.png")
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                            contentDescription = "上传者头像",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                raw?.user?.displayName ?: "未知上传者",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "上传者",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 审核员信息
                    if (raw?.audit_user != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    val userId = raw.audit_user?.id
                                    if (userId != null) {
                                        navigator.navigate(UserDetail(userId.toLong(), appDetail.store))
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(raw.audit_user?.userAvatar ?: "https://static.sineshop.xin/images/user_avatar/default_avatar.png")
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .build(),
                                contentDescription = "审核员头像",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    raw.audit_user?.displayName ?: "未知审核员",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "审核员",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                AppStore.XIAOQU_SPACE,
                AppStore.SINE_OPEN_MARKET,
                AppStore.LING_MARKET -> {
                    // 这些商店只显示上传者
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable {
                                val userId = appDetail.user.id.toLongOrNull()
                                if (userId != null) {
                                    navigator.navigate(UserDetail(userId, appDetail.store))
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = appDetail.user.avatarUrl,
                            contentDescription = "上传者头像",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(appDetail.user.displayName, style = MaterialTheme.typography.titleMedium)
                    }
                }

                else -> {
                    Text(
                        text = "⚠什么都没有!﹁_﹂",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// --- 评论列表标题组件 ---
@Composable
fun CommentsHeader(appDetail: UnifiedAppDetail) {
    Text(
        "评论 (${appDetail.reviewCount})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

// --- 无评论提示组件 ---
@Composable
fun NoCommentsMessage() {
    Text(
        "暂无评论",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}