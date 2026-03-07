/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.components.prefs

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import me.voltual.pyrolysis.data.entity.LinkRef

@Composable
fun LinkPreference(
    link: LinkRef,
    modifier: Modifier = Modifier,
    index: Int = 1,
    groupSize: Int = 1,
) {
    val context = LocalContext.current

    BasePreference(
        modifier = modifier,
        titleId = link.titleId,
        index = index,
        groupSize = groupSize,
        onClick = {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    link.url.toUri()
                )
            )
        }
    )
}