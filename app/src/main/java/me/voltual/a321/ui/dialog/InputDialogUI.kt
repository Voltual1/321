/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.a321.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.voltual.a321.R
import me.voltual.a321.core.ui.icons.Phosphor
import me.voltual.a321.core.ui.icons.phosphor.X
import me.voltual.a321.core.utils.extension.text.RE_finishChars
import me.voltual.a321.ui.components.DialogNegativeButton
import me.voltual.a321.ui.components.DialogPositiveButton

@Composable
fun IntInputPrefDialogUI(
  title: String,
  initialValue: Int,
  range: IntRange = 0..1000000,
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val textFieldFocusRequester = remember { FocusRequester() }

  var savedValue by remember { mutableIntStateOf(initialValue) }

  LaunchedEffect(Unit) {
    delay(100)
    textFieldFocusRequester.requestFocus()
  }

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
      Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
      Text(
        text = "${range.first}-${range.last}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      TextField(
        modifier =
          Modifier.padding(vertical = 8.dp)
            .shadow(1.dp, MaterialTheme.shapes.large)
            .fillMaxWidth()
            .focusRequester(textFieldFocusRequester),
        value = if (savedValue != -1) savedValue.toString() else "",
        colors = textFieldDialogColors(),
        shape = MaterialTheme.shapes.large,
        singleLine = true,
        placeholder = { Text(text = "${range.first}-${range.last}") },
        onValueChange = { input ->
          savedValue =
            if (input.isNotEmpty()) {
              input.filter { it.isDigit() }.toIntOrNull() ?: initialValue
            } else -1
        },
        keyboardOptions =
          KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Number,
          ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
      )

      Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        DialogNegativeButton(onClick = onDismiss)
        DialogPositiveButton(
          modifier = Modifier.padding(start = 16.dp),
          onClick = { onConfirm(savedValue.coerceIn(range)) },
        )
      }
    }
  }
}

@Composable
fun StringInputPrefDialogUI(
  title: String,
  initialValue: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val textFieldFocusRequester = remember { FocusRequester() }
  var savedValue by remember { mutableStateOf(initialValue) }

  LaunchedEffect(Unit) {
    delay(100)
    textFieldFocusRequester.requestFocus()
  }

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
      Text(text = title, style = MaterialTheme.typography.titleLarge)
      TextField(
        modifier =
          Modifier.padding(vertical = 8.dp)
            .shadow(1.dp, MaterialTheme.shapes.large)
            .fillMaxWidth()
            .focusRequester(textFieldFocusRequester),
        value = savedValue,
        colors = textFieldDialogColors(),
        shape = MaterialTheme.shapes.large,
        singleLine = true,
        onValueChange = { savedValue = it },
        keyboardOptions =
          KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Text,
          ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
      )

      Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        DialogNegativeButton(onClick = onDismiss)
        DialogPositiveButton(
          modifier = Modifier.padding(start = 16.dp),
          onClick = { if (savedValue.isNotEmpty()) onConfirm(savedValue) else onDismiss() },
        )
      }
    }
  }
}

@Composable
fun StringInputDialogUI(
  titleText: String,
  initialValue: String,
  onDismiss: () -> Unit,
  onSave: (String) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val mainFocusRequester = remember { FocusRequester() }
  var savedValue by remember { mutableStateOf(initialValue) }

  fun submit() {
    focusManager.clearFocus()
    onSave(savedValue)
  }

  LaunchedEffect(Unit) {
    delay(100)
    mainFocusRequester.requestFocus()
  }

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
      TextField(
        modifier =
          Modifier.padding(vertical = 8.dp)
            .shadow(1.dp, MaterialTheme.shapes.large)
            .fillMaxWidth()
            .focusRequester(mainFocusRequester),
        value = savedValue,
        colors = textFieldDialogColors(),
        shape = MaterialTheme.shapes.large,
        singleLine = false,
        onValueChange = { if (it.contains(RE_finishChars)) submit() else savedValue = it },
        keyboardOptions =
          KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Text,
          ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
          IconButton(onClick = { savedValue = "" }) {
            Icon(
              imageVector = Phosphor.X,
              contentDescription = stringResource(id = R.string.clear_text),
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        },
      )

      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        DialogNegativeButton(textId = R.string.cancel, onClick = onDismiss)
        Spacer(Modifier.weight(1f))
        DialogPositiveButton(textId = R.string.save, onClick = { submit() })
      }
    }
  }
}

@Composable
fun CreateFileDialogUI(
  title: String = "新建",
  initialValue: String = "",
  onDismiss: () -> Unit,
  onConfirmFolder: (String) -> Unit,
  onConfirmFile: (String) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val textFieldFocusRequester = remember { FocusRequester() }
  var savedValue by remember { mutableStateOf(initialValue) }

  LaunchedEffect(Unit) {
    delay(100)
    textFieldFocusRequester.requestFocus()
  }

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
      Text(text = title, style = MaterialTheme.typography.titleLarge)

      TextField(
        modifier =
          Modifier.padding(vertical = 8.dp)
            .shadow(1.dp, MaterialTheme.shapes.large)
            .fillMaxWidth()
            .focusRequester(textFieldFocusRequester),
        value = savedValue,
        colors = textFieldDialogColors(),
        shape = MaterialTheme.shapes.large,
        singleLine = true,
        onValueChange = { savedValue = it },
        keyboardOptions =
          KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Text,
          ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
      )

      Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DialogNegativeButton(textId = android.R.string.cancel, onClick = onDismiss)

        Spacer(modifier = Modifier.width(8.dp))

        DialogPositiveButton(
          textId = R.string.folder,
          onClick = { if (savedValue.isNotEmpty()) onConfirmFolder(savedValue) },
        )

        Spacer(modifier = Modifier.width(8.dp))

        DialogPositiveButton(textId = R.string.file, onClick = { onConfirmFile(savedValue) })
      }
    }
  }
}

/** 链接与提取码输入对话框 (支持必填提取码控制) */
@Composable
fun LinkAndCodeInputDialogUI(
  title: String = "输入分享信息",
  initialLink: String = "",
  initialCode: String = "",
  codeRequired: Boolean = false,
  onDismiss: () -> Unit,
  onConfirm: (link: String, code: String) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val linkFocusRequester = remember { FocusRequester() }

  var linkValue by remember { mutableStateOf(initialLink) }
  var codeValue by remember { mutableStateOf(initialCode) }

  val canConfirm = linkValue.isNotBlank() && (!codeRequired || codeValue.isNotBlank())

  LaunchedEffect(Unit) {
    delay(100)
    linkFocusRequester.requestFocus()
  }

  Card(
    shape = MaterialTheme.shapes.extraLarge,
    modifier = Modifier.padding(8.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)

      TextField(
        modifier =
          Modifier.fillMaxWidth()
            .shadow(1.dp, MaterialTheme.shapes.large)
            .focusRequester(linkFocusRequester),
        value = linkValue,
        onValueChange = { linkValue = it },
        label = { Text("分享链接") },
        placeholder = { Text("https://……") },
        singleLine = true,
        colors = textFieldDialogColors(),
        shape = MaterialTheme.shapes.large,
        keyboardOptions =
          KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
      )

      TextField(
        modifier = Modifier.fillMaxWidth().shadow(1.dp, MaterialTheme.shapes.large),
        value = codeValue,
        onValueChange = { codeValue = it },
        label = { Text(if (codeRequired) "提取码 (必填)" else "提取码 (可选)") },
        placeholder = { Text("请输入密码") },
        singleLine = true,
        colors = textFieldDialogColors(),
        shape = MaterialTheme.shapes.large,
        keyboardOptions =
          KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Text),
        keyboardActions =
          KeyboardActions(
            onDone = {
              focusManager.clearFocus()
              if (canConfirm) onConfirm(linkValue, codeValue)
            }
          ),
      )

      Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        DialogNegativeButton(onClick = onDismiss)
        DialogPositiveButton(
          modifier = Modifier.padding(start = 16.dp),
          onClick = {
            if (canConfirm) {
              onConfirm(linkValue, codeValue)
            }
          },
        )
      }
    }
  }
}

@Composable
private fun textFieldDialogColors() =
  TextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
  )