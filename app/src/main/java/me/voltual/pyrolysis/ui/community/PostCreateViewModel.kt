//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.ui.community

import android.app.Application
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import io.ktor.client.call.*
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.data.PostDraftDataStore
import me.voltual.pyrolysis.core.database.PostDraftRepository
import me.voltual.pyrolysis.core.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import me.voltual.pyrolysis.core.proto.UserCredentialsKt
import io.ktor.client.plugins.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.http.content.*
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.flow.Flow 
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class PostCreateViewModel(application: Application) : AndroidViewModel(application) {

    private val draftRepository = PostDraftRepository()
    private val draftDataStore = PostDraftDataStore(application)

    private val _uiState = MutableStateFlow(PostCreateUiState())
    val uiState: StateFlow<PostCreateUiState> = _uiState.asStateFlow()

    // 偏好设置状态
    private val _preferencesState = MutableStateFlow(DraftPreferencesState())
    val preferencesState: StateFlow<DraftPreferencesState> = _preferencesState.asStateFlow()

    private val _postStatus = MutableStateFlow<PostStatus>(PostStatus.Idle)
    val postStatus: StateFlow<PostStatus> = _postStatus.asStateFlow()

    // 对话框状态
    private val _showRestoreDialog = MutableStateFlow(false)
    val showRestoreDialog: StateFlow<Boolean> = _showRestoreDialog.asStateFlow()

    // Snackbar 回调
    private var _snackbarHostState: SnackbarHostState? = null
    fun setSnackbarHostState(snackbarHostState: SnackbarHostState) {
        _snackbarHostState = snackbarHostState
    }

    init {
        viewModelScope.launch {
            // 加载偏好设置
            draftDataStore.preferencesFlow.first().let { preferences ->
                _preferencesState.value = DraftPreferencesState(
                    autoRestoreDraft = preferences.autoRestoreDraft,
                    noStoreDraft = preferences.noStoreDraft
                )
            }

            // 检查是否有草稿并决定是否显示对话框
            draftRepository.draftFlow.first().let { draft ->
                if (draft != null && (draft.title.isNotBlank() || draft.content.isNotBlank() || draft.imageUrls.isNotBlank())) {
                    if (_preferencesState.value.autoRestoreDraft) {
                        // 自动恢复草稿
                        restoreDraft(draft)
                    } else {
                        // 显示恢复对话框
                        _showRestoreDialog.value = true
                    }
                }
            }
        }
        observeAndAutoSave()
    }

    private fun restoreDraft(draft: PostDraftRepository.DraftDto) {
        _uiState.update {
            it.copy(
                title = draft.title,
                content = draft.content,
                imageUrls = draft.imageUrls,
                // imageUriToUrlMap 不再从草稿中恢复，它是在上传图片时构建的
                selectedSubsectionId = draft.subsectionId
            )
        }
    }


    // 处理对话框操作
    fun onRestoreDialogConfirm() {
        viewModelScope.launch {
            draftRepository.draftFlow.first()?.let { draft ->
                restoreDraft(draft)
            }
            _showRestoreDialog.value = false
        }
    }

    fun onRestoreDialogDismiss() {
        _showRestoreDialog.value = false
    }

    // 更新偏好设置
    fun setAutoRestoreDraft(enabled: Boolean) {
        viewModelScope.launch {
            draftDataStore.setAutoRestoreDraft(enabled)
            _preferencesState.value = _preferencesState.value.copy(autoRestoreDraft = enabled)
        }
    }

    fun setNoStoreDraft(enabled: Boolean) {
        viewModelScope.launch {
            draftDataStore.setNoStoreDraft(enabled)
            _preferencesState.value = _preferencesState.value.copy(noStoreDraft = enabled)

            // 如果不存储草稿，立即清除当前草稿
            if (enabled) {
                draftRepository.clearDraft()
            }
        }
    }

    // --- Event Handlers ---
    fun onTitleChange(newTitle: String) {
        if (!_preferencesState.value.noStoreDraft) {
            _uiState.update { it.copy(title = newTitle) }
        }
    }

    fun onContentChange(newContent: String) {
        if (!_preferencesState.value.noStoreDraft) {
            _uiState.update { it.copy(content = newContent) }
        }
    }

    fun onSubsectionChange(newId: Int) {
        if (!_preferencesState.value.noStoreDraft) {
            _uiState.update { it.copy(selectedSubsectionId = newId) }
        }
    }

    fun onImageUrlsChange(urls: String) {
        if (!_preferencesState.value.noStoreDraft) {
            _uiState.update { it.copy(imageUrls = urls) }
        }
    }

    fun uploadImage(uri: Uri) {
        if (_preferencesState.value.noStoreDraft) {
            showSnackbar("草稿存储已禁用")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(showProgressDialog = true, progressMessage = "上传图片中...") }

            val realPath = withContext(Dispatchers.IO) {
                // 显式转换为 Application 类型
                val context: Application = getApplication()
                FileUtil.getRealPathFromURI(context, uri)
            }

            if (realPath == null) {
                _uiState.update { it.copy(showProgressDialog = false) }
                showSnackbar("无法获取图片路径")
                return@launch
            }

            try {
                val file = File(realPath)
                val bytes = file.readBytes()
                val uploadResult = uploadImageKtor(bytes, file.name)

                if (uploadResult.isSuccess) {
                    uploadResult.getOrNull()?.let { imageUrl ->
                        _uiState.update { currentState ->
                            // 构建新的 imageUriToUrlMap
                            val newUrlMap = currentState.imageUriToUrlMap + (uri to imageUrl)
                            val newUrls = newUrlMap.values.joinToString(",")
                            currentState.copy(
                                imageUrls = newUrls,
                                imageUriToUrlMap = newUrlMap,
                                showProgressDialog = false
                            )
                        }
                        showSnackbar("图片上传成功")
                    }
                } else {
                    showSnackbar("上传失败: ${uploadResult.exceptionOrNull()?.message}")
                    _uiState.update { it.copy(showProgressDialog = false) }
                }
            } catch (e: Exception) {
                showSnackbar("上传错误: ${e.message}")
                _uiState.update { it.copy(showProgressDialog = false) }
            } finally {
                _uiState.update { it.copy(showProgressDialog = false) }
            }
        }
    }
    

    private suspend fun uploadImageKtor(fileBytes: ByteArray, fileName: String): Result<String> {
    return withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = KtorClient.uploadHttpClient.post("api.php") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", fileBytes, Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            })
                        }
                    )
                )
            }

            // 使用数据类解析响应
            val responseBody: KtorClient.UploadResponse = response.body()
            
            // 使用 downurl 字段，与 AppReleaseViewModel 保持一致
            if (responseBody.code == 0 && !responseBody.downurl.isNullOrBlank()) {
                Result.success(responseBody.downurl)
            } else {
                Result.failure(Throwable("上传失败: ${responseBody.msg}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

    fun removeImage(uri: Uri) {
        if (!_preferencesState.value.noStoreDraft) {
            _uiState.update { currentState ->
                val newUrlMap = currentState.imageUriToUrlMap - uri
                val newUrls = newUrlMap.values.joinToString(",")
                currentState.copy(
                    imageUrls = newUrls,
                    imageUriToUrlMap = newUrlMap
                )
            }
        }
    }

    fun createPost(
        title: String,
        imageUrls: String,
        subsectionId: Int,
        bvNumber: String,
        tempDeviceName: String,
        mode: String,
        refundAppId: Long = 0L,
        refundVersionId: Long = 0L,
        refundPayMoney: Int = 0,
        selectedRefundReason: String = ""
    ) {
        viewModelScope.launch {
            _postStatus.value = PostStatus.Loading

            try {
                // 显式转换为 Application 类型
                val context: Application = getApplication()
                // 显式指定类型
                val userCredentialsFlow = AuthManager.getCredentials(context)
                val userCredentials = userCredentialsFlow.first()
                if (userCredentials.userId == 0L || userCredentials.token.isEmpty()) {
                    _postStatus.value = PostStatus.Error("请先登录")
                    showSnackbar("请先登录")
                    return@launch
                }

                val token = userCredentials.token

                val finalContent = if (mode == "refund") {
                    val videoPart = if (bvNumber.isNotBlank()) "【视频：$bvNumber】" else ""
                    """
                    ${_uiState.value.content}


                    问题类型:$selectedRefundReason
                    系统定制商：${android.os.Build.BRAND.uppercase()}
                    设备型号：${android.os.Build.MODEL}
                    退还金额:$refundPayMoney
                    资源id:$refundAppId
                    资源版本:$refundVersionId 機型：$tempDeviceName｜$videoPart
                    """.trimIndent()
                } else {
                    val videoPart = if (bvNumber.isNotBlank()) "【视频：$bvNumber】" else ""
                    "${_uiState.value.content} 機型：$tempDeviceName｜$videoPart"
                }

                val finalSubsectionId = if (mode == "refund") 21 else subsectionId

                val createPostResult = KtorClient.ApiServiceImpl.createPost(
                    appid = 1,
                    token = token,
                    title = title,
                    content = finalContent,
                    sectionId = finalSubsectionId,
                    imageUrls = if (imageUrls.isNotBlank()) imageUrls else null
                )

                if (createPostResult.isSuccess) {
                    _postStatus.value = PostStatus.Success
                    // 发帖成功后清除草稿
                    if (!_preferencesState.value.noStoreDraft) {
                        draftRepository.clearDraft()
                    }
                    showSnackbar("发帖成功")
                } else {
                    val errorMsg = createPostResult.exceptionOrNull()?.message ?: "发帖失败"
                    _postStatus.value = PostStatus.Error(errorMsg)
                    showSnackbar("发帖失败: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "网络错误: ${e.message}"
                _postStatus.value = PostStatus.Error(errorMsg)
                showSnackbar(errorMsg)
            }
        }
    }

    fun clearDraft() {
        viewModelScope.launch {
            draftRepository.clearDraft()
        }
    }

    fun resetPostStatus() {
        _postStatus.value = PostStatus.Idle
    }

    @OptIn(FlowPreview::class)
    private fun observeAndAutoSave() {
        _uiState
            .debounce(1000)
            .onEach { state ->
                // 如果不存储草稿，跳过自动保存
                if (_preferencesState.value.noStoreDraft) {
                    return@onEach
                }

                if (state.title.isNotBlank() || state.content.isNotBlank() || state.imageUrls.isNotBlank()) {
                    draftRepository.saveDraft(
                        PostDraftRepository.DraftDto(
                            title = state.title,
                            content = state.content,
                            imageUris = emptyList(), // 修改
                            imageUrls = state.imageUrls,
                            subsectionId = state.selectedSubsectionId
                        )
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // 新增：显示 Snackbar 的方法
    private fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarHostState?.showSnackbar(message)
        }
    }
}

data class PostCreateUiState(
    val title: String = "",
    val content: String = "",
    val selectedSubsectionId: Int = 11,
    val imageUrls: String = "",
    // 将 imageUriToUrlMap 移到 UI 状态中
    val imageUriToUrlMap: Map<Uri, String> = emptyMap(),
    val showProgressDialog: Boolean = false,
    val progressMessage: String = ""
)

// 偏好设置状态
data class DraftPreferencesState(
    val autoRestoreDraft: Boolean = false,
    val noStoreDraft: Boolean = false
)

sealed class PostStatus {
    object Idle : PostStatus()
    object Loading : PostStatus()
    object Success : PostStatus()
    data class Error(val message: String) : PostStatus()
}