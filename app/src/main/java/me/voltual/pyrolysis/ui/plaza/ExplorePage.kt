/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.plaza

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.voltual.pyrolysis.FILTER_CATEGORY_ALL
import me.voltual.pyrolysis.FILTER_CATEGORY_FAV
import me.voltual.pyrolysis.LINK_IOD_DLSTATS
import me.voltual.pyrolysis.MainActivity
//import com.machiav3lli.fdroid.NeoApp
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.data.content.Preferences
import me.voltual.pyrolysis.data.entity.DialogKey
import me.voltual.pyrolysis.data.entity.Source
import me.voltual.pyrolysis.data.entity.TopDownloadType
import me.voltual.pyrolysis.data.entity.appCategoryIcon
import me.voltual.pyrolysis.ui.components.CategoriesList
import me.voltual.pyrolysis.ui.components.FilledRoundButton
import me.voltual.pyrolysis.ui.components.ProductsListItem
import me.voltual.pyrolysis.ui.components.RoundButton
import me.voltual.pyrolysis.ui.components.SegmentedTabButton
import me.voltual.pyrolysis.ui.components.SelectChip
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.ui.components.SortFilterChip
import me.voltual.pyrolysis.core.ui.icons.Phosphor
import me.voltual.pyrolysis.core.ui.icons.phosphor.Asterisk
import me.voltual.pyrolysis.core.ui.icons.phosphor.CirclesFour
import me.voltual.pyrolysis.core.ui.icons.phosphor.HeartStraight
import me.voltual.pyrolysis.core.ui.icons.phosphor.Info
import me.voltual.pyrolysis.core.ui.icons.phosphor.ListBullets
import me.voltual.pyrolysis.ui.dialog.BaseDialog
import me.voltual.pyrolysis.ui.dialog.KeyDialogUI
//import com.machiav3lli.fdroid.ui.navigation.NavItem
import me.voltual.pyrolysis.core.utils.extension.android.launchView
import me.voltual.pyrolysis.core.utils.extension.koinPyrolysisViewModel
import me.voltual.pyrolysis.core.utils.extension.sortedLocalized
import me.voltual.pyrolysis.core.utils.onLaunchClick
//import com.machiav3lli.fdroid.viewmodels.ExploreVM
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorePage(
    viewModel: ExploreVM = koinPyrolysisViewModel(),
    plazaViewModel: PlazaViewModel = koinPyrolysisViewModel(),
) {
    val context = LocalContext.current
    val mainActivity = LocalActivity.current as MainActivity
    val scope = rememberCoroutineScope()
    val catsListState = rememberLazyListState()
    val topsListState = rememberLazyListState()
    val navigator = LocalNavigator.current

    val categoryProductsState by viewModel.categoryProductsState.collectAsStateWithLifecycle()
    val topProductsState by viewModel.topProductsState.collectAsStateWithLifecycle()
    val dataState by plazaViewModel.dataState.collectAsStateWithLifecycle()
    val selectedCategory = rememberSaveable {
        mutableStateOf("")
    }
    val exploreTab = rememberSaveable { mutableIntStateOf(0) }

    val openDialog = remember { mutableStateOf(false) }
    val dialogKey: MutableState<DialogKey?> = remember { mutableStateOf(null) }

    val notModifiedSortFilter by remember(categoryProductsState.sortFilter) {
        derivedStateOf {
            Preferences[Preferences.Key.SortOrderExplore] == Preferences.Key.SortOrderExplore.default.value &&
                    Preferences[Preferences.Key.SortOrderAscendingExplore] == Preferences.Key.SortOrderAscendingExplore.default.value &&
                    Preferences[Preferences.Key.ReposFilterExplore] == Preferences.Key.ReposFilterExplore.default.value &&
                    Preferences[Preferences.Key.LicensesFilterExplore] == Preferences.Key.LicensesFilterExplore.default.value &&
                    Preferences[Preferences.Key.AntifeaturesFilterExplore] == Preferences.Key.AntifeaturesFilterExplore.default.value &&
                    Preferences[Preferences.Key.TargetSDKExplore] == Preferences.Key.TargetSDKExplore.default.value &&
                    Preferences[Preferences.Key.MinSDKExplore] == Preferences.Key.MinSDKExplore.default.value
        }
    }

    LaunchedEffect(Unit) {
        Preferences.addPreferencesChangeListener {
            when (it) {
                Preferences.Key.ReposFilterExplore,
                Preferences.Key.CategoriesFilterExplore,
                Preferences.Key.AntifeaturesFilterExplore,
                Preferences.Key.LicensesFilterExplore,
                Preferences.Key.SortOrderExplore,
                Preferences.Key.SortOrderAscendingExplore,
                Preferences.Key.TargetSDKExplore,
                Preferences.Key.MinSDKExplore,
                    -> viewModel.setSortFilter(
                    listOf(
                        Preferences[Preferences.Key.ReposFilterExplore],
                        Preferences[Preferences.Key.CategoriesFilterExplore],
                        Preferences[Preferences.Key.AntifeaturesFilterExplore],
                        Preferences[Preferences.Key.LicensesFilterExplore],
                        Preferences[Preferences.Key.SortOrderExplore],
                        Preferences[Preferences.Key.SortOrderAscendingExplore],
                        Preferences[Preferences.Key.TargetSDKExplore],
                        Preferences[Preferences.Key.MinSDKExplore],
                    ).toString()
                )

                else -> {}
            }
        }
    }

    BackHandler(selectedCategory.value != "") {
        Preferences[Preferences.Key.CategoriesFilterExplore] = ""
        selectedCategory.value = ""
        viewModel.setExploreSource(Source.NONE)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
/*        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .padding(top = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.extraLarge,
                )
                .padding(horizontal = 4.dp),
        ) {
            SegmentedTabButton(
                text = stringResource(id = R.string.categories),
                icon = Phosphor.CirclesFour,
                selected = {
                    exploreTab.intValue == 0
                },
                onClick = {
                    exploreTab.intValue = 0
                }
            )
            if (Preferences[Preferences.Key.DLStatsProvider] != Preferences.DLStatsProvider.None
                && topProductsState.statsNotEmpty
            ) SegmentedTabButton(
                text = stringResource(id = R.string.top_apps),
                icon = Phosphor.Asterisk,
                selected = {
                    exploreTab.intValue == 1
                },
                onClick = {
                    exploreTab.intValue = 1
                },
            )
        }*/
        when (exploreTab.intValue) {
            0 -> {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AnimatedVisibility(selectedCategory.value.isNotEmpty()) {
                        RoundButton(
                            icon = Phosphor.ListBullets,
                            description = stringResource(id = R.string.categories)
                        ) {
                            Preferences[Preferences.Key.CategoriesFilterExplore] = ""
                            selectedCategory.value = ""
                            viewModel.setExploreSource(Source.NONE)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    SortFilterChip(
                        notModified = notModifiedSortFilter,
                        fullWidth = true,
                    ) {
                        navigator.navigate(SortFilterSheet)
                    }
                }
                Column(
                    modifier = Modifier.clipToBounds(),
                ) {
                    CategoriesList(
                        items = listOf(
                            Triple(
                                FILTER_CATEGORY_FAV,
                                stringResource(id = R.string.favorite_applications),
                                Phosphor.HeartStraight
                            ),
                        ) + (categoryProductsState.categories.sortedLocalized { label }
                            .map { Triple(it.name, it.label, it.name.appCategoryIcon) }),
                        selectedKey = selectedCategory,
                    ) {
                        when (it) {
                            FILTER_CATEGORY_FAV -> {
                                Preferences[Preferences.Key.CategoriesFilterExplore] =
                                    FILTER_CATEGORY_ALL
                                selectedCategory.value = FILTER_CATEGORY_FAV
                                viewModel.setExploreSource(Source.FAVORITES)
                            }

                            else                -> {
                                Preferences[Preferences.Key.CategoriesFilterExplore] = it
                                selectedCategory.value = it
                                viewModel.setExploreSource(Source.AVAILABLE)
                            }
                        }
                        scope.launch {
                            catsListState.animateScrollToItem(0)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        state = catsListState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(
                            items = categoryProductsState.items,
                            key = { it.packageName }) { item ->
                            ProductsListItem(
                                item = item,
                                repo = dataState.reposMap[item.repositoryId],
                                isFavorite = dataState.favorites.contains(item.packageName),
                                onUserClick = {
                                navigator.navigate(AppPage(it.packageName))
                                },
                                onFavouriteClick = {
                                    plazaViewModel.setFavorite(
                                        it.packageName,
                                        !dataState.favorites.contains(it.packageName)
                                    )
                                },
                                installed = categoryProductsState.installedMap[item.packageName],
                                onActionClick = {
                                    val installed =
                                        categoryProductsState.installedMap[it.packageName]
                                    val action = {
/*                                        NeoApp.wm.install(
                                            Pair(it.packageName, it.repositoryId)
                                        )*/
                                    }
                                    if (installed != null && installed.launcherActivities.isNotEmpty())
/*                                        context.onLaunchClick(
                                            installed,
                                          //  neoActivity.supportFragmentManager
                                        )*/
                                    else if (Preferences[Preferences.Key.DownloadShowDialog]) {
                                        dialogKey.value = DialogKey.Download(it.name, action)
                                        openDialog.value = true
                                    } else action()
                                }
                            )
                        }
                    }
                }
            }

            1 -> {
                // TODO add modes' action chips
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    state = topsListState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    item {
                        LazyRow(
                            modifier = Modifier.height(54.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(items = TopDownloadType.entries, key = { it.key }) { type ->
                                SelectChip(
                                    text = stringResource(type.displayString),
                                    checked = topProductsState.topAppType == type,
                                    alwaysShowIcon = false,
                                ) {
                                    viewModel.setTopAppsType(type)
                                }
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                                trailingContent = {
                                    FilledRoundButton(
                                        icon = Phosphor.Info,
                                        description = stringResource(R.string.download_stats),
                                    ) {
                                        context.launchView(LINK_IOD_DLSTATS)
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.top_apps_notice),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            )
                        }
                    }
                    items(items = topProductsState.items, key = { it.packageName }) { item ->
                        ProductsListItem(
                            item = item,
                            repo = dataState.reposMap[item.repositoryId],
                            isFavorite = dataState.favorites.contains(item.packageName),
                            onUserClick = {
                              navigator.navigate(AppPage(it.packageName))
                            },
                            onFavouriteClick = {
                                plazaViewModel.setFavorite(
                                    it.packageName,
                                    !dataState.favorites.contains(it.packageName)
                                )
                            },
                            installed = topProductsState.installedMap[item.packageName],
                            onActionClick = {
                                val installed = topProductsState.installedMap[it.packageName]
                                val action = {
/*                                    NeoApp.wm.install(  
                                        Pair(it.packageName, it.repositoryId)
                                    )*/
                                }
                                if (installed != null && installed.launcherActivities.isNotEmpty())
/*                                    context.onLaunchClick(
                                        installed,
                                     //   neoActivity.supportFragmentManager
                                    )*/
                                else if (Preferences[Preferences.Key.DownloadShowDialog]) {
                                    dialogKey.value = DialogKey.Download(it.name, action)
                                    openDialog.value = true
                                } else action()
                            }
                        )
                    }
                }
            }
        }
    }

    if (openDialog.value) {
        BaseDialog(openDialogCustom = openDialog) {
            when (dialogKey.value) {
                is DialogKey.Download -> KeyDialogUI(
                    key = dialogKey.value,
                    openDialog = openDialog,
                    primaryAction = {
                        if (Preferences[Preferences.Key.ActionLockDialog] != Preferences.ActionLock.None)
                            mainActivity.launchLockPrompt {
                                (dialogKey.value as DialogKey.Download).action()
                                openDialog.value = false
                            }
                        else {
                            (dialogKey.value as DialogKey.Download).action()
                            openDialog.value = false
                        }
                    },
                    onDismiss = {
                        dialogKey.value = null
                        openDialog.value = false
                    }
                )

                else                  -> {}
            }
        }
    }
}
