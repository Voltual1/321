/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.a321.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.voltual.a321.data.entity.ColoringState

@Composable
fun ActionButton(
  modifier: Modifier = Modifier,
  text: String,
  coloring: ColoringState = ColoringState.Positive,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  FilledTonalButton(
    modifier = modifier,
    colors =
      ButtonDefaults.filledTonalButtonColors(
        contentColor =
          when (coloring) {
            ColoringState.Positive -> MaterialTheme.colorScheme.onPrimaryContainer
            ColoringState.Negative -> MaterialTheme.colorScheme.onTertiaryContainer
            ColoringState.Neutral -> MaterialTheme.colorScheme.onSecondaryContainer
          },
        containerColor =
          when (coloring) {
            ColoringState.Positive -> MaterialTheme.colorScheme.primaryContainer
            ColoringState.Negative -> MaterialTheme.colorScheme.tertiaryContainer
            ColoringState.Neutral -> MaterialTheme.colorScheme.secondaryContainer
          },
      ),
    enabled = enabled,
    onClick = onClick,
  ) {
    if (icon != null) {
      Icon(imageVector = icon, contentDescription = text)
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
fun OutlinedActionButton(
  modifier: Modifier = Modifier,
  text: String,
  coloring: ColoringState = ColoringState.Positive,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  OutlinedButton(
    modifier = modifier,
    colors =
      ButtonDefaults.outlinedButtonColors(
        contentColor =
          when (coloring) {
            ColoringState.Positive -> MaterialTheme.colorScheme.primary
            ColoringState.Negative -> MaterialTheme.colorScheme.tertiary
            ColoringState.Neutral -> MaterialTheme.colorScheme.secondary
          }
      ),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          when (coloring) {
            ColoringState.Positive -> MaterialTheme.colorScheme.primary
            ColoringState.Negative -> MaterialTheme.colorScheme.tertiary
            ColoringState.Neutral -> MaterialTheme.colorScheme.secondary
          },
      ),
    enabled = enabled,
    onClick = onClick,
  ) {
    if (icon != null) {
      Icon(imageVector = icon, contentDescription = text)
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
fun FlatActionButton(
  modifier: Modifier = Modifier,
  text: String,
  coloring: ColoringState = ColoringState.Positive,
  iconOnSide: Boolean = false,
  icon: ImageVector? = null,
  onClick: () -> Unit,
) {
  TextButton(
    modifier = modifier,
    colors =
      ButtonDefaults.textButtonColors(
        contentColor =
          when (coloring) {
            ColoringState.Positive -> MaterialTheme.colorScheme.primary
            ColoringState.Negative -> MaterialTheme.colorScheme.tertiary
            ColoringState.Neutral -> MaterialTheme.colorScheme.secondary
          }
      ),
    onClick = onClick,
  ) {
    Text(
      modifier = Modifier.padding(horizontal = 4.dp),
      text = text,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.titleSmall,
    )
    if (icon != null) {
      if (iconOnSide) Spacer(modifier = Modifier.weight(1f))
      Icon(imageVector = icon, contentDescription = text)
    }
  }
}

@Composable
fun SecondaryActionButton(
  modifier: Modifier = Modifier,
  icon: ImageVector,
  description: String,
  onClick: () -> Unit,
) {
  OutlinedIconButton(
    modifier = modifier.size(56.dp),
    shape = MaterialTheme.shapes.extraLarge,
    colors =
      IconButtonDefaults.outlinedIconButtonColors(
        contentColor = MaterialTheme.colorScheme.secondary
      ),
    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.secondary),
    onClick = onClick,
  ) {
    Icon(imageVector = icon, contentDescription = description)
  }
}
