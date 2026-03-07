/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.components.appsheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.data.content.Preferences
import me.voltual.pyrolysis.core.database.entity.EmbeddedProduct
import me.voltual.pyrolysis.core.database.entity.Release
import me.voltual.pyrolysis.ui.components.InfoChip
import me.voltual.pyrolysis.core.utils.extension.Quadruple
import me.voltual.pyrolysis.core.utils.extension.text.formatSize
import me.voltual.pyrolysis.core.utils.getAndroidVersionName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.text.DateFormat
import java.util.Date

@Composable
fun AppInfoChips(
    list: ImmutableList<String>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.height(54.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(items = list, key = { it }) { text ->
            InfoChip(
                text = text,
            )
        }
    }
}

@Composable
fun EmbeddedProduct.appInfoChips(
    canUpdate: Boolean,
    isInstalled: Boolean,
    installedVersion: String,
    latestRelease: Release?,
    categories: List<String>,
) = listOfNotNull(
    when {
        isInstalled && canUpdate -> "v${installedVersion.trimStart('v')} → v${version.trimStart('v')}"
        isInstalled              -> "v${installedVersion.trimStart('v')}"
        else                     -> "v${version.trimStart('v')}"
    },
    displayRelease?.size?.formatSize().orEmpty(),
    DateFormat.getDateInstance().format(Date(product.updated)),
    *categories.toTypedArray(),
    when {
        Preferences[Preferences.Key.AndroidInsteadOfSDK] && latestRelease != null && latestRelease.minSdkVersion != 0 ->
            "${stringResource(id = R.string.min_android)} ${getAndroidVersionName(latestRelease.minSdkVersion)}"

        latestRelease?.minSdkVersion != 0                                                                             ->
            "${stringResource(id = R.string.min_sdk)} ${latestRelease?.minSdkVersion}"

        else                                                                                                          -> null
    },
    when {
        Preferences[Preferences.Key.AndroidInsteadOfSDK] && latestRelease != null && latestRelease.targetSdkVersion != 0 ->
            "${stringResource(id = R.string.target_android)} ${
                getAndroidVersionName(
                    latestRelease.targetSdkVersion
                )
            }"

        latestRelease?.targetSdkVersion != 0                                                                             ->
            "${stringResource(id = R.string.target_sdk)} ${latestRelease?.targetSdkVersion}"

        else                                                                                                             -> null
    },
    if (product.antiFeatures.isNotEmpty()) stringResource(id = R.string.anti_features)
    else null,
    *product.licenses.toTypedArray(),
).toImmutableList()

@Composable
fun Quadruple<Long, Long, String, Int>.downloadInfoChips(
) = listOfNotNull(
    stringResource(id = R.string.downloads_total_FORMAT, first),
    stringResource(id = R.string.downloads_recent_FORMAT, second),
    stringResource(id = R.string.downloads_top_client_FORMAT, third),
    stringResource(id = R.string.downloads_top_ranking_FORMAT, fourth),
).toImmutableList()