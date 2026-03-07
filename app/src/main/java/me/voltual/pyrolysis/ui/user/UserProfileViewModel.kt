//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.data.DeviceConfig
import me.voltual.pyrolysis.data.DeviceNameDataStore
import me.voltual.pyrolysis.feature.store.repository.IAppStoreRepository
import me.voltual.pyrolysis.data.unified.UnifiedUserDetail
import me.voltual.pyrolysis.data.unified.UpdateUserProfileParams
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.io.File

@KoinViewModel
class UserProfileViewModel(
    private val repositories: Map<AppStore, IAppStoreRepository>,
    private val deviceNameDataStore: DeviceNameDataStore
) : ViewModel() {

    data class UserProfileUiState(
        val isLoading: Boolean = false,
        val userDetail: UnifiedUserDetail? = null,
        val currentDevice: DeviceConfig = DeviceConfig(),
        val isUploading: Boolean = false,
        val allDevices: List<DeviceConfig> = emptyList(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                deviceNameDataStore.deviceListFlow,
                deviceNameDataStore.currentConfigFlow
            ) { list, current ->
                _uiState.update { it.copy(allDevices = list, currentDevice = current) }
            }.collect()
        }
    }

    fun loadUserProfile(store: AppStore) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val repository = repositories[store] ?: return@launch
            val result = repository.getCurrentUserDetail()
            _uiState.update { it.copy(isLoading = false, userDetail = result.getOrNull()) }
        }
    }

    fun switchDevice(config: DeviceConfig) {
        viewModelScope.launch {
            deviceNameDataStore.updateDeviceList(
                uiState.value.allDevices.map { it.copy(isSelected = it == config) }
            )
        }
    }

    fun importDeviceConfig(jsonStr: String, onResult: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            val count = deviceNameDataStore.importConfigsFromJson(jsonStr)
            onResult(count > 0, count)
        }
    }

    fun updateProfile(
        store: AppStore, 
        params: UpdateUserProfileParams, 
        currentConfig: DeviceConfig, 
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val updatedList = uiState.value.allDevices.map {
                if (it.isSelected) currentConfig.copy(isSelected = true) else it
            }
            deviceNameDataStore.updateDeviceList(updatedList)
            
            val result = repositories[store]?.updateUserProfile(params)
            onResult(result?.isSuccess == true, if (result?.isSuccess == true) "已同步云端并保存本地" else "本地已保存，云端失败")
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun uploadAvatar(store: AppStore, imageFile: File, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            try {
                val repository = repositories[store] ?: return@launch onResult(false, "不支持的平台")
                val result = repository.uploadAvatar(imageFile.readBytes(), imageFile.name)
                if (result.isSuccess) {
                    loadUserProfile(store)
                    onResult(true, "头像上传成功")
                } else {
                    onResult(false, result.exceptionOrNull()?.message ?: "上传失败")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "文件处理失败")
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }
}