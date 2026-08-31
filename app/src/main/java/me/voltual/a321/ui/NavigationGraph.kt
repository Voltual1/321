// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import me.voltual.a321.core.ui.animation.*
import me.voltual.a321.core.ui.components.IDMTransferDialog
import me.voltual.a321.core.ui.theme.ThemeCustomizeScreen
import me.voltual.a321.ui.auth.AuthScreen
import me.voltual.a321.ui.explorer.ExplorerScreen
import me.voltual.a321.ui.settings.update.UpdateSettingsScreen

@Composable
fun BBQNavDisplay(
  navController: NavHostController,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
) {
  val slideDistance = rememberSlideDistance() // 获取 30dp 对应的像素值
  val scope = rememberCoroutineScope()

  NavHost(
    navController = navController,
    startDestination = Home.route,
    modifier = modifier.fillMaxSize(),
    enterTransition = { materialSharedAxisXIn(forward = true, slideDistance = slideDistance) },
    exitTransition = { materialSharedAxisXOut(forward = true, slideDistance = slideDistance) },
    popEnterTransition = { materialSharedAxisXIn(forward = false, slideDistance = slideDistance) },
    popExitTransition = { materialSharedAxisXOut(forward = false, slideDistance = slideDistance) }
  ) {
    composable(route = Home.route) {
      ExplorerScreen(snackbarHostState = snackbarHostState)
    }

    composable(route = ThemeCustomize.route) {
      ThemeCustomizeScreen(modifier = Modifier.fillMaxSize())
    }

    composable(route = UpdateSettings.route) {
      UpdateSettingsScreen(snackbarHostState = snackbarHostState)
    }

    composable(route = Login.route) {
      AuthScreen(
        snackbarHostState = snackbarHostState,
        onLoginSuccess = {
          scope.launch { snackbarHostState.showSnackbar("登录成功") }
          navController.popBackStack()
        },
      )
    }
  }
}

@Composable
fun DownloadHandler(onBack: () -> Unit) {
  val context = LocalContext.current
  var showInstallDialog by remember { mutableStateOf(false) }

  val idmPackages =
    listOf(
      "idm.internet.download.manager.plus",
      "idm.internet.download.manager",
      "idm.internet.download.manager.adm.lite",
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
      try {
        context.startActivity(targetIntent)
      } catch (_: Exception) {}
      onBack()
    } else {
      showInstallDialog = true
    }
  }

  if (showInstallDialog) {
    IDMTransferDialog(onDismiss = onBack)
  }
}