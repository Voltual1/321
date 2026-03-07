//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。

package me.voltual.pyrolysis.ui.update

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.data.unified.UnifiedAppItem
import me.voltual.pyrolysis.core.utils.getApplicationInfoCompat
import me.voltual.pyrolysis.core.utils.getInstalledPackagesCompat
import me.voltual.pyrolysis.core.utils.isSystemApplication
import me.voltual.pyrolysis.core.utils.versionCodeCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val isLoading: Boolean = false,
        val apps: List<UnifiedAppItem> = emptyList(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadInstalledApps()
    }

    fun refresh() {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        _uiState.value = UiState(isLoading = true, apps = emptyList(), error = null)

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val pm = context.packageManager

                val appList = withContext(Dispatchers.IO) {
                    val allPackages = pm.getInstalledPackagesCompat()

                    allPackages.filter { packageInfo ->
                        !pm.isSystemApplication(packageInfo.packageName)
                    }.mapNotNull { packageInfo ->
                        try {
                            val appInfo = pm.getApplicationInfoCompat(packageInfo.packageName)
                            val appName = appInfo.loadLabel(pm).toString()
                            val packageName = packageInfo.packageName
                            val versionName = packageInfo.versionName ?: "N/A"

                            // 直接获取 Drawable
                            val iconDrawable = try {
                                pm.getApplicationIcon(packageName)
                            } catch (e: PackageManager.NameNotFoundException) {
                                null
                            }

                            val iconUri = iconDrawable?.let { drawableToTempFile(context, it, packageName)?.toURI()?.toString() } ?: ""

                            val uniqueId = "${packageName}_local_${UUID.randomUUID()}"
                            val navigationId = packageName
                            val navigationVersionId = packageInfo.versionCodeCompat

                            UnifiedAppItem(
                                uniqueId = uniqueId,
                                navigationId = navigationId,
                                navigationVersionId = navigationVersionId,
                                store = AppStore.LOCAL,
                                name = appName,
                                iconUrl = iconUri,
                                versionName = versionName
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedBy { it.name }
                }

                _uiState.value = UiState(isLoading = false, apps = appList, error = null)

            } catch (e: Exception) {
                val errorMessage = getApplication<Application>().getString(R.string.update_load_apps_failed, e.message)
                _uiState.value = UiState(isLoading = false, apps = emptyList(), error = errorMessage)
            }
        }
    }

    private fun drawableToTempFile(context: Application, drawable: android.graphics.drawable.Drawable, packageName: String): File? {
        return try {
            val bitmap = drawable.toBitmap()
            val file = File(context.cacheDir, "${packageName}_icon.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}