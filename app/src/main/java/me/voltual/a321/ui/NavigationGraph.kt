//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321.ui

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
import me.voltual.a321.core.ui.components.IDMTransferDialog
import me.voltual.a321.ui.settings.update.UpdateSettingsScreen
import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.navigation3.ui.NavDisplay
import me.voltual.a321.core.ui.theme.ThemeCustomizeScreen
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import me.voltual.a321.ui.explorer.ExplorerScreen
import androidx.compose.foundation.layout.*
import me.voltual.a321.ui.auth.AuthScreen
import me.voltual.a321.core.ui.animation.*

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
        rememberViewModelStoreNavEntryDecorator<NavKey>()      // 为每个 Entry 提供独立的 ViewModel 存储
    )
    
    val scope = rememberCoroutineScope()

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
                    ExplorerScreen()
                }
                
                is ThemeCustomize -> NavEntry(key) {
                    ThemeCustomizeScreen(modifier = Modifier.fillMaxSize())
                }
                
                is UpdateSettings -> NavEntry(key) {
                    UpdateSettingsScreen(snackbarHostState = snackbarHostState)
                }
                is Login -> NavEntry(key) {
                AuthScreen(
                snackbarHostState = snackbarHostState,
                onLoginSuccess = { 
            scope.launch {
                snackbarHostState.showSnackbar("登录成功")
            }
            onBack() 
        }
                )
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