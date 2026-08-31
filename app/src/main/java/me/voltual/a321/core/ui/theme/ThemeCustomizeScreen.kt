// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.voltual.a321.restartMainActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCustomizeScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val initialColors = remember { ThemeColorStore.loadColors(context) }
  var lightColors by remember { mutableStateOf(initialColors.lightSet) }
  var darkColors by remember { mutableStateOf(initialColors.darkSet) }

  var showSavedMessage by remember { mutableStateOf(false) }
  var showResetDialog by remember { mutableStateOf(false) }

  var dpi by remember { mutableStateOf(ThemeColorStore.loadDpi(context)) }
  var fontSize by remember { mutableStateOf(ThemeColorStore.loadFontSize(context)) }

  // 是否启用自定义 DPI 的状态
  var customDpiEnabled by remember { mutableStateOf(ThemeColorStore.loadCustomDpiEnabled(context)) }

  val roundScreenPaddings = remember { ThemeColorStore.loadRoundScreenPaddings(context) }
  var roundScreenEnabled by remember { mutableStateOf(roundScreenPaddings.enabled) }
  var roundLeft by remember { mutableStateOf(roundScreenPaddings.left) }
  var roundTop by remember { mutableStateOf(roundScreenPaddings.top) }
  var roundRight by remember { mutableStateOf(roundScreenPaddings.right) }
  var roundBottom by remember { mutableStateOf(roundScreenPaddings.bottom) }

  // 持久化的主题模式 (0: 跟随系统, 1: 日间, 2: 夜间)
  var appThemeMode by remember { mutableStateOf(ThemeColorStore.loadThemeMode(context)) }
  var themeDropdownExpanded by remember { mutableStateOf(false) }

  // 翻译状态
  var translate by remember { mutableStateOf(false) }

  var selectedTab by remember { mutableStateOf(0) }

  LaunchedEffect(showSavedMessage) {
    if (showSavedMessage) {
      delay(2000)
      showSavedMessage = false
    }
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text("恢复默认主题") },
      text = { Text("确定要恢复所有颜色和设置吗？此操作不可撤销。") },
      confirmButton = {
        Button(
          onClick = {
            scope.launch {
              // 重置所有颜色和显示设置
              lightColors = ThemeColorStore.DEFAULT_COLORS.lightSet
              darkColors = ThemeColorStore.DEFAULT_COLORS.darkSet
              dpi = 1.0f
              fontSize = 1.0f
              customDpiEnabled = false // 重置为不启用自定义 DPI
              appThemeMode = 0 // 重置为跟随系统
            }
            showResetDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
          Text("恢复")
        }
      },
      dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("取消") } },
    )
  }

  Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
    Column(modifier = Modifier.fillMaxSize()) {
      if (showSavedMessage) {
        Text(
          text = "主题已保存！正在应用...",
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          textAlign = TextAlign.Center,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically, // 垂直居中对齐
      ) {
        IconButton(onClick = { showResetDialog = true }) { Icon(Icons.Filled.Refresh, "恢复默认设置") }

        // 翻译按钮
        IconButton(onClick = { translate = !translate }) { Icon(Icons.Filled.Language, "翻译") }
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
      ) {
        item {
          Text(
            text = "显示设置",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
        item {
          // 自定义下拉菜单选择应用主题模式
          val themeOptions = listOf("跟随系统", "日间模式", "夜间模式")
          BBQExposedDropdownMenuBox(
            expanded = themeDropdownExpanded,
            onExpandedChange = { themeDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
          ) {
            OutlinedTextField(
              value = themeOptions.getOrElse(appThemeMode) { "跟随系统" },
              onValueChange = {},
              readOnly = true,
              label = { Text("应用主题") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded) },
              modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
            )
            BBQExposedDropdownMenu(
              expanded = themeDropdownExpanded,
              onDismissRequest = { themeDropdownExpanded = false }
            ) {
              themeOptions.forEachIndexed { index, option ->
                DropdownMenuItem(
                  text = { Text(option) },
                  onClick = {
                    appThemeMode = index
                    themeDropdownExpanded = false
                  }
                )
              }
            }
          }
        }
        item {
          SwitchWithText(
            text = "启用圆屏适配",
            checked = roundScreenEnabled,
            onCheckedChange = { roundScreenEnabled = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
        item {
          OutlinedTextField(
            value = roundLeft.toString(),
            onValueChange = { roundLeft = it.toFloatOrNull() ?: roundLeft },
            label = { Text("左内边距 (dp)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            enabled = roundScreenEnabled,
          )
        }
        item {
          OutlinedTextField(
            value = roundTop.toString(),
            onValueChange = { roundTop = it.toFloatOrNull() ?: roundTop },
            label = { Text("上内边距 (dp)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            enabled = roundScreenEnabled,
          )
        }
        item {
          OutlinedTextField(
            value = roundRight.toString(),
            onValueChange = { roundRight = it.toFloatOrNull() ?: roundRight },
            label = { Text("右内边距 (dp)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            enabled = roundScreenEnabled,
          )
        }
        item {
          OutlinedTextField(
            value = roundBottom.toString(),
            onValueChange = { roundBottom = it.toFloatOrNull() ?: roundBottom },
            label = { Text("下内边距 (dp)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            enabled = roundScreenEnabled,
          )
        }
        item {
          SwitchWithText(
            text = "启用自定义屏幕密度和字体大小",
            checked = customDpiEnabled,
            onCheckedChange = { customDpiEnabled = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
        item {
          OutlinedTextField(
            value = dpi.toString(),
            onValueChange = { dpi = it.toFloatOrNull() ?: dpi },
            label = { Text("屏幕密度 (DPI 缩放)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            enabled = customDpiEnabled, // 只有在启用自定义 DPI 时才可编辑
          )
        }
        item {
          OutlinedTextField(
            value = fontSize.toString(),
            onValueChange = { fontSize = it.toFloatOrNull() ?: fontSize },
            label = { Text("字体大小 (倍数)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            enabled = customDpiEnabled, // 只有在启用自定义 DPI 时才可编辑
          )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

        item {
          // 使用 PrimaryTabRow 替代弃用的 TabRow
          PrimaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.padding(horizontal = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
          ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("日间模式") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("夜间模式") })
          }
        }

        when (selectedTab) {
          0 -> { // 日间模式
            items(lightColors.toList(), key = { "light_" + it.first }) { (name, color) ->
              Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ColorEditItem(
                  colorName = name,
                  currentColor = color,
                  onColorChange = { newColor ->
                    lightColors = lightColors.copyWith(name, newColor)
                  },
                  translate = translate, // 传递 translate 状态
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
              }
            }
          }
          1 -> { // 夜间模式
            items(darkColors.toList(), key = { "dark_" + it.first }) { (name, color) ->
              Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ColorEditItem(
                  colorName = name,
                  currentColor = color,
                  onColorChange = { newColor -> darkColors = darkColors.copyWith(name, newColor) },
                  translate = translate, // 传递 translate 状态
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
              }
            }
          }
        }
      }
    }

    ExtendedFloatingActionButton(
      onClick = {
        saveThemeAndRestart(
          context = context,
          colors = CustomColorSet(lightColors, darkColors),
          dpi = dpi,
          fontScale = fontSize,
          customDpiEnabled = customDpiEnabled,
          roundScreenEnabled = roundScreenEnabled,
          roundLeft = roundLeft,
          roundTop = roundTop,
          roundRight = roundRight,
          roundBottom = roundBottom,
          appThemeMode = appThemeMode,
        )
        showSavedMessage = true
      },
      icon = { Icon(Icons.Filled.Save, "保存") },
      text = { Text("保存并应用") },
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
    )
  }
}

// 局部函数：保存主题并重启

@Suppress("DEPRECATION")
private fun saveThemeAndRestart(
  context: Context,
  colors: CustomColorSet,
  dpi: Float,
  fontScale: Float,
  customDpiEnabled: Boolean,
  roundScreenEnabled: Boolean,
  roundLeft: Float,
  roundTop: Float,
  roundRight: Float,
  roundBottom: Float,
  appThemeMode: Int,
) {
  val scope =
    (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope
      ?: kotlinx.coroutines.MainScope()
  scope.launch {
    val oldDpi = ThemeColorStore.loadDpi(context)
    val oldFontScale = ThemeColorStore.loadFontSize(context)
    val oldCustomDpiEnabled = ThemeColorStore.loadCustomDpiEnabled(context)
    val oldThemeMode = ThemeColorStore.loadThemeMode(context)

    ThemeColorStore.saveColors(context, colors)
    ThemeColorStore.saveDpi(context, dpi)
    ThemeColorStore.saveFontSize(context, fontScale)
    ThemeColorStore.saveCustomDpiEnabled(context, customDpiEnabled) // 保存是否启用自定义 DPI 的状态
    ThemeColorStore.saveRoundScreenPaddings(
      context,
      roundScreenEnabled,
      roundLeft,
      roundTop,
      roundRight,
      roundBottom,
    )
    ThemeColorStore.saveThemeMode(context, appThemeMode) // 持久化存储主题模式

    withContext(Dispatchers.Main) {
      ThemeManager.applyCustomColors(context) // 应用颜色自定义
      ThemeManager.applyThemeMode(context, appThemeMode) // 即时且平滑地应用主题模式

      // 仅当 DPI、字体大小、自定义 DPI 启用状态或主题模式改变时才重启 Activity
      if (oldDpi != dpi || oldFontScale != fontScale || oldCustomDpiEnabled != customDpiEnabled || oldThemeMode != appThemeMode) {
        (context as? Activity)?.let {
          if (customDpiEnabled) {
            val resources = it.resources
            val configuration = Configuration(resources.configuration)
            val metrics = resources.displayMetrics
            val newDensityDpi = (dpi * DisplayMetrics.DENSITY_DEFAULT).toInt()
            configuration.densityDpi = newDensityDpi
            configuration.fontScale = fontScale
            metrics.densityDpi = newDensityDpi
            resources.updateConfiguration(configuration, metrics)
          }
        }
        delay(300)
        restartMainActivity(context) // 重启以重建界面样式
      }
    }
  }
}

@Composable
fun ColorEditItem(
  colorName: String,
  currentColor: Color,
  onColorChange: (Color) -> Unit,
  translate: Boolean, // 接收 translate 参数
) {
  var hexValue by remember(currentColor) { mutableStateOf(currentColor.toHex()) }
  var showColorPicker by remember { mutableStateOf(false) }

  if (showColorPicker) {
    HsvColorPickerDialog(
      initialColor = currentColor,
      onColorSelected = { newColor ->
        onColorChange(newColor)
        hexValue = newColor.toHex()
        showColorPicker = false
      },
      onDismiss = { showColorPicker = false },
    )
  }

  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.size(48.dp)
          .background(currentColor)
          .border(1.dp, MaterialTheme.colorScheme.outline)
    )
    Text(
      text = if (translate) colorNameTranslations[colorName] ?: colorName else colorName, // 使用翻译
      modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
      style = MaterialTheme.typography.bodyLarge,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = hexValue,
        onValueChange = {
          val newHex = it.take(6)
          hexValue = newHex
          if (newHex.isValidHex()) {
            onColorChange(Color(android.graphics.Color.parseColor("#$newHex")))
          }
        },
        label = { Text("HEX") },
        modifier = Modifier.width(100.dp),
        maxLines = 1,
        singleLine = true,
      )
      Spacer(modifier = Modifier.width(8.dp))
      IconButton(onClick = { showColorPicker = true }) {
        Icon(imageVector = Icons.Filled.ColorLens, contentDescription = "选择颜色")
      }
    }
  }
}

@Composable
fun HsvColorPickerDialog(
  initialColor: Color,
  onColorSelected: (Color) -> Unit,
  onDismiss: () -> Unit,
) {
  val hsvArray = FloatArray(3)
  android.graphics.Color.colorToHSV(initialColor.toArgb(), hsvArray)

  var hue by remember { mutableStateOf(hsvArray[0]) }
  var saturation by remember { mutableStateOf(hsvArray[1]) }
  var value by remember { mutableStateOf(hsvArray[2]) }

  val currentColor = remember(hue, saturation, value) { Color.hsv(hue, saturation, value) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("选择颜色") },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(64.dp)
                .background(currentColor)
                .border(
                  1.dp,
                  MaterialTheme.colorScheme.outline,
                  androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                )
          )
          Column {
            Text("HEX: ${currentColor.toHex()}")
            Text(
              "RGB: ${currentColor.red.to255()}, ${currentColor.green.to255()}, ${currentColor.blue.to255()}"
            )
          }
        }

        Text("色相 (0-360°)", style = MaterialTheme.typography.labelMedium)
        Slider(
          value = hue,
          onValueChange = { hue = it },
          valueRange = 0f..360f,
          modifier = Modifier.fillMaxWidth(),
        )

        Text("饱和度 (0-100%)", style = MaterialTheme.typography.labelMedium)
        Slider(
          value = saturation,
          onValueChange = { saturation = it },
          valueRange = 0f..1f,
          modifier = Modifier.fillMaxWidth(),
        )

        Text("亮度 (0-100%)", style = MaterialTheme.typography.labelMedium)
        Slider(
          value = value,
          onValueChange = { value = it },
          valueRange = 0f..1f,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onColorSelected(currentColor) },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
      ) {
        Text("确认")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    shape = AppShapes.medium, // 应用 AppShapes.medium
  )
}

fun Color.toHex(): String {
  return String.format("%06X", this.toArgb() and 0xFFFFFF)
}

fun String.isValidHex(): Boolean = this.length == 6 && this.matches(Regex("[0-9A-Fa-f]{6}"))

fun Float.to255(): Int = (this * 255).roundToInt()

// 颜色名称翻译
val colorNameTranslations =
  mapOf(
    "primary" to "主要颜色",
    "onPrimary" to "主要文字颜色",
    "primaryContainer" to "主要容器颜色",
    "onPrimaryContainer" to "主要容器文字颜色",
    "secondary" to "次要颜色",
    "onSecondary" to "次要文字颜色",
    "secondaryContainer" to "次要容器颜色",
    "onSecondaryContainer" to "次要容器文字颜色",
    "surface" to "表面颜色",
    "onSurface" to "表面文字颜色",
    "surfaceVariant" to "表面变体颜色",
    "onSurfaceVariant" to "表面变体文字颜色",
    "outline" to "轮廓颜色",
    "error" to "错误颜色",
    "onError" to "错误文字颜色",
    "background" to "背景颜色",
    "onBackground" to "背景文字颜色",
    "messageLikeBg" to "点赞消息背景色",
    "messageCommentBg" to "评论消息背景色",
    "messageDefaultBg" to "普通消息背景色",
    "billingIncome" to "收入颜色",
    "billingExpense" to "支出颜色",
  )