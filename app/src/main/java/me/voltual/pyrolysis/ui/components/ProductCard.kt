/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.core.database.entity.Repository
import me.voltual.pyrolysis.data.entity.ProductItem
import me.voltual.pyrolysis.manager.network.createIconUri

val PRODUCT_CARD_ICON = 48.dp
val PRODUCT_CARD_HEIGHT = 64.dp
val PRODUCT_CAROUSEL_HEIGHT = 164.dp
val PRODUCT_CARD_WIDTH = 220.dp

@Composable
fun ProductCard(
    product: ProductItem,
    repo: Repository? = null,
    onUserClick: (ProductItem) -> Unit = {},
) {
    val imageDataPair by remember(product, repo) {
        mutableStateOf(
            createIconUri(
                product.icon,
                repo?.address,
                repo?.authentication
            )
        )
    }

    ListItem(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clickable { onUserClick(product) }
            .width(IntrinsicSize.Max)
            .widthIn(
                min = PRODUCT_CARD_HEIGHT,
                max = PRODUCT_CARD_WIDTH,
            ),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
        leadingContent = {
            NetworkImage(
                modifier = Modifier.size(PRODUCT_CARD_ICON),
                data = imageDataPair.first,
                fallbackData = imageDataPair.second,
            )
        },
        overlineContent = {
            Text(
                text = product.version,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        headlineContent = {
            Text(
                text = product.name,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
    )
}

/*@Preview
@Composable
fun ProductCardPreview() {
    ProductCard(ProductItem())
}*/