package me.voltual.a321.ui.explorer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareFileSheet(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: (password: String, expiration: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val password = remember { mutableStateOf("") }
    
    // 默认过期时间：2099-12-12
    var selectedDate by remember { mutableStateOf(LocalDate(2099, 12, 12)) }
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "分享文件",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. 提取码输入
            OutlinedTextField(
                value = password.value,
                onValueChange = { password.value = it },
                label = { Text("提取码 (留空为无密码)") },
                placeholder = { Text("请输入4位提取码") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 2. 有效期选择
            OutlinedCard(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("有效期至", style = MaterialTheme.typography.labelSmall)
                        Text(selectedDate.toString(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. 确认按钮
            Button(
                onClick = {
                    // 将 LocalDateTime 转为 ISO 8601 格式字符串
                    val isoString = LocalDateTime(selectedDate, LocalTime(8, 0, 0)).toString() + "+08:00"
                    onConfirm(password.value, isoString)
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建分享链接")
            }
        }
    }

    // MD3 日期选择器弹窗
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val instant = Instant.fromEpochMilliseconds(it)
                        selectedDate = instant.toLocalDateTime(TimeZone.UTC).date
                    }
                    showDatePicker = false
                }) { Text("确定") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

data class ShareUiState(
    val password: MutableState<String> = mutableStateOf(""),
    val expiration: MutableState<LocalDateTime> = mutableStateOf(
        LocalDateTime(2099, 12, 12, 8, 0, 0) // 默认原型中的 2099 年
    )
)