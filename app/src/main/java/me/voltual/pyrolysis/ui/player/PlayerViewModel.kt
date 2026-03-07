//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。

package me.voltual.pyrolysis.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.api.BiliApiManager
import me.voltual.pyrolysis.data.PlayerDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.Inflater
import org.koin.android.annotation.KoinViewModel

enum class VideoScaleMode { FIT, FILL, ZOOM }

data class PlayerSettings(
    val scaleMode: VideoScaleMode = VideoScaleMode.FIT,
    val danmakuSize: Float = 1.2f
)

@KoinViewModel
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerDataStore = PlayerDataStore(application)

    private val _playerUiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val playerUiState = _playerUiState.asStateFlow()

    private val _settings = MutableStateFlow(PlayerSettings())
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            playerDataStore.settingsFlow.firstOrNull()?.let {
                _settings.value = it
            }
        }
    }

    fun updateScaleMode(scaleMode: VideoScaleMode) {
        viewModelScope.launch {
            val newSettings = _settings.value.copy(scaleMode = scaleMode)
            _settings.value = newSettings
            playerDataStore.saveSettings(newSettings)
        }
    }

    // 更新弹幕字号的方法
    fun updateDanmakuSize(newSize: Float) {
        viewModelScope.launch {
            val newSettings = _settings.value.copy(danmakuSize = newSize)
            _settings.value = newSettings
            playerDataStore.saveSettings(newSettings)
        }
    }

    fun loadVideoData(bvid: String) {
        viewModelScope.launch {
            _playerUiState.value = PlayerUiState.Loading
            try {
                val infoResult = BiliApiManager.instance.getVideoInfo(bvid)
                
                if (infoResult.isFailure) {
                    _playerUiState.value = PlayerUiState.Error("获取视频信息失败: ${infoResult.exceptionOrNull()?.message}")
                    return@launch
                }

                val videoInfo = infoResult.getOrThrow()
                if (videoInfo.code != 0 || videoInfo.data == null) {
                    _playerUiState.value = PlayerUiState.Error("获取视频信息失败: ${videoInfo.message}")
                    return@launch
                }

                val videoData = videoInfo.data
                val page = videoData.pages.firstOrNull()

                if (page == null) {
                    _playerUiState.value = PlayerUiState.Error("未找到视频分页信息")
                    return@launch
                }

                val cid = page.cid
                var playUrl: String? = null
                var danmakuData: ByteArray? = null
                var errorMsg: String? = null

                val playUrlJob = launch {
                    try {
                        val playUrlResult = BiliApiManager.instance.getPlayUrl(bvid, cid)
                        if (playUrlResult.isSuccess) {
                            val playUrlResponse = playUrlResult.getOrThrow()
                            if (playUrlResponse.code == 0) {
                                playUrl = playUrlResponse.data?.durl?.firstOrNull()?.url
                            } else {
                                errorMsg = "获取播放地址失败: ${playUrlResponse.message}"
                            }
                        } else {
                            errorMsg = "获取播放地址网络错误: ${playUrlResult.exceptionOrNull()?.message}"
                        }
                    } catch (e: Exception) { 
                        errorMsg = "获取播放地址异常: ${e.message}" 
                    }
                }

                val danmakuJob = launch {
                    try {
                        val danmakuResult = BiliApiManager.instance.getDanmaku(cid)
                        if (danmakuResult.isSuccess) {
                            danmakuData = decompress(danmakuResult.getOrThrow())
                        }
                        // 弹幕加载失败不是致命错误，静默处理
                    } catch (e: Exception) { 
                        // 弹幕加载失败不是致命错误
                    }
                }

                playUrlJob.join()
                danmakuJob.join()

                if (playUrl != null) {
                    _playerUiState.value = PlayerUiState.Success(
                        title = videoData.title,
                        videoUrl = playUrl!!,
                        danmakuData = danmakuData
                    )
                } else {
                    _playerUiState.value = PlayerUiState.Error(errorMsg ?: "未知错误")
                }
            } catch (e: Exception) { 
                _playerUiState.value = PlayerUiState.Error("网络请求失败: ${e.message}") 
            }
        }
    }

    private fun decompress(data: ByteArray): ByteArray {
        var output: ByteArray
        val decompresser = Inflater(true)
        decompresser.reset()
        decompresser.setInput(data)
        val o = ByteArrayOutputStream(data.size)
        try {
            val buf = ByteArray(2048)
            while (!decompresser.finished()) {
                val i = decompresser.inflate(buf)
                o.write(buf, 0, i)
            }
            output = o.toByteArray()
        } catch (e: Exception) {
            output = data
            e.printStackTrace()
        } finally {
            try { o.close() } catch (e: IOException) { e.printStackTrace() }
        }
        decompresser.end()
        return output
    }
}

// PlayerUiState 密封类
sealed class PlayerUiState {
    object Loading : PlayerUiState()
    data class Success(
        val title: String,
        val videoUrl: String,
        val danmakuData: ByteArray?
    ) : PlayerUiState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Success
            if (title != other.title) return false
            if (videoUrl != other.videoUrl) return false
            if (danmakuData != null) {
                if (other.danmakuData == null) return false
                if (!danmakuData.contentEquals(other.danmakuData)) return false
            } else if (other.danmakuData != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = title.hashCode()
            result = 31 * result + videoUrl.hashCode()
            result = 31 * result + (danmakuData?.contentHashCode() ?: 0)
            return result
        }
    }
    data class Error(val message: String) : PlayerUiState()
}