// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.payment

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.core.ui.theme.*
import me.voltual.pyrolysis.core.utils.DownloadManager

@Composable
fun PaymentCenterScreen(
    viewModel: PaymentViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Navigation 3 导航器
    val navigator = LocalNavigator.current

    val isLoadingBalance by viewModel.isLoadingBalance.collectAsState()
    val paymentInfo by viewModel.paymentInfo.collectAsState()
    val coinsBalance by viewModel.coinsBalance.collectAsState()
    val paymentStatus by viewModel.paymentStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val downloadUrl by remember(paymentStatus) {
        derivedStateOf {
            if (paymentStatus == PaymentStatus.SUCCESS) viewModel.getDownloadUrl() else null
        }
    }

    val downloadFileName by remember(paymentStatus) {
        derivedStateOf {
            if (paymentStatus == PaymentStatus.SUCCESS) viewModel.getDownloadFileName() else null
        }
    }

    fun startDownload(url: String, fileName: String) {
        val activity = context as? Activity
        activity?.let {
            DownloadManager.download(it, url, fileName, null)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.downloadEvent.collectLatest { event ->
            val activity = context as? Activity
            activity?.let {
                DownloadManager.download(
                    activity = it,
                    url = event.url,
                    fileName = event.fileName
                )

                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "任务已发送至 1DM: ${event.fileName}",
                        actionLabel = "管理下载",
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        // 类型安全导航到下载管理
                        navigator.navigate(Download)
                    }
                }
            }
        }
    }

    // 根据状态渲染 UI
    when (paymentStatus) {
        PaymentStatus.SUCCESS -> {
            PaymentResultDialog(
                success = true,
                onDismiss = {
                    viewModel.resetPaymentStatus()
                    navigator.goBack()
                },
                onDownload = {
                    downloadUrl?.let { url ->
                        downloadFileName?.let { fileName -> startDownload(url, fileName) }
                    }
                },
                showDownloadButton = paymentInfo?.type == PaymentType.APP_PURCHASE && !downloadUrl.isNullOrEmpty(),
                snackbarHostState = snackbarHostState,
                coroutineScope = coroutineScope,
                fileName = downloadFileName,
                viewModel = viewModel,
                onNavigateToDownload = { navigator.navigate(Download) } // 传递导航回调
            )
        }
        PaymentStatus.FAILED -> {
            PaymentResultDialog(
                success = false,
                error = errorMessage,
                onDismiss = { viewModel.resetPaymentStatus() },
                showDownloadButton = false,
                viewModel = viewModel
            )
        }
        else -> {
            PaymentContent(
                paymentInfo = paymentInfo,
                coinsBalance = coinsBalance,
                isLoadingBalance = isLoadingBalance,
                errorMessage = errorMessage,
                onFetchBalance = { viewModel.fetchCoinsBalance() },
                onPay = { amount -> viewModel.executePayment(amount) },
                viewModel = viewModel,
                isPaymentProcessing = paymentStatus == PaymentStatus.PROCESSING,
                modifier = modifier,
                snackbarHostState = snackbarHostState
            )
        }
    }
}

@Composable
fun PaymentContent(
    paymentInfo: PaymentInfo?,
    coinsBalance: Int?,
    isLoadingBalance: Boolean,
    errorMessage: String?,
    onFetchBalance: () -> Unit,
    onPay: (Int) -> Unit,
    viewModel: PaymentViewModel,
    isPaymentProcessing: Boolean,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState
) {
    val coroutineScope = rememberCoroutineScope()
    var amount by remember(paymentInfo) { mutableStateOf(paymentInfo?.price?.toString() ?: "") }
    var postIdInput by remember { mutableStateOf("") }
    val isAdvancedMode = paymentInfo?.type == PaymentType.POST_REWARD && (paymentInfo.postId ?: 0L) == 0L

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isAdvancedMode) {
                BBQCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = postIdInput,
                            onValueChange = { postIdInput = it },
                            label = { Text("输入帖子ID") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            )
                        )
                        Button(
                            onClick = {
                                postIdInput.toLongOrNull()?.let { viewModel.loadPostInfo(it) }
                                    ?: coroutineScope.launch {
                                        snackbarHostState.showSnackbar("请输入有效的帖子ID")
                                    }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("加载帖子") }
                    }
                }
            } else {
                BBQCard(modifier = Modifier.fillMaxWidth()) {
                    paymentInfo?.let { info ->
                        when (info.type) {
                            PaymentType.APP_PURCHASE -> {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = info.iconUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(16.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                info.appName,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "版本: ${info.versionId}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    Text(
                                        info.previewContent,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2
                                    )
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("价格")
                                        Text(
                                            "${info.price} 硬币",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            PaymentType.POST_REWARD -> {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 帖子标题
                            Text(
                                paymentInfo.postTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // 显示预览内容
                            Text(
                                text = paymentInfo.previewContent,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            // 作者信息
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AsyncImage(
                                    model = paymentInfo.authorAvatar,
                                    contentDescription = "作者头像",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(Modifier.width(12.dp))

                                Column {
                                    Text(
                                        paymentInfo.authorName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "发布于 ${paymentInfo.postTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
//                            else -> { Text("支付信息", modifier = Modifier.padding(16.dp)) }
                        }
                    }
                }
            }
            
            // ==================== 支付金额区域 ====================
            if (paymentInfo?.type == PaymentType.POST_REWARD) {
                BBQCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "打赏金额",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // 金额选择器
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(5, 10, 20, 50, 100).forEach { coinAmount ->
                                CoinAmountChip(
                                    amount = coinAmount,
                                    selected = amount == coinAmount.toString(),
                                    onClick = { amount = coinAmount.toString() }
                                )
                            }
                        }

                        // 自定义金额输入
                        OutlinedTextField(
                            value = amount,
                            onValueChange = {
                                if (it.isEmpty() || it.toIntOrNull() != null) {
                                    amount = it
                                }
                            },
                            label = { Text("自定义金额") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            trailingIcon = {
                                Text("硬币", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        )
                    }
                }
            }        

            // 余额区域
            BBQCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "我的硬币",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                        BBQOutlinedButton(
                            onClick = onFetchBalance,
                            enabled = !isLoadingBalance,
                            text = { Text(if (isLoadingBalance) "查询中..." else "刷新余额") }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("当前余额:", modifier = Modifier.weight(1f))
                        if (isLoadingBalance) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                "${coinsBalance ?: "--"} 硬币",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ==================== 支付按钮区域 ====================
        val payAmount = amount.toIntOrNull() ?: 0
        BBQCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = AppShapes.medium
        ) {
            BBQButton(
                onClick = {
                    if (payAmount > 0) {
                        if (coinsBalance != null && payAmount > coinsBalance) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("硬币余额不足，请充值")
                            }
                        } else {
                            onPay(payAmount)
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("请输入有效的支付金额")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = payAmount > 0 && (coinsBalance == null || payAmount <= coinsBalance) && !isPaymentProcessing, // 禁用条件
                contentPadding = PaddingValues(vertical = 16.dp),
                text = {
                    if (isPaymentProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = when {
                                paymentInfo?.type == PaymentType.APP_PURCHASE ->
                                    "确认支付 ${paymentInfo.price} 硬币购买应用"

                                paymentInfo?.type == PaymentType.POST_REWARD ->
                                    "打赏 $payAmount 硬币"

                                else -> "确认支付 $payAmount 硬币"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    }
    BBQSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        }        
    

    errorMessage?.let { msg ->
        LaunchedEffect(msg) { snackbarHostState.showSnackbar(msg) }
    }
}

@Composable
private fun CoinAmountChip(
    amount: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$amount",
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PaymentResultDialog(
    success: Boolean,
    error: String? = null,
    onDismiss: () -> Unit,
    onDownload: (() -> Unit)? = null,
    showDownloadButton: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    coroutineScope: CoroutineScope? = null,
    fileName: String? = null,
    viewModel: PaymentViewModel,
    onNavigateToDownload: (() -> Unit)? = null // 新增回调，用于导航到下载管理
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        BBQCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    if (success) "支付成功！" else "支付失败",
                    style = MaterialTheme.typography.headlineMedium
                )

                if (!success && !error.isNullOrEmpty()) {
                    Text(
                        error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Spacer(Modifier.height(32.dp))

                if (success && showDownloadButton) {
                    BBQButton(
                        onClick = {
                            val url = viewModel.getDownloadUrl()
                            val name = viewModel.getDownloadFileName()
                            if (url != null) {
                                viewModel.startDownload(url, name)
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        text = { Text("下载应用") }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // 如果提供了“管理下载”导航回调，显示按钮
                if (success && onNavigateToDownload != null) {
                    BBQOutlinedButton(
                        onClick = {
                            onNavigateToDownload()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        text = { Text("管理下载") }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                BBQOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    text = { Text(if (success) "完成" else "重试") }
                )
            }
        }
    }
}