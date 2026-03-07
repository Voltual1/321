/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.core.ui.icons.Phosphor
import me.voltual.pyrolysis.core.ui.icons.phosphor.CaretDownUp
import me.voltual.pyrolysis.core.ui.icons.phosphor.CaretUpDown

@Composable
fun ExpandablePrivacyBlock(
    modifier: Modifier = Modifier,
    heading: String? = null,
    preExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(preExpanded) }
    val surfaceColor by animateColorAsState(
        targetValue = if (expanded && heading != null) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest,
        label = "surfaceColor"
    )

    Surface(
        modifier = Modifier.animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        onClick = { expanded = !expanded },
        color = surfaceColor
    ) {
        Column {
            ExpandablePrivacyHeader(heading, expanded)
            AnimatedVisibility(visible = expanded) {
                Column(modifier.padding(bottom = 8.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ExpandableItemsBlock(
    modifier: Modifier = Modifier,
    heading: String? = null,
    icon: ImageVector? = null,
    preExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(preExpanded) }
    val surfaceColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh
        else Color.Transparent,
        label = "surfaceColor"
    )
    val expandedPadding by animateDpAsState(
        targetValue = if (expanded) 2.dp else 0.dp,
        label = "expandedPadding"
    )

    Surface(
        modifier = Modifier
            .animateContentSize()
            .padding(vertical = expandedPadding),
        shape = MaterialTheme.shapes.large,
        onClick = { expanded = !expanded },
        color = surfaceColor
    ) {
        Column(modifier = modifier) {
            ExpandableItemsHeader(heading, icon, expanded)
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.padding(
                        bottom = 16.dp,
                        start = 8.dp,
                        end = 8.dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ExpandablePrivacyHeader(
    heading: String? = null,
    expanded: Boolean = false,
    positive: Boolean = true,
) {
    if (heading != null) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (positive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = heading,
                    style = MaterialTheme.typography.titleSmall,
                )
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = if (expanded) Phosphor.CaretDownUp
                    else Phosphor.CaretUpDown,
                    contentDescription = heading
                )
            }
        }
    }
}

@Composable
fun ExpandableItemsHeader(
    heading: String? = null,
    icon: ImageVector? = null,
    expanded: Boolean = false,
    withIcon: Boolean = true,
) {
    var spacerHeight = 0
    if (heading == null) spacerHeight += 8
    Spacer(modifier = Modifier.requiredHeight(spacerHeight.dp))
    if (heading != null) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = icon,
                    contentDescription = heading
                )
            }
            Text(
                modifier = Modifier.weight(1f),
                text = heading,
                style = MaterialTheme.typography.titleSmall,
            )
            if (withIcon) Icon(
                modifier = Modifier.size(24.dp),
                imageVector = if (expanded) Phosphor.CaretDownUp
                else Phosphor.CaretUpDown,
                contentDescription = heading
            )
        }
    }
}