/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.components.privacy

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.data.entity.ColoringState
import me.voltual.pyrolysis.ui.components.ActionButton
import me.voltual.pyrolysis.ui.components.ExpandablePrivacyBlock
import me.voltual.pyrolysis.core.ui.icons.Phosphor
import me.voltual.pyrolysis.core.ui.icons.phosphor.SlidersHorizontal

@Composable
fun PrivacyCard(
    modifier: Modifier = Modifier,
    heading: String? = null,
    actionText: String = "",
    actionIcon: ImageVector = Phosphor.SlidersHorizontal,
    preExpanded: Boolean = false,
    onAction: () -> Unit = { },
    content: @Composable ColumnScope.() -> Unit,
) {
    ExpandablePrivacyBlock(
        modifier = modifier.padding(horizontal = 8.dp),
        heading = heading,
        preExpanded = preExpanded
    ) {
        content()
        if (actionText.isNotEmpty()) Row(
            modifier = Modifier.padding(
                top = 8.dp,
                start = 8.dp,
                end = 8.dp,
            ),
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                text = actionText,
                icon = actionIcon,
                coloring = ColoringState.Positive,
                onClick = onAction
            )
        }
    }
}