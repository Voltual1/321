//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.plaza

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.SineShopClient
import me.voltual.pyrolysis.feature.store.repository.IAppStoreRepository
import me.voltual.pyrolysis.feature.store.repository.SineOpenMarketRepository
import me.voltual.pyrolysis.feature.store.repository.XiaoQuRepository
import me.voltual.pyrolysis.data.unified.UnifiedAppReleaseParams
import me.voltual.pyrolysis.core.utils.ApkInfo
import me.voltual.pyrolysis.core.utils.ApkParser
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.koin.android.annotation.KoinViewModel
import androidx.core.net.toUri

// 小趣空间分类模型
data class AppCategory(
    val categoryId: Int?,
    val subCategoryId: Int?,
    val categoryName: String
)

enum class ApkUploadService(val displayName: String) {
    KEYUN("氪云"),
    WANYUEYUN("挽悦云")
}

@KoinViewModel
class AppReleaseViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    
    // --- 商店选择 ---
    private val _selectedStore = MutableStateFlow(AppStore.XIAOQU_SPACE)
    val selectedStore = _selectedStore.asStateFlow()
    
    fun onStoreSelected(store: AppStore) {
        _selectedStore.value = store
        // 切换商店时重置部分状态
        clearProcessFeedback()
    }

    // --- 仓库实例 ---
    private val xiaoQuRepo: IAppStoreRepository = XiaoQuRepository(KtorClient.ApiServiceImpl)
    private val sineOpenRepo: IAppStoreRepository = SineOpenMarketRepository()

    private fun getCurrentRepo(): IAppStoreRepository {
        return when (_selectedStore.value) {
            AppStore.XIAOQU_SPACE -> xiaoQuRepo
            AppStore.SINE_OPEN_MARKET -> sineOpenRepo
            else -> xiaoQuRepo // 默认
        }
    }

    // --- 通用状态 ---
    val appName = mutableStateOf("")
    val packageName = mutableStateOf("")
    val versionName = mutableStateOf("")
    val versionCode = mutableStateOf(0L)
    val appSize = mutableStateOf("") // MB
    val localIconUri = mutableStateOf<Uri?>(null)
    val tempIconFile = mutableStateOf<File?>(null)
    val tempApkFile = mutableStateOf<File?>(null) // 保存本地APK文件引用
    
    // --- 小趣空间特定状态 ---
    val isUpdateMode = mutableStateOf(false)
    private var appId: Long = 0
    private var appVersionId: Long = 0
    val selectedApkUploadService = mutableStateOf(ApkUploadService.KEYUN)
    val appIntroduce = mutableStateOf("资源介绍【密码:】 ")
    val appExplain = mutableStateOf("适配性能描述 •\n包名：\n版本：")
    val isPay = mutableStateOf(0)
    val payMoney = mutableStateOf("")
    val selectedCategoryIndex = mutableStateOf(0)
    val apkDownloadUrl = mutableStateOf("") // 小趣空间用外链
    val iconUrl = mutableStateOf<String?>(null) // 小趣空间用网络图标
    val introductionImageUrls = mutableStateListOf<String>() // 小趣空间用网络图床
    
    val categories = listOf(
        AppCategory(45, 47, "影音阅读"),
        AppCategory(45, 55, "音乐听歌"),
        AppCategory(45, 61, "休闲娱乐"),
        AppCategory(45, 58, "文件管理"),
        AppCategory(45, 59, "图像摄影"),
        AppCategory(45, 53, "输入方式"),
        AppCategory(45, 54, "生活出行"),
        AppCategory(45, 50, "社交通讯"),
        AppCategory(45, 56, "上网浏览"),
        AppCategory(45, 60, "其他类型"),
        AppCategory(45, 62, "跑酷竞技"),
        AppCategory(45, 49, "系统工具"),
        AppCategory(45, 48, "桌面插件"),
        AppCategory(45, 65, "学习教育")
    ).filter { it.categoryId != null && it.subCategoryId != null }
     .distinctBy { it.subCategoryId }
     .sortedBy { it.categoryName }

    // --- 弦开放平台特定状态 ---
    // 应用类型
    val appTypeOptions = listOf("手表应用", "手机应用", "大屏应用", "TV应用", "WearOS应用")
    val appTypeId = mutableStateOf(2) // 手机应用 (默认 ID 为 2)

    // 版本类型
    val versionTypeOptions = listOf("官方版", "正式版", "测试版", "公测版", "美化版", "破解版", "修改版", "免费版", "定制版", "手表版")
    val appVersionTypeId = mutableStateOf(2) // 正式版 (默认 ID 为 2)

    // 应用标签 (从API获取)
    val tagOptions = mutableStateListOf<String>() // 改为可变列表
    val selectedTagIndex = mutableStateOf(0)
    val appTags = mutableStateOf(0) // 默认选中第一个
            
    // 加载标签的函数，由 UI 手动调用
    fun loadTagOptions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 调用 SineShopClient 获取标签列表
                val tagListResult = SineShopClient.getAppTagList()
                if (tagListResult.isSuccess) {
                    val tagList = tagListResult.getOrNull() ?: emptyList() // 直接获取列表
                    // 将标签名称添加到列表中
                    tagOptions.clear()
                    tagOptions.addAll(tagList.map { it.name })
                    // 设置默认选中第一个
                    if (tagList.isNotEmpty()) {
                        selectedTagIndex.value = 0
                        appTags.value = 0 // ID 从 0 开始
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果加载失败，可以设置一个默认列表
                tagOptions.clear()
                tagOptions.addAll(listOf("实用工具", "应用商店", "游戏", "通讯社交", "学习教育"))
                selectedTagIndex.value = 0
                appTags.value = 0
            }
        }
    }

    // SDK 版本状态
    val sdkMin = mutableStateOf(21)
    val sdkTarget = mutableStateOf(33)

    val developer = mutableStateOf("")
    val source = mutableStateOf("互联网")
    val describe = mutableStateOf("介绍一下你的应用…")
    val updateLog = mutableStateOf("本次更新的内容…")
    val uploadMessage = mutableStateOf("给审核员的留言…")
    val keyword = mutableStateOf("关键字")
    val isWearOs = mutableStateOf(0)
    val abi = mutableStateOf(0) // 0: 不限
    val screenshotsUris = mutableStateListOf<Uri>() // 本地截图URI
    val tempScreenshotFiles = mutableListOf<File>()

    // --- 进度状态 ---
    val isApkUploading = mutableStateOf(false)
    val isIconUploading = mutableStateOf(false)
    val isIntroImagesUploading = mutableStateOf(false)
    val isReleasing = mutableStateOf(false)
    private val _processFeedback = MutableStateFlow<Result<String>?>(null)
    val processFeedback = _processFeedback.asStateFlow()
    
    // 根据商店类型定义不同的最大图片数量
    private val MAX_INTRO_IMAGES_XIAOQU = 3
    private val MAX_INTRO_IMAGES_SINE_OPEN = 5
// 弦开放平台：选择截图（保存本地URI，发布时一起上传）
    fun addScreenshots(uris: List<Uri>) {
        if (_selectedStore.value != AppStore.SINE_OPEN_MARKET) return
        
        // 检查当前数量是否已达到上限
        if (screenshotsUris.size >= MAX_INTRO_IMAGES_SINE_OPEN) {
            // 可以通过 Snackbar 或其他方式提示用户
            // 这里为了简化，直接返回
            return
        }
        
        // 计算还能添加多少张
        val currentCount = screenshotsUris.size
        val remainingSlots = MAX_INTRO_IMAGES_SINE_OPEN - currentCount
        val urisToUpload = uris.take(remainingSlots)
        
        screenshotsUris.addAll(urisToUpload)
        
        // 可以在这里异步将 URI 转为 File 存入 tempScreenshotFiles
        viewModelScope.launch(Dispatchers.IO) {
            urisToUpload.forEach { uri ->
                val file = uriToTempFile(context, uri, "screenshot_${System.currentTimeMillis()}.png")
                file?.let { tempScreenshotFiles.add(it) }
            }
        }
    }
    
    fun removeScreenshot(uri: Uri) {
        screenshotsUris.remove(uri)
        // 同步移除 tempScreenshotFiles 逻辑略复杂，建议重置或简单处理
        tempScreenshotFiles.removeIf { it.toUri() == uri }
    }
    // --- APK 解析 ---
    fun parseAndUploadApk(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _processFeedback.value = Result.success("正在解析APK...")
            // 使用 ApkInfo
            val parsedInfo: ApkInfo? = ApkParser.parse(context, uri)

            if (parsedInfo == null) {
                _processFeedback.value = Result.failure(Throwable("APK 文件解析失败"))
                return@launch
            }

            withContext(Dispatchers.Main) {
                appName.value = parsedInfo.appName
                packageName.value = parsedInfo.packageName
                versionName.value = parsedInfo.versionName
                versionCode.value = parsedInfo.versionCode
                appSize.value = parsedInfo.sizeInMb.toString()
                
                // 保存临时文件引用
                tempApkFile.value = parsedInfo.tempApkFile
                tempIconFile.value = parsedInfo.tempIconFile
                localIconUri.value = parsedInfo.tempIconFileUri
                
                // 自动填充字段
                appExplain.value = "适配性能描述 •\n包名：${parsedInfo.packageName}\n版本：${parsedInfo.versionName}"
                
                // 如果是弦开放平台，自动填充 SDK 信息
                if (_selectedStore.value == AppStore.SINE_OPEN_MARKET) {
                    // 使用解析出的 SDK 信息
                    // 注意：minSdkVersion 和 targetSdkVersion 可能为 0，表示未指定
                    if (parsedInfo.minSdkVersion > 0) {
                        sdkMin.value = parsedInfo.minSdkVersion
                    } else {
                        // 如果未指定，可以使用一个默认值
                        sdkMin.value = 21
                    }
                    
                    if (parsedInfo.targetSdkVersion > 0) {
                        sdkTarget.value = parsedInfo.targetSdkVersion
                    } else {
                        // 如果未指定，可以使用一个默认值
                        sdkTarget.value = 33
                    }
                }
            }

            // 仅小趣空间需要立即上传 APK 和图标到图床
            if (_selectedStore.value == AppStore.XIAOQU_SPACE) {
                val uploadJobs = mutableListOf<kotlinx.coroutines.Job>()

                uploadJobs += launch {
                    isApkUploading.value = true
                    val serviceType = when (selectedApkUploadService.value) {
                        ApkUploadService.KEYUN -> "KEYUN"
                        ApkUploadService.WANYUEYUN -> "WANYUEYUN"
                    }
                    val apkResult = xiaoQuRepo.uploadApk(parsedInfo.tempApkFile, serviceType)
                    apkResult.onSuccess { url ->
                        apkDownloadUrl.value = url
                    }.onFailure { e ->
                        withContext(Dispatchers.Main) {
                            _processFeedback.value = Result.failure(e)
                        }
                    }
                    isApkUploading.value = false
                }

                parsedInfo.tempIconFile?.let { iconFile ->
                    uploadJobs += launch {
                        isIconUploading.value = true
                        val imageResult = xiaoQuRepo.uploadImage(iconFile, "icon")
                        imageResult.onSuccess { url ->
                            iconUrl.value = url
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) {
                                _processFeedback.value = Result.failure(e)
                            }
                        }
                        isIconUploading.value = false
                    }
                }
                uploadJobs.joinAll()
            } else {
                _processFeedback.value = Result.success("APK解析完成，准备发布")
            }
        }
    }

    // --- 图片处理 ---
    
    // 小趣空间：上传介绍图到图床
    fun uploadIntroductionImages(uris: List<Uri>) {
        if (_selectedStore.value != AppStore.XIAOQU_SPACE) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val currentCount = introductionImageUrls.size
            if (currentCount >= MAX_INTRO_IMAGES_XIAOQU) {
                _processFeedback.value = Result.failure(Throwable("最多只能上传 $MAX_INTRO_IMAGES_XIAOQU 张介绍图"))
                return@launch
            }

            isIntroImagesUploading.value = true
            // 使用小趣空间的最大图片数量
            val remainingSlots = MAX_INTRO_IMAGES_XIAOQU - currentCount
            val urisToUpload = uris.take(remainingSlots)

            val uploadJobs = urisToUpload.map { uri ->
                launch {
                    val tempFileName = "intro_${System.currentTimeMillis()}.jpg"
                    val tempFile = uriToTempFile(context, uri, tempFileName)
                    tempFile?.let {
                        val imageResult = xiaoQuRepo.uploadImage(it, "intro")
                        imageResult.onSuccess { url ->
                            if (introductionImageUrls.size < MAX_INTRO_IMAGES_XIAOQU) {
                                introductionImageUrls.add(url)
                            }
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) {
                                _processFeedback.value = Result.failure(e)
                            }
                        }
                    }
                }
            }
            uploadJobs.joinAll()
            isIntroImagesUploading.value = false
        }
    }

    private fun createStreamInputProvider(file: File): InputProvider {
        return InputProvider { file.inputStream().asInput() }
    }
    
    private suspend fun uploadToKeyun(file: File, mediaType: String = "application/octet-stream", contextMessage: String = "文件", onSuccess: (String) -> Unit) {
        try {
            val response = KtorClient.uploadHttpClient.submitFormWithBinaryData(
                url = "api.php",
                formData = formData {
                    append("file", createStreamInputProvider(file), Headers.build {
                        append(HttpHeaders.ContentType, mediaType)
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    })
                }
            )

            if (response.status.isSuccess()) {
                val responseBody: KtorClient.UploadResponse = response.body<KtorClient.UploadResponse>()
                if (responseBody.code == 0 && !responseBody.downurl.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        _processFeedback.value = Result.success("$contextMessage (氪云): ${responseBody.msg}")
                        onSuccess(responseBody.downurl)
                    }
                } else {
                    withContext(Dispatchers.Main){
                        _processFeedback.value = Result.failure(Throwable("$contextMessage (氪云): ${responseBody.msg}"))
                    }
                }
            } else {
                withContext(Dispatchers.Main){
                    _processFeedback.value = Result.failure(Throwable("$contextMessage (氪云): 网络错误 ${response.status}"))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main){
                                _processFeedback.value = Result.failure(Throwable("$contextMessage (氪云): ${e.message}"))
            }
        } finally {
            // file.delete() // 暂时注释掉，因为可能需要复用
        }
    }

    private suspend fun uploadToWanyueyun(file: File, onSuccess: (String) -> Unit) {
        try {
            val response = KtorClient.wanyueyunUploadHttpClient.submitFormWithBinaryData(
                url = "upload",
                formData = formData {
                    append("Api", "小趣API")
                    append("file", createStreamInputProvider(file), Headers.build {
                        append(HttpHeaders.ContentType, "application/vnd.android.package-archive")
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    })
                }
            )

            if (response.status.isSuccess()) {
                val responseBody: KtorClient.WanyueyunUploadResponse = response.body<KtorClient.WanyueyunUploadResponse>()
                if (responseBody.code == 200 && !responseBody.data.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        _processFeedback.value = Result.success("APK (挽悦云): ${responseBody.msg}")
                        onSuccess(responseBody.data)
                    }
                } else {
                    withContext(Dispatchers.Main){
                        _processFeedback.value = Result.failure(Throwable("APK (挽悦云): ${responseBody.msg}"))
                    }
                }
            } else {
                withContext(Dispatchers.Main){
                    _processFeedback.value = Result.failure(Throwable("APK (挽悦云): 网络错误 ${response.status}"))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main){
                _processFeedback.value = Result.failure(Throwable("APK (挽悦云): ${e.message}"))
            }
        } finally {
             // file.delete()
        }
    }

    fun clearProcessFeedback() {
        _processFeedback.value = null
    }

    private fun uriToTempFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.cacheDir, fileName)
            file.outputStream().use { outputStream ->
                inputStream.use { it.copyTo(outputStream) }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    fun removeIntroductionImage(url: String) {
        introductionImageUrls.remove(url)
    }

    // --- 发布逻辑 ---
    
    fun releaseApp(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            isReleasing.value = true
            _processFeedback.value = Result.success("正在准备发布...")
            
            try {
                val repo = getCurrentRepo()
                
                // 构建参数
                val params = UnifiedAppReleaseParams(
                    store = _selectedStore.value,
                    appName = appName.value,
                    packageName = packageName.value,
                    versionName = versionName.value,
                    versionCode = versionCode.value,
                    sizeInMb = appSize.value.toDoubleOrNull() ?: 0.0,
                    iconFile = tempIconFile.value,
                    iconUrl = iconUrl.value,
                    apkFile = tempApkFile.value,
                    apkUrl = apkDownloadUrl.value,
                    
                    // 小趣空间
                    introduce = appIntroduce.value,
                    explain = appExplain.value,
                    introImages = introductionImageUrls.toList(),
                    categoryId = categories.getOrNull(selectedCategoryIndex.value)?.categoryId,
                    subCategoryId = categories.getOrNull(selectedCategoryIndex.value)?.subCategoryId,
                    isPay = isPay.value,
                    payMoney = if (isPay.value == 1) payMoney.value else "",
                    isUpdate = isUpdateMode.value,
                    appId = appId,
                    appVersionId = appVersionId,
                    
                    // 弦开放平台
                    appTypeId = appTypeId.value,
                    appVersionTypeId = appVersionTypeId.value,
                    appTags = appTags.value.toString(), // 转换为字符串
                    sdkMin = sdkMin.value,
                    sdkTarget = sdkTarget.value,
                    developer = developer.value,
                    source = source.value,
                    describe = describe.value,
                    updateLog = updateLog.value,
                    uploadMessage = uploadMessage.value,
                    keyword = keyword.value,
                    isWearOs = isWearOs.value,
                    abi = abi.value,
                    screenshots = tempScreenshotFiles.toList()
                )
                
                val result = repo.releaseApp(params)
                
                if (result.isSuccess) {
                    _processFeedback.value = Result.success("发布成功！")
                    withContext(Dispatchers.Main) { onSuccess() }
                } else {
                    _processFeedback.value = Result.failure(result.exceptionOrNull() ?: Exception("发布失败"))
                }
                
            } catch (e: Exception) {
                _processFeedback.value = Result.failure(e)
            } finally {
                isReleasing.value = false
            }
        }
    }

    // --- 辅助方法 ---
    // (保留原有的 populateFromAppDetail, deleteApp, uriToTempFile, createStreamInputProvider 等方法)
    // 为节省篇幅，这里假设它们未变动，实际代码中需完整保留
    
    fun populateFromAppDetail(appDetail: KtorClient.AppDetail) {
        // 仅支持小趣空间回填
        if (_selectedStore.value != AppStore.XIAOQU_SPACE) return
        
        isUpdateMode.value = true
        appId = appDetail.id
        appVersionId = appDetail.apps_version_id

        appName.value = appDetail.appname
        apkDownloadUrl.value = appDetail.download ?: ""

        val explainLines = appDetail.app_explain?.split("\n")
        val pkgNameLine = explainLines?.find { it.startsWith("包名：") }
        packageName.value = pkgNameLine?.substringAfter("包名：")?.trim() ?: ""

        versionName.value = appDetail.version
        versionCode.value = appDetail.apps_version_id // 小趣空间复用
        appSize.value = appDetail.app_size.replace("MB", "").trim()
        appIntroduce.value = appDetail.app_introduce?.replace("<br>", "\n") ?: ""
        appExplain.value = appDetail.app_explain ?: ""
        isPay.value = appDetail.is_pay
        payMoney.value = if (appDetail.pay_money > 0) appDetail.pay_money.toString() else ""
        selectedCategoryIndex.value = categories.indexOfFirst {
            it.categoryId == appDetail.category_id && it.subCategoryId == appDetail.sub_category_id
        }.takeIf { it != -1 } ?: 0

        iconUrl.value = appDetail.app_icon
        localIconUri.value = null
        introductionImageUrls.clear()
        appDetail.app_introduction_image_array?.let {
            introductionImageUrls.addAll(it)
        }
    }
    
    // --- Setter 方法用于下拉菜单 ---
    fun setAppTypeId(id: Int) {
        appTypeId.value = id
    }
    
    fun setAppVersionTypeId(id: Int) {
        appVersionTypeId.value = id
    }
    
    fun setAppTags(id: Int) {
        appTags.value = id
    }
}