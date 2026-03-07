package me.voltual.pyrolysis.ui

import androidx.navigation3.runtime.NavKey
import androidx.compose.ui.platform.TextToolbar
import android.view.View

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 */
class Navigator(
    val state: NavigationState,
    private val textToolbar: TextToolbar? = null,
    private val hostView: View? = null // 传入原生 View 引用
) {
    private fun forceCleanup() {
        // 尝试常规隐藏
//        textToolbar?.hide()
        
        // 暴力终结原生的 ActionMode (FloatingToolbar)
        // 在 View 层级上，这会强制销毁当前的上下文菜单，不再触发坐标计算
//        hostView?.cancelPendingInputEvents()
        
        // 剥夺焦点：防止某些组件因持有焦点而在销毁瞬间尝试重绘菜单
        hostView?.clearFocus()
    }
    
    fun logoutAndReset() {
        state.resetToStart()
    }        

    fun navigate(route: NavKey) {
        forceCleanup() // 执行全套暴力清理
        
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
            // This is a top level route, just switch to it.
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun goBack() {
        forceCleanup()
        
        val currentStack = state.backStacks[state.topLevelRoute] ?:
            error("Stack for ${state.topLevelRoute} not found")
            // If we're at the base of the current route, go back to the start route stack.
        
        if (currentStack.last() == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}