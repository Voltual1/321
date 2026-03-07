/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.core.ui.icons.Phosphor
import me.voltual.pyrolysis.core.ui.icons.phosphor.Asterisk
import me.voltual.pyrolysis.core.ui.icons.phosphor.FunnelSimple

@Composable
fun SortFilterChip(
    notModified: Boolean,
    fullWidth: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.TopStart,
    ) {
        ActionChip(
            text = stringResource(id = R.string.sort_filter),
            icon = Phosphor.FunnelSimple,
            fullWidth = fullWidth,
            onClick = onClick
        )

        if (!notModified) {
            Icon(
                modifier = Modifier.align(Alignment.TopEnd),
                imageVector = Phosphor.Asterisk,
                contentDescription = stringResource(id = R.string.state_modified),
            )
        }
    }
}
