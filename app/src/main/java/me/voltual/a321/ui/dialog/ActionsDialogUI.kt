/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */

package me.voltual.a321.ui.dialog

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.voltual.a321.R
import me.voltual.a321.data.entity.ColoringState
import me.voltual.a321.ui.components.ActionButton
import me.voltual.a321.ui.components.FlatActionButton

@Composable
fun ActionsDialogUI(
  titleText: String,
  messageText: String,
  primaryText: String,
  primaryIcon: ImageVector? = null,
  primaryAction: (() -> Unit) = {},
  secondaryText: String = "",
  secondaryIcon: ImageVector? = null,
  secondaryAction: (() -> Unit)? = null,
  @StringRes dismissTextId: Int = R.string.cancel,
  onDismiss: () -> Unit,
) {
  val scrollState = rememberScrollState()

  Card(
    shape = MaterialTheme.shapes.extraLarge,
    modifier = Modifier.padding(8.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = titleText, style = MaterialTheme.typography.titleLarge)
      Column(modifier = Modifier.verticalScroll(scrollState).fillMaxWidth().weight(1f, false)) {
        Text(
          modifier = Modifier.fillMaxWidth(),
          text = messageText,
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
        )
      }

      Row(Modifier.fillMaxWidth()) {
        FlatActionButton(text = stringResource(id = dismissTextId), onClick = onDismiss)
        Spacer(Modifier.weight(1f))
        if (secondaryAction != null && secondaryText.isNotEmpty()) {
          ActionButton(
            text = secondaryText,
            icon = secondaryIcon,
            coloring = ColoringState.Negative,
          ) {
            secondaryAction()
            onDismiss()
          }
          Spacer(Modifier.requiredWidth(8.dp))
        }
        ActionButton(text = primaryText, icon = primaryIcon) {
          primaryAction()
          onDismiss()
        }
      }
    }
  }
}
