//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.user

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.data.DeviceConfig
import me.voltual.pyrolysis.data.unified.UpdateUserProfileParams
import me.voltual.pyrolysis.core.ui.components.MarkDownText // 导入 MarkDownText
import me.voltual.pyrolysis.core.ui.theme.* import me.voltual.pyrolysis.core.utils.FileUtil
import coil3.compose.rememberAsyncImagePainter
import com.github.dhaval2404.imagepicker.ImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AccountProfileScreen(
    snackbarHostState: SnackbarHostState,
    store: AppStore,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var nickname by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var qqNumber by remember { mutableStateOf("") }
    
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    
    var showImportDialog by remember { mutableStateOf(false) }
    // 新增：Guise 信息弹窗状态
    var showGuiseInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.userDetail, state.currentDevice) {
        state.userDetail?.let {
            nickname = it.displayName ?: ""
            description = it.description ?: ""
            qqNumber = it.bindQq?.toString() ?: "" 
        }
        brand = state.currentDevice.brand
        model = state.currentDevice.model
        alias = state.currentDevice.alias
    }

    LaunchedEffect(store) {
        viewModel.loadUserProfile(store)
    }

    // Guise 信息弹窗
    if (showGuiseInfoDialog) {
        GuiseInfoDialog(onDismiss = { showGuiseInfoDialog = false })
    }

    if (showImportDialog) {
        ImportConfigDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { json ->
                viewModel.importDeviceConfig(json) { success, count ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (success) "成功导入 $count 个机型" else "导入失败，请检查格式"
                        )
                    }
                }
                showImportDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = { BBQSnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            
            AvatarSection(
                currentUrl = state.userDetail?.avatarUrl,
                onImageSelected = { uri ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val path = FileUtil.getRealPathFromURI(context, uri)
                        if (path != null) {
                            viewModel.uploadAvatar(store, File(path)) { _, msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(32.dp))

            ProfileFields(
                store = store,
                nickname = nickname,
                onNicknameChange = { nickname = it },
                qqNumber = qqNumber,
                onQqNumberChange = { qqNumber = it },
                description = description,
                onDescriptionChange = { description = it },
                brand = brand,
                onBrandChange = { brand = it },
                model = model,
                onModelChange = { model = it },
                allDevices = state.allDevices,
                currentDevice = state.currentDevice,
                onDeviceSelect = { viewModel.switchDevice(it) },
                onImportClick = { showImportDialog = true },
                onHelpClick = { showGuiseInfoDialog = true } // 传递点击事件
            )

            Button(
                onClick = {
                    val params = UpdateUserProfileParams(
                        nickname = nickname,
                        description = description,
                        qqNumber = qqNumber,
                        deviceName = model 
                    )
                    val updatedConfig = state.currentDevice.copy(
                        brand = brand, 
                        model = model,
                        alias = alias 
                    )
                    viewModel.updateProfile(store, params, updatedConfig) { _, msg ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("保存全部修改")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFields(
    store: AppStore,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    qqNumber: String,
    onQqNumberChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    brand: String,
    onBrandChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    allDevices: List<DeviceConfig>,
    currentDevice: DeviceConfig,
    onDeviceSelect: (DeviceConfig) -> Unit,
    onImportClick: () -> Unit,
    onHelpClick: () -> Unit // 新增回调
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("账户信息", style = MaterialTheme.typography.titleMedium)
        
        val nicknameLabel = when (store) {
            AppStore.XIAOQU_SPACE -> "修改昵称"
            AppStore.LING_MARKET -> "市场昵称"
            else -> "外显名称"
        }

        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = { Text(nicknameLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (store == AppStore.XIAOQU_SPACE) {
            OutlinedTextField(
                value = qqNumber,
                onValueChange = onQqNumberChange,
                label = { Text("QQ 号码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (store == AppStore.SIENE_SHOP || store == AppStore.LING_MARKET) {
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("个性签名") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设备伪装库（仅本地）", style = MaterialTheme.typography.titleMedium)
                // 添加问号按钮
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "关于 Guise",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            TextButton(onClick = onImportClick) {
                Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("导入 Guise")
            }
        }

        BBQExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = currentDevice.alias.ifEmpty { "未命名配置" },
                onValueChange = {},
                readOnly = true,
                label = { Text("当前选中的模板") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )

            BBQExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                allDevices.forEach { device ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(device.alias, style = MaterialTheme.typography.bodyLarge)
                                Text("${device.brand} ${device.model}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        },
                        onClick = {
                            onDeviceSelect(device)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = brand,
                onValueChange = onBrandChange,
                label = { Text("品牌") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                label = { Text("型号") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
    }
}

@Composable
fun GuiseInfoDialog(onDismiss: () -> Unit) {
    val markdownContent = """
        Guise 项目原作者为 [Houvven](https://github.com/Houvven/)
        仓库地址为 https://github.com/Houvven/Guise/
        **注意！作者已删库，不是我给的地址有误。**
        
        你可以去例如 [酷安](https://www.coolapk.com/) 查找有关于 Guise 的信息和模板。
        
        Guise 是一个安卓 11-13 可用的应用伪装类的机型修改的 Xposed 模块。
        但本项目在此仅兼容 Guise 的模板防止重复造轮子。有关于模板获取见上文。
        
        一个格式正确的 JSON 模板例子是这样的：
        ```json
        [
            {
                "name": "名字",
                "configuration": "{\"brand\":\"品牌名\",\"model\":\"型号\",\"product\":\"corot\",\"device\":\"corot\"}"
            }
        ]
        ```
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于 Guise") },
        shape = AppShapes.medium,
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                MarkDownText(
                    content = markdownContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

@Composable
fun ImportConfigDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从 JSON 导入") },
        shape = AppShapes.medium,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("粘贴 Guise 导出的机型列表 JSON", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("[{...}] 或 {\"configuration\":\"...\"}") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun AvatarSection(
    currentUrl: String?,
    onImageSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { onImageSelected(it) }
        }
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.BottomEnd) {
            val painter = rememberAsyncImagePainter(currentUrl)
            
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                if (!currentUrl.isNullOrEmpty()) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SmallFloatingActionButton(
                onClick = {
                    ImagePicker.with(context as Activity)
                        .cropSquare()
                        .compress(1024)
                        .maxResultSize(512, 512)
                        .createIntent { launcher.launch(it) }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "更换头像", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun CircularProgressIndicator(size: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        strokeWidth = 2.dp
    )
}