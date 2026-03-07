/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.components.appsheet

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.data.entity.LinkType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkItem(
    modifier: Modifier = Modifier,
    linkType: LinkType,
    onClick: (Uri?) -> Unit = {},
    onLongClick: (Uri?) -> Unit = {},
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = { onClick(linkType.link) },
                onLongClick = { onLongClick(linkType.link) }
            ),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
        leadingContent = {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = linkType.icon,
                contentDescription = linkType.title
            )
        },
        headlineContent = {
            Text(
                text = linkType.title,
                style = MaterialTheme.typography.titleSmall,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        supportingContent = {
            if (linkType.link != null) {
                Text(
                    text = linkType.link.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}
