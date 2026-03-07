/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SingleChoiceSegmentedButtonRowScope.SegmentedTabButton(
    text: String,
    icon: ImageVector,
    selected: () -> Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SegmentedButton(
        modifier = modifier,
        selected = selected(),
        onClick = onClick,
        border = BorderStroke(0.dp, Color.Transparent),
        colors = SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            inactiveContainerColor = Color.Transparent,
            inactiveContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(imageVector = icon, contentDescription = text)
        }
    ) {
        Text(text = text)
    }
}
