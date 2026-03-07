// Copyright (C) 2025 Voltual
package me.voltual.321.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.NavBackStack
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.*
import androidx.navigation3.scene.DialogSceneStrategy
import me.voltual.321.core.ui.components.IDMTransferDialog
import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.navigation3.ui.NavDisplay
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import me.voltual.321.core.ui.animation.*

@Composable
fun BBQNavDisplay(
    backStack: List<NavKey>,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val mySceneStrategy = remember { DialogSceneStrategy<NavKey>() }
    val slideDistance = rememberSlideDistance() // 获取 30dp 对应的像素值
    
    val decorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(), // 保持 UI 状态（如滚动位置）
        rememberViewModelStoreNavEntryDecorator<NavKey>()      // 核心：为每个 Entry 提供独立的 ViewModel 存储
    )

    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        entryDecorators = decorators, // 传入装饰器
        modifier = modifier.fillMaxSize(),
        sceneStrategy = mySceneStrategy,
        
        //  前进动画：当新页面入栈时触发
        transitionSpec = {
            materialSharedAxisX(
                forward = true, 
                slideDistance = slideDistance
            )
        },

        //  返回动画：当页面出栈（Pop）时触发
        popTransitionSpec = {
            materialSharedAxisX(
                forward = false, 
                slideDistance = slideDistance
            )
        },
        // 使用手动实现的 entryProvider 闭包
        entryProvider = { key ->
            when (key) {
                is Home -> NavEntry(key) {
                }

                // 保底逻辑
                else -> NavEntry(key) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Unknown Key: ${key::class.simpleName}", color = Color.Red)
                    }
                }
            }
        }
    )
}
@Composable
 fun DownloadHandler(onBack: () -> Unit) {
    val context = LocalContext.current
    var showInstallDialog by remember { mutableStateOf(false) }

    val idmPackages = listOf(
        "idm.internet.download.manager.plus",
        "idm.internet.download.manager",
        "idm.internet.download.manager.adm.lite"
    )

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        var targetIntent: Intent? = null

        for (pkg in idmPackages) {
            targetIntent = pm.getLaunchIntentForPackage(pkg)
            if (targetIntent != null) break
        }

        if (targetIntent != null) {
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(targetIntent) } catch (_: Exception) {}
            onBack()
        } else {
            showInstallDialog = true
        }
    }

    if (showInstallDialog) {
        IDMTransferDialog(onDismiss = onBack)
    }
}