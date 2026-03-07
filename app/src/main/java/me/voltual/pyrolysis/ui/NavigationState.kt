// Copyright (C) 2025 Voltual
package me.voltual.pyrolysis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * Create a navigation state that persists config changes and process death.
 */

@Composable
fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>
): NavigationState {
    val topLevelRoute = rememberSerializable(
        startRoute, topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer())
    ) {
        mutableStateOf(startRoute)
    }

    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        NavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks
        )
    }
}

class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>
) {
    var topLevelRoute: NavKey by topLevelRoute
    
    val currentRoute: NavKey?
        get() = backStacks[topLevelRoute]?.lastOrNull() 
            ?: backStacks[startRoute]?.lastOrNull()

    val stacksInUse: List<NavKey>
        get() = if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }

    fun resetToStart() {
    // 1. 先切换路由标识，让 Compose 在下一次计算时知道我们要看 startRoute
    topLevelRoute = startRoute
    
    // 2. 遍历堆栈进行清理
    backStacks.forEach { (key, stack) ->
        if (key == startRoute) {
            // 针对首页堆栈：保留第一个（根）页面，移除之上的所有页面
            while (stack.size > 1) {
                stack.removeLastOrNull()
            }
        } else {
            // 针对非首页堆栈：
            // 不要直接 clear()！如果某些侧滑动画或过渡还在引用它，clear 会导致闪崩。
            // 建议：如果它已经是空的就跳过，如果有内容，也保留至少一个，或者等它不可见后再清。
            // 但为了简单且安全，我们可以让它至少保留一个。
            if (stack.isNotEmpty()) {
                while (stack.size > 1) {
                    stack.removeLastOrNull()
                }
                // 注意：这里如果为了彻底释放内存，可以在确保 topLevelRoute 改变后，
                // 延迟清空非活跃栈，或者接受保留一个根节点的开销。
            }
        }
    }
}
}

/**
 * Convert NavigationState into NavEntries.
 */
/**
 * 完全参考官方 Recipe 实现的 Entry 转换逻辑
 * 核心在于返回 SnapshotStateList 以保证 NavDisplay 的响应式更新
 */
@Composable
fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): SnapshotStateList<NavEntry<NavKey>> {

    val decoratedEntries = backStacks.mapValues { (_, stack) ->
        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        )
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = decorators,
            entryProvider = entryProvider
        )
    }

    // 重点：使用 getTopLevelRoutesInUse() 动态计算活跃堆栈
    // 并通过 toMutableStateList() 返回 SnapshotStateList
    return remember(topLevelRoute, startRoute, decoratedEntries) {
        val routesInUse = if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }

        routesInUse
            .flatMap { decoratedEntries[it] ?: emptyList() }
            .toMutableStateList()
    }
}