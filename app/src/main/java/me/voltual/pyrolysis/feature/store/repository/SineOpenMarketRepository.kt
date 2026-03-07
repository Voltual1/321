package me.voltual.pyrolysis.feature.store.repository

import me.voltual.pyrolysis.OpenMarketSineWorldClient
import me.voltual.pyrolysis.data.unified.*
import java.io.File
import org.koin.core.annotation.Single

@Single
class SineOpenMarketRepository : IAppStoreRepository {

    // --- 读操作：弦开放平台主要用于发布，此处返回空结果 ---
    
    override suspend fun getCategories(): Result<List<UnifiedCategory>> = Result.success(emptyList())
    
    override suspend fun getApps(categoryId: String?, page: Int, userId: String?): Result<Pair<List<UnifiedAppItem>, Int>> = 
        Result.success(Pair(emptyList(), 0))
        
    override suspend fun searchApps(query: String, page: Int, userId: String?): Result<Pair<List<UnifiedAppItem>, Int>> = 
        Result.success(Pair(emptyList(), 0))

    // --- 上传操作：该仓库直接返回路径，由 releaseApp 统一处理文件 ---
    
    override suspend fun uploadImage(file: File, type: String): Result<String> = Result.success(file.absolutePath)
    
    override suspend fun uploadApk(file: File, serviceType: String): Result<String> = Result.success(file.absolutePath)

    // --- 发布应用 ---

    override suspend fun releaseApp(params: UnifiedAppReleaseParams): Result<Unit> {
        return try {
            val iconFile = params.iconFile ?: throw Exception("必须提供图标文件")
            val apkFile = params.apkFile ?: throw Exception("必须提供APK文件")
            val screenshots = params.screenshots.takeIf { !it.isNullOrEmpty() } ?: throw Exception("至少提供一张截图")

            // 1. 构造预上传信息
            val preUploadInfo = OpenMarketSineWorldClient.AppReleaseInfo(
                appName = params.appName,
                packageName = params.packageName,
                versionName = params.versionName,
                versionCode = params.versionCode.toString(),
                appTypeId = params.appTypeId ?: 1,
                appVersionTypeId = params.appVersionTypeId ?: 1,
                appTags = params.appTags ?: "1",
                appSdkMin = params.sdkMin ?: 1,
                appSdkTarget = params.sdkTarget ?: 1,
                appDeveloper = params.developer ?: "",
                appSource = params.source ?: "互联网",
                appDescribe = params.describe ?: "",
                appUpdateLog = params.updateLog ?: "",
                uploadMessage = params.uploadMessage ?: "",
                keyword = params.keyword ?: "",
                appIsWearos = params.isWearOs ?: 0,
                appAbi = params.abi ?: 0,
                downloadSize = (params.sizeInMb * 1024 * 1024).toLong(),
                iconFile = iconFile,
                screenshotFiles = screenshots
            )

            // 2. 执行预上传并获取 Token
            val preResult = OpenMarketSineWorldClient.preUpload(preUploadInfo).getOrThrow()
            
            // 3. 上传 APK
            OpenMarketSineWorldClient.uploadApk(apkFile, preResult.uploadToken).getOrThrow()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 注意：所有的 deleteComment, getAppDetail, toggleFavorite, updateUserProfile 等
    // 统统删掉了！它们会自动使用 IAppStoreRepository 里的默认“不支持”实现。
}