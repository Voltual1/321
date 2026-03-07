//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import me.voltual.pyrolysis.core.ui.icons.Phosphor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import me.voltual.pyrolysis.core.ui.icons.phosphor.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey        
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.ui.Navigator
import me.voltual.pyrolysis.ui.NavigationState
import me.voltual.pyrolysis.data.DrawerMenuDataStore

sealed class IconSource {
    data class Resource(val resId: Int) : IconSource()
    data class Vector(val imageVector: ImageVector) : IconSource()
    data class Remote(val url: String) : IconSource()
}

// 修改数据类
data class DrawerItem(
    val id: String, 
    val label: String,
    val icon: IconSource, //
    val route: AppDestination
)

@Composable
fun DrawerHeader(modifier: Modifier = Modifier, backgroundUri: String?) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        if (backgroundUri != null) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = "Drawer Header Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun NavigationDrawerItems(
    navigator: Navigator,
    currentTopLevelRoute: NavKey?,           // ← 使用 NavKey 类型
    drawerState: DrawerState,
    scope: CoroutineScope
) {
    val context = LocalContext.current

val allDrawerItems = remember {
    mutableListOf(
        DrawerItem("home", "首页", IconSource.Resource(R.drawable.ic_menu_home), Home),
        DrawerItem("resources", "资源广场", IconSource.Resource(R.drawable.ic_menu_apps), ResourcePlaza(isMyResource = false)),
        DrawerItem("explore", "仓库探索", IconSource.Vector(Phosphor.Compass), Explore),
        DrawerItem("repos_search", "仓库搜索", IconSource.Vector(Phosphor.MagnifyingGlass), SearchPage),
        DrawerItem("prefsrepos", "仓库管理", IconSource.Vector(Phosphor.Graph), PrefsReposPage),
        DrawerItem("community", "交流社区", IconSource.Resource(R.drawable.ic_menu_community), Community),
        DrawerItem("messages", "消息中心", IconSource.Resource(R.drawable.ic_menu_message), MessageCenter),
        DrawerItem("ranking_list", "天梯竞赛", IconSource.Resource(R.drawable.ic_menu_ranking), RankingList),
        DrawerItem("release_app", "发布应用", IconSource.Resource(R.drawable.bg), CreateAppRelease),
        DrawerItem("bot_logs", "日志", IconSource.Resource(R.drawable.work_log), LogViewer),
        DrawerItem("store_manager", "存储管理", IconSource.Resource(R.drawable.appbackuprestore), StoreManager),
        DrawerItem("download", "下载管理", IconSource.Resource(R.drawable.dsdownload), Download),
        DrawerItem("update_settings", "更新设置", IconSource.Resource(R.drawable.asusupdate), UpdateSettings),
        DrawerItem("settings", "主题设置", IconSource.Resource(R.drawable.ic_menu_settings), ThemeCustomize),
        DrawerItem("signin_settings", "签到设置", IconSource.Resource(R.drawable.sign_in), SignInSettings),
        DrawerItem("login", "登录账号", IconSource.Resource(R.drawable.ic_menu_login), Login),
        DrawerItem("logout", "退出登录", IconSource.Resource(R.drawable.ic_menu_logout), Home)
    )
}
    val allItemsMap = remember { allDrawerItems.associateBy { it.id } }

    var orderedItems by remember { mutableStateOf<List<DrawerItem>>(emptyList()) }
    var draggedItem by remember { mutableStateOf<DrawerItem?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var itemHeight by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val savedOrder = DrawerMenuDataStore.loadMenuOrder(context).first()
        orderedItems = if (savedOrder.isEmpty()) {
            allDrawerItems
        } else {
            val ordered = savedOrder.mapNotNull { allItemsMap[it] }
            val newItems = allDrawerItems.filter { it.id !in savedOrder }
            ordered + newItems
        }
    }

    val placeholderIndex by remember(draggedItem, dragOffsetY) {
        derivedStateOf {
            draggedItem?.let {
                val initialIndex = orderedItems.indexOf(it)
                val displacement = (dragOffsetY / itemHeight).toInt()
                (initialIndex + displacement).coerceIn(0, orderedItems.size - 1)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(orderedItems, key = { it.id }) { item ->
                val isBeingDragged = item.id == draggedItem?.id
                val index = orderedItems.indexOf(item)
                val showPlaceholder = placeholderIndex == index && placeholderIndex != orderedItems.indexOf(draggedItem)

                if (showPlaceholder) {
                    if (placeholderIndex!! > orderedItems.indexOf(draggedItem)) {
                        ItemContent(item, currentTopLevelRoute, false, scope, drawerState, navigator)
                        PlaceholderItem(modifier = Modifier.onSizeChanged { itemHeight = it.height })
                    } else {
                        PlaceholderItem(modifier = Modifier.onSizeChanged { itemHeight = it.height })
                        ItemContent(item, currentTopLevelRoute, false, scope, drawerState, navigator)
                    }
                } else {
                    ItemContent(
                        item = item,
                        currentTopLevelRoute = currentTopLevelRoute,
                        isDragged = isBeingDragged,
                        scope = scope,
                        drawerState = drawerState,
                        navigator = navigator,
                        modifier = Modifier
                            .onSizeChanged { itemHeight = it.height }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggedItem = item },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        placeholderIndex?.let { toIndex ->
                                            val fromIndex = orderedItems.indexOf(draggedItem!!)
                                            if (fromIndex != toIndex) {
                                                val newList = orderedItems.toMutableList().apply {
                                                    add(toIndex, removeAt(fromIndex))
                                                }
                                                orderedItems = newList
                                                scope.launch {
                                                    DrawerMenuDataStore.saveMenuOrder(context, newList.map { it.id })
                                                }
                                            }
                                        }
                                        draggedItem = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggedItem = null
                                        dragOffsetY = 0f
                                    }
                                )
                            }
                    )
                }
            }
        }

        draggedItem?.let { item ->
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = dragOffsetY
                        shadowElevation = 8f
                    }
                    .padding(horizontal = 12.dp)
            ) {
                ItemContent(item, currentTopLevelRoute, false, scope, drawerState, navigator)
            }
        }
    }
}

@Composable
private fun ItemContent(
    item: DrawerItem,
    currentTopLevelRoute: NavKey?,
    isDragged: Boolean,
    scope: CoroutineScope,
    drawerState: DrawerState,
    navigator: Navigator,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val isSelected = currentTopLevelRoute == item.route

    NavigationDrawerItem(
        label = { Text(item.label) },
        icon = {
        val iconModifier = Modifier.size(24.dp)
    when (val source = item.icon) {
        is IconSource.Resource -> Icon(painterResource(source.resId), null,modifier = iconModifier)
        is IconSource.Vector -> Icon(source.imageVector, null,modifier = iconModifier)
        is IconSource.Remote -> AsyncImage(
            model = source.url,
            contentDescription = null,
            modifier = iconModifier
        )
    }
},
        selected = isSelected,
        onClick = {
            scope.launch { drawerState.close() }
            when (item.id) {
                "logout" -> {
                    scope.launch {
                        AuthManager.clearCredentials(context)
                        navigator.logoutAndReset()   
                    }
                }
                "login" -> {
                    navigator.navigate(Login)
                }
                else -> {
                    navigator.navigate(item.route)
                }
            }
        },
        modifier = modifier
            .padding(vertical = 4.dp)
            .graphicsLayer { alpha = if (isDragged) 0f else 1f },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            unselectedContainerColor = Color.Transparent
        )
    )
}

@Composable
private fun PlaceholderItem(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {}
}