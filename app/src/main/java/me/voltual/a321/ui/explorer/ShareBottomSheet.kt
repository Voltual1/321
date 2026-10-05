@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.voltual.a321.ui.explorer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import me.voltual.a321.core.ui.theme.SwitchWithText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareFileSheet(
  displayTitle: String,
  supportsCustomPassword: Boolean = true,
  onDismiss: () -> Unit,
  onConfirm: (password: String, expiration: String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState()
  val password = remember { mutableStateOf("") }
  var isPermanent by remember { mutableStateOf(false) }

  var selectedDate by remember { mutableStateOf(LocalDate(2099, 12, 12)) }
  var showDatePicker by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(text = "创建分享链接", style = MaterialTheme.typography.headlineSmall)
      Text(
        text = displayTitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (supportsCustomPassword) {
        OutlinedTextField(
          value = password.value,
          onValueChange = { password.value = it },
          label = { Text("提取码 (留空为无密码)") },
          placeholder = { Text("请输入4位提取码") },
          leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
      } else {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
          SwitchWithText(
            text = "永久有效",
            checked = isPermanent,
            onCheckedChange = { isPermanent = it }
          )
        }
      }

      OutlinedCard(
        onClick = { if (!isPermanent) showDatePicker = true },
        enabled = !isPermanent,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.DateRange,
            contentDescription = null,
            tint = if (isPermanent) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              "有效期至",
              style = MaterialTheme.typography.labelSmall,
              color = if (isPermanent) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              if (isPermanent) "永久有效" else selectedDate.toString(),
              style = MaterialTheme.typography.bodyLarge,
              color = if (isPermanent) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Button(
        onClick = {
          val isoString = if (isPermanent) {
            "PERMANENT"
          } else {
            val time = LocalTime(8, 0, 0, 0)
            val dateTime = LocalDateTime(selectedDate, time)

            "${dateTime.year}-" +
              "${dateTime.month.number.toString().padStart(2, '0')}-" +
              "${dateTime.day.toString().padStart(2, '0')}T" +
              "${dateTime.hour.toString().padStart(2, '0')}:" +
              "${dateTime.minute.toString().padStart(2, '0')}:" +
              "${dateTime.second.toString().padStart(2, '0')}." +
              "${(dateTime.nanosecond / 1_000_000).toString().padStart(3, '0')}" +
              "+08:00"
          }

          onConfirm(password.value, isoString)
        },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
      ) {
        Icon(Icons.Default.Share, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("立即创建")
      }
    }
  }

  if (showDatePicker) {
    val datePickerState =
      rememberDatePickerState(
        initialSelectedDateMillis =
          selectedDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
      )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let {
              val instant = Instant.fromEpochMilliseconds(it)
              selectedDate = instant.toLocalDateTime(TimeZone.UTC).date
            }
            showDatePicker = false
          }
        ) {
          Text("确定")
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }
}