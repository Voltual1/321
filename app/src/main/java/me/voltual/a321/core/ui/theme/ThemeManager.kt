// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.a321.core.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import me.voltual.a321.BBQApplication

object ThemeManager {
  var isAppDarkTheme by mutableStateOf(false)
  var customColorSet by mutableStateOf<CustomColorSet?>(null)

  fun applyCustomColors(context: Context) {
    customColorSet = ThemeColorStore.loadColors(context)
  }

  /**
   * 应用特定的主题模式并刷新 isAppDarkTheme 状态
   * 0 -> 跟随系统, 1 -> 强制日间模式, 2 -> 强制夜间模式
   */
  fun applyThemeMode(context: Context, mode: Int) {
    val targetNightMode = when (mode) {
      1 -> AppCompatDelegate.MODE_NIGHT_NO
      2 -> AppCompatDelegate.MODE_NIGHT_YES
      else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    AppCompatDelegate.setDefaultNightMode(targetNightMode)
    isAppDarkTheme = when (mode) {
      1 -> false
      2 -> true
      else -> isSystemInDarkTheme(context)
    }
  }

  /**
   * 抽屉快速切换暗色主题：切换 1 (日间) 和 2 (夜间) 模式并进行持久化
   */
  fun toggleTheme() {
    val context = BBQApplication.context
    val currentMode = ThemeColorStore.loadThemeMode(context)
    val newMode = if (currentMode == 2) 1 else 2
    runBlocking {
      ThemeColorStore.saveThemeMode(context, newMode)
    }
    applyThemeMode(context, newMode)
  }

  fun initialize(context: Context) {
    // 确保读取持久化存储的主题模式并应用
    val mode = ThemeColorStore.loadThemeMode(context)
    applyThemeMode(context, mode)

    // 加载自定义颜色
    applyCustomColors(context)
  }

  private fun isSystemInDarkTheme(context: Context): Boolean {
    val currentNightMode =
      context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
  }
}