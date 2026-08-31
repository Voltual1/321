// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321.ui

import android.view.View
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/** Handles navigation events (forward and back) by updating the navigation state. */
class Navigator(
  val navController: NavHostController,
  private val hostView: View? = null, // 传入原生 View 引用
  private val topAppBarController: TopAppBarController? = null,
) {
  private fun forceCleanup() {
    // 剥夺焦点：防止某些view组件因持有焦点而在销毁瞬间尝试重绘菜单
    hostView?.clearFocus()
    // 自动清空 TopAppBar 状态
    topAppBarController?.clear()
  }

  fun logoutAndReset() {
    forceCleanup()
    navController.navigate(Home.route) {
      popUpTo(navController.graph.findStartDestination().id) {
        inclusive = true
      }
      launchSingleTop = true
    }
  }

  fun navigate(route: AppDestination) {
    forceCleanup() // 执行暴力清理

    navController.navigate(route.route) {
      // 顶级路由设置标准的 SingleTop 策略，以便在抽屉切换时能够保存并恢复状态
      if (route == Home || route == ThemeCustomize) {
        popUpTo(navController.graph.findStartDestination().id) {
          saveState = true
        }
        launchSingleTop = true
        restoreState = true
      }
    }
  }

  fun goBack() {
    forceCleanup()
    navController.popBackStack()
  }
}