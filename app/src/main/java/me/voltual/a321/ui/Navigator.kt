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
import androidx.compose.ui.platform.TextToolbar
import androidx.navigation3.runtime.NavKey

/** Handles navigation events (forward and back) by updating the navigation state. */
class Navigator(
  val state: NavigationState,
  private val textToolbar: TextToolbar? = null,
  private val hostView: View? = null, // 传入原生 View 引用
) {
  private fun forceCleanup() {

    // 剥夺焦点：防止某些view组件因持有焦点而在销毁瞬间尝试重绘菜单
    hostView?.clearFocus()
  }

  fun logoutAndReset() {
    state.resetToStart()
  }

  fun navigate(route: NavKey) {
    forceCleanup() // 执行暴力清理

    if (route in state.backStacks.keys) {
      state.topLevelRoute = route
      // This is a top level route, just switch to it.
    } else {
      state.backStacks[state.topLevelRoute]?.add(route)
    }
  }

  fun goBack() {
    forceCleanup()

    val currentStack =
      state.backStacks[state.topLevelRoute] ?: error("Stack for ${state.topLevelRoute} not found")
    // If we're at the base of the current route, go back to the start route stack.

    if (currentStack.last() == state.topLevelRoute) {
      state.topLevelRoute = state.startRoute
    } else {
      currentStack.removeLastOrNull()
    }
  }
}
