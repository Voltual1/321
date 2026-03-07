/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.components.appsheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.core.ui.utils.addIfElse

@Composable
fun HtmlTextBlock(
    modifier: Modifier = Modifier,
    shortText: String,
    longText: String = "",
    onUriClick: (String) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var isExpanded by rememberSaveable { mutableStateOf(false) }
        val text = remember(isExpanded, shortText, longText) {
            htmlToAnnotatedString(
                if (isExpanded) longText else shortText,
                linkInteractionListener = { link ->
                    if (link is LinkAnnotation.Url) onUriClick(link.url)
                }
            )
        }
        Text(
            text,
            modifier = Modifier
                .animateContentSize()
                .padding(12.dp)
                .addIfElse(
                    isExpanded,
                    { fillMaxWidth() },
                    { align(Alignment.CenterHorizontally) }
                )
        )
        if (longText.isNotEmpty()) {
            FilledTonalButton(onClick = { isExpanded = !isExpanded }) {
                Text(text = stringResource(id = if (isExpanded) R.string.show_less else R.string.show_more))
            }
        }
    }
}
