/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.plaza

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.voltual.pyrolysis.FILTER_CATEGORY_ALL
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.data.content.Preferences
import me.voltual.pyrolysis.data.entity.AndroidVersion
import me.voltual.pyrolysis.data.entity.ColoringState
import me.voltual.pyrolysis.data.entity.toAntiFeature
import me.voltual.pyrolysis.ui.components.ActionButton
import me.voltual.pyrolysis.ui.components.ChipsSwitch
import me.voltual.pyrolysis.ui.components.DeSelectAll
import me.voltual.pyrolysis.ui.components.ExpandableItemsBlock
import me.voltual.pyrolysis.ui.components.OutlinedActionButton
import me.voltual.pyrolysis.ui.components.SelectChip
import me.voltual.pyrolysis.core.ui.icons.Phosphor
import me.voltual.pyrolysis.core.ui.icons.phosphor.ArrowUUpLeft
import me.voltual.pyrolysis.core.ui.icons.phosphor.Check
import me.voltual.pyrolysis.core.ui.icons.phosphor.SortAscending
import me.voltual.pyrolysis.core.ui.icons.phosphor.SortDescending
import me.voltual.pyrolysis.core.utils.extension.koinPyrolysisViewModel
import me.voltual.pyrolysis.core.utils.extension.text.nullIfEmpty
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun SortFilterSheet(
    viewModel: PlazaViewModel = koinPyrolysisViewModel(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val nestedScrollConnection = rememberNestedScrollInteropConnection()
    val sortFilterState by viewModel.sortFilterState.collectAsStateWithLifecycle()

    // 统一使用一套全局/广场页面的 Key，不再根据页面切换
    val sortKey = Preferences.Key.SortOrderExplore
    val sortAscendingKey = Preferences.Key.SortOrderAscendingExplore
    val reposFilterKey = Preferences.Key.ReposFilterExplore
    val categoriesFilterKey = Preferences.Key.CategoriesFilterExplore
    val antifeaturesFilterKey = Preferences.Key.AntifeaturesFilterExplore
    val licensesFilterKey = Preferences.Key.LicensesFilterExplore
    val minSDKFilterKey = Preferences.Key.MinSDKExplore
    val targetSDKFilterKey = Preferences.Key.TargetSDKExplore

    // 状态管理保持不变，但引用的 Key 已简化
    var sortOption by remember(Preferences[sortKey]) { mutableStateOf(Preferences[sortKey]) }
    var sortAscending by remember(Preferences[sortAscendingKey]) { mutableStateOf(Preferences[sortAscendingKey]) }
    val filteredOutRepos = remember(Preferences[reposFilterKey]) {
        mutableStateListOf(*Preferences[reposFilterKey].toTypedArray())
    }
    var filterCategory by remember(Preferences[categoriesFilterKey]) { mutableStateOf(Preferences[categoriesFilterKey]) }
    val filteredAntifeatures = remember(Preferences[antifeaturesFilterKey]) {
        mutableStateListOf(*Preferences[antifeaturesFilterKey].toTypedArray())
    }
    val filteredLicenses = remember(Preferences[licensesFilterKey]) {
        mutableStateListOf(*Preferences[licensesFilterKey].toTypedArray())
    }
    var filterMinSDK by remember(Preferences[minSDKFilterKey]) { mutableStateOf(Preferences[minSDKFilterKey]) }
    var filterTargetSDK by remember(Preferences[targetSDKFilterKey]) { mutableStateOf(Preferences[targetSDKFilterKey]) }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                HorizontalDivider(thickness = 2.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedActionButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.action_reset),
                        icon = Phosphor.ArrowUUpLeft,
                        coloring = ColoringState.Negative,
                    ) {
                        // 重置逻辑：直接使用固定 Key
                        Preferences[sortKey] = sortKey.default.value
                        Preferences[sortAscendingKey] = sortAscendingKey.default.value
                        Preferences[reposFilterKey] = reposFilterKey.default.value
                        Preferences[categoriesFilterKey] = categoriesFilterKey.default.value
                        Preferences[antifeaturesFilterKey] = antifeaturesFilterKey.default.value
                        Preferences[licensesFilterKey] = licensesFilterKey.default.value
                        Preferences[minSDKFilterKey] = minSDKFilterKey.default.value
                        Preferences[targetSDKFilterKey] = targetSDKFilterKey.default.value
                        onDismiss()
                    }
                    ActionButton(
                        text = stringResource(id = R.string.action_apply),
                        icon = Phosphor.Check,
                        modifier = Modifier.weight(1f),
                        coloring = ColoringState.Positive,
                        onClick = {
                            Preferences[sortKey] = sortOption
                            Preferences[sortAscendingKey] = sortAscending
                            Preferences[reposFilterKey] = filteredOutRepos.toSet()
                            Preferences[categoriesFilterKey] = filterCategory
                            Preferences[antifeaturesFilterKey] = filteredAntifeatures.toSet()
                            Preferences[licensesFilterKey] = filteredLicenses.toSet()
                            Preferences[minSDKFilterKey] = filterMinSDK
                            Preferences[targetSDKFilterKey] = filterTargetSDK
                            onDismiss()
                        }
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(nestedScrollConnection)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            // 1. 排序部分
            item {
                ExpandableItemsBlock(
                    heading = stringResource(id = R.string.sorting_order),
                    preExpanded = true,
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sortKey.default.value.values.forEach {
                            SelectChip(
                                text = stringResource(id = it.order.titleResId),
                                checked = it == sortOption,
                                alwaysShowIcon = false,
                            ) { sortOption = it }
                        }
                    }
                    ChipsSwitch(
                        firstTextId = R.string.sort_ascending,
                        firstIcon = Phosphor.SortAscending,
                        secondTextId = R.string.sort_descending,
                        secondIcon = Phosphor.SortDescending,
                        firstSelected = sortAscending,
                        onCheckedChange = { sortAscending = it }
                    )
                }
            }

            // 2. 仓库过滤
            item {
                ExpandableItemsBlock(
                    heading = stringResource(id = R.string.repositories),
                    preExpanded = filteredOutRepos.isNotEmpty(),
                ) {
                    DeSelectAll(
                        sortFilterState.enabledRepos.map { it.id.toString() },
                        filteredOutRepos
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sortFilterState.enabledRepos.sortedBy { it.name }.forEach { repo ->
                            val checked by derivedStateOf { !filteredOutRepos.contains(repo.id.toString()) }
                            SelectChip(text = repo.name, checked = checked) {
                                if (checked) filteredOutRepos.add(repo.id.toString())
                                else filteredOutRepos.remove(repo.id.toString())
                            }
                        }
                    }
                }
            }

            // 3. 分类过滤 (去除了 if 判断，广场页面默认显示)
            item {
                ExpandableItemsBlock(
                    heading = stringResource(id = R.string.categories),
                    preExpanded = filterCategory != FILTER_CATEGORY_ALL,
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val allCategory = Pair(FILTER_CATEGORY_ALL, stringResource(id = R.string.all))
                        val otherCategories = sortFilterState.categories.sortedBy { it.label }.map { it.name to it.label }
                        
                        (listOf(allCategory) + otherCategories).forEach { (id, label) ->
                            SelectChip(
                                text = label,
                                checked = id == filterCategory,
                                alwaysShowIcon = false,
                            ) { filterCategory = id }
                        }
                    }
                }
            }
            item {
                ExpandableItemsBlock(
                    heading = stringResource(id = R.string.min_android),
                    preExpanded = filterMinSDK != AndroidVersion.Unknown,
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AndroidVersion.entries.forEach {
                            SelectChip(
                                text = it.valueString,
                                checked = it == filterMinSDK,
                                alwaysShowIcon = false,
                            ) {
                                filterMinSDK = it
                            }
                        }
                    }
                }
            }
            item {
                ExpandableItemsBlock(
                    heading = stringResource(id = R.string.target_android),
                    preExpanded = filterTargetSDK != AndroidVersion.Unknown,
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AndroidVersion.entries.forEach {
                            SelectChip(
                                text = it.valueString,
                                checked = it == filterTargetSDK,
                                alwaysShowIcon = false,
                            ) {
                                filterTargetSDK = it
                            }
                        }
                    }
                }
            }
            item {
                ExpandableItemsBlock(
                    heading = stringResource(id = R.string.allowed_anti_features),
                    preExpanded = filteredAntifeatures.isNotEmpty(),
                ) {
                    DeSelectAll(
                        sortFilterState.antifeaturePairs.map { it.first },
                        filteredAntifeatures
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sortFilterState.antifeaturePairs.sortedBy {
                            it.second.nullIfEmpty()
                                ?: it.first.toAntiFeature()
                                    ?.let { context.getString(it.titleResId) }
                                ?: it.first
                        }.forEach {
                            val checked by derivedStateOf {
                                !filteredAntifeatures.contains(it.first)
                            }

                            SelectChip(
                                text = it.second.nullIfEmpty()
                                    ?: it.first.toAntiFeature()
                                        ?.let { stringResource(id = it.titleResId) }
                                    ?: it.first,
                                checked = checked
                            ) {
                                if (checked) filteredAntifeatures.add(it.first)
                                else filteredAntifeatures.remove(it.first)
                            }
                        }
                    }
                }
            }
            item {
                ExpandableItemsBlock(
                    heading = stringResource(id = R.string.allowed_licenses),
                    preExpanded = filteredLicenses.isNotEmpty(),
                ) {
                    DeSelectAll(sortFilterState.licenses, filteredLicenses)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sortFilterState.licenses.sorted().forEach {
                            val checked by derivedStateOf {
                                !filteredLicenses.contains(it)
                            }

                            SelectChip(
                                text = it,
                                checked = checked
                            ) {
                                if (checked) filteredLicenses.add(it)
                                else filteredLicenses.remove(it)
                            }
                        }
                    }
                }
            }
        }
    }
}
