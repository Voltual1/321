// file: me/voltual/pyrolysis/ui/CompositionLocal.kt
package me.voltual.pyrolysis.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavKey

// 必须显式导入 Navigator 和 NavigationState
import me.voltual.pyrolysis.ui.Navigator
import me.voltual.pyrolysis.ui.NavigationState

/**
 * 当前 Navigator 实例，供任何 Composable 发起类型安全导航。
 */
val LocalNavigator = compositionLocalOf<Navigator> {
    error("No Navigator provided")
}

/**
 * 当前 NavigationState 实例，供需要读取状态的 Composable 使用。
 */
val LocalNavigationState = compositionLocalOf<NavigationState> {
    error("No NavigationState provided")
}

/**
 * 全局 SnackbarHostState，避免层层传递。
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}