// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.community

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.github.dhaval2404.imagepicker.ImagePicker
import kotlinx.coroutines.flow.first
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.data.DeviceNameDataStore
import me.voltual.pyrolysis.ui.*               // 提供 LocalNavigator, ImagePreview 等
import me.voltual.pyrolysis.core.ui.theme.*

private const val MODE_CREATE = "create"
private const val MODE_REFUND = "refund"
private const val MAX_IMAGE_COUNT = 20  // 添加图片数量限制常量

data class Subsection(val id: Int, val name: String)

val SUBSECTIONS = listOf(
    Subsection(4, "手机应用"),
    Subsection(5, "适配应用"),
    Subsection(9, "技巧攻略"),
    Subsection(10, "许愿求助"),
    Subsection(11, "分享生活"),
    Subsection(13, "校园生活"),
    Subsection(14, "学习园地")
)

data class RefundReason(val name: String)

val REFUND_REASONS = listOf(
    RefundReason("无法下载"),
    RefundReason("适配不良"),
    RefundReason("与软件描述不符"),
    RefundReason("资源无法使用"),
    RefundReason("违法违规"),
    RefundReason("适配反馈")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCreateScreen(
    viewModel: PostCreateViewModel,
    onBackClick: () -> Unit,                     // 返回回调，通常为 LocalNavigator.current.goBack()
    mode: String,
    refundAppName: String,
    refundAppId: Long,
    refundVersionId: Long,
    refundPayMoney: Int,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    // 获取 Navigation 3 的导航器
    val navigator = LocalNavigator.current

    val isRefundMode = mode == MODE_REFUND
    val uiState by viewModel.uiState.collectAsState()
    val postStatus by viewModel.postStatus.collectAsState()
    val preferencesState by viewModel.preferencesState.collectAsState()
    val showRestoreDialog by viewModel.showRestoreDialog.collectAsState()

    // 本地 UI 状态
    var bvNumber by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var tempDeviceName by rememberSaveable { mutableStateOf("") }
    var manualImageUrls by rememberSaveable { mutableStateOf("") }
    var selectedRefundReason by rememberSaveable { mutableStateOf(REFUND_REASONS.first().name) }

    val context = LocalContext.current
    val activity = context as? Activity
    val deviceNameDataStore = remember { DeviceNameDataStore(context) }

    // 设置 SnackbarHostState
    LaunchedEffect(Unit) {
        viewModel.setSnackbarHostState(snackbarHostState)
    }

    // 处理发帖状态
    LaunchedEffect(postStatus) {
        when (postStatus) {
            is PostStatus.Success -> {
                onBackClick()
                viewModel.resetPostStatus()
            }
            is PostStatus.Error -> {
                viewModel.resetPostStatus()
            }
            else -> {}
        }
    }

    // 在 Composable 首次进入时，根据模式初始化标题和设备名
    LaunchedEffect(Unit) {
        if (isRefundMode) {
            viewModel.onTitleChange("$refundAppName  【应用退币申请】")
        }
        
        // 从 DataStore 获取当前选中的设备别名
        val currentConfig = deviceNameDataStore.currentConfigFlow.first()
        tempDeviceName = currentConfig.alias
    }

    // 草稿恢复对话框
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onRestoreDialogDismiss() },
            shape = AppShapes.medium,
            title = { Text("恢复草稿") },
            text = { Text("检测到未完成的草稿，是否恢复？") },
            confirmButton = {
                TextButton(onClick = { viewModel.onRestoreDialogConfirm() }) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onRestoreDialogDismiss() }) {
                    Text("取消")
                }
            }
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) { 
            result.data?.data?.let { uri ->
                if (uiState.imageUriToUrlMap.size < MAX_IMAGE_COUNT) {
                    viewModel.uploadImage(uri)
                }
            }
        }
    }    

    if (uiState.showProgressDialog) {
        AlertDialog(
            onDismissRequest = { /* 不允许取消 */ },
            title = { Text("上传中") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(uiState.progressMessage)
                }
            },
            confirmButton = {}
        )
    }

    // 加载状态对话框
    if (postStatus is PostStatus.Loading) {
        AlertDialog(
            onDismissRequest = { /* 不允许取消 */ },
            title = { Text("发帖中") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("正在发布帖子...")
                }
            },
            confirmButton = {}
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val selectedTopicName = if (isRefundMode) {
            selectedRefundReason
        } else {
            SUBSECTIONS.find { it.id == uiState.selectedSubsectionId }?.name ?: ""
        }

        BBQExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    ),
                readOnly = true,
                value = selectedTopicName,
                onValueChange = {},
                label = { Text(if (isRefundMode) "问题类型" else "选择话题") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )
            BBQExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (isRefundMode) {
                    REFUND_REASONS.forEach { reason ->
                        DropdownMenuItem(
                            text = { Text(reason.name) },
                            onClick = {
                                selectedRefundReason = reason.name
                                expanded = false
                            }
                        )
                    }
                } else {
                    SUBSECTIONS.forEach { subsection ->
                        DropdownMenuItem(
                            text = { Text(subsection.name) },
                            onClick = {
                                viewModel.onSubsectionChange(subsection.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.title,
            onValueChange = { if (!isRefundMode) viewModel.onTitleChange(it) },
            label = { Text("标题") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = isRefundMode
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.content,
            onValueChange = { viewModel.onContentChange(it) },
            label = { Text(if (isRefundMode) "详细描述问题 (12字以上)" else "内容") },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            maxLines = 10
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 草稿偏好设置选项
        DraftPreferencesSection(
            autoRestoreDraft = preferencesState.autoRestoreDraft,
            noStoreDraft = preferencesState.noStoreDraft,
            onAutoRestoreChange = { viewModel.setAutoRestoreDraft(it) },
            onNoStoreChange = { viewModel.setNoStoreDraft(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 图片上传区
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                "图片上传 (最多 $MAX_IMAGE_COUNT 张)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.imageUriToUrlMap.values.toList()) { imageUrl ->
                    ImagePreviewItem(
                        imageUrl = imageUrl,
                        onRemoveClick = {
                            val uriToRemove = uiState.imageUriToUrlMap.entries
                                .firstOrNull { it.value == imageUrl }?.key
                            if (uriToRemove != null) {
                                viewModel.removeImage(uriToRemove)
                            }
                        },
                        onImageClick = {
                            // Navigation 3 类型安全导航
                            navigator.navigate(ImagePreview(imageUrl))
                        }
                    )
                }
                if (uiState.imageUriToUrlMap.size < MAX_IMAGE_COUNT) {
                    item {
                        OutlinedButton(onClick = {ImagePicker.with(context as Activity)
                                            .crop()
//                                            .compress(1024)
//                                            .maxResultSize(1080, 1080)
                                            .createIntent { intent ->
                                                imagePickerLauncher.launch(intent)
                                            }}, modifier = Modifier.size(80.dp)) {
                            Icon(Icons.Default.Add, "添加图片")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = bvNumber,
            onValueChange = { bvNumber = it },
            label = { Text("BV号 (可选)") },
            placeholder = { Text("例如: BV1RohqzoEsy") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = tempDeviceName,
            onValueChange = { tempDeviceName = it },
            label = { Text("设备名称 (临时修改)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = manualImageUrls,
            onValueChange = { manualImageUrls = it },
            label = { Text("或手动输入图片链接 (多个用逗号隔开)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (uiState.title.isBlank()) {
                    // 错误提示已在 ViewModel 中处理
                } else if (uiState.content.isBlank()) {
                } else if (isRefundMode && uiState.content.length < 12) {
                } else {
                    val uploadedUrlsList = uiState.imageUrls.split(",").filter { it.isNotBlank() }
                    val manualUrlsList = manualImageUrls.split(",").filter { it.isNotBlank() }
                    val allImageUrls = (uploadedUrlsList + manualUrlsList).distinct().joinToString(",")

                    viewModel.createPost(
                        title = uiState.title,
                        imageUrls = allImageUrls,
                        subsectionId = uiState.selectedSubsectionId,
                        bvNumber = bvNumber,
                        tempDeviceName = tempDeviceName,
                        mode = mode,
                        refundAppId = refundAppId,
                        refundVersionId = refundVersionId,
                        refundPayMoney = refundPayMoney,
                        selectedRefundReason = selectedRefundReason
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = postStatus !is PostStatus.Loading
        ) {
            Text(if (isRefundMode) "提交申请" else "发布帖子")
        }
    }
}

// 草稿偏好设置组件
@Composable
private fun DraftPreferencesSection(
    autoRestoreDraft: Boolean,
    noStoreDraft: Boolean,
    onAutoRestoreChange: (Boolean) -> Unit,
    onNoStoreChange: (Boolean) -> Unit
) {
    BBQCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "草稿设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = autoRestoreDraft,
                    onCheckedChange = onAutoRestoreChange
                )
                Text(
                    "自动恢复草稿（不询问）",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = noStoreDraft,
                    onCheckedChange = onNoStoreChange
                )
                Text(
                    "不存储草稿",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (noStoreDraft) {
                Text(
                    "注意：启用后不会保存任何草稿内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}