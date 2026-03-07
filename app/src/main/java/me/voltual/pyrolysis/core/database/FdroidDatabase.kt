/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.database

import androidx.room.Database
import java.io.File
import android.util.Log
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.TypeConverters
import androidx.room.Room
import me.voltual.pyrolysis.core.database.FdroidDatabase.Companion.dbCreateCallback
import androidx.sqlite.db.SupportSQLiteDatabase
import me.voltual.pyrolysis.core.database.dao.*
import me.voltual.pyrolysis.core.database.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import org.koin.dsl.module

@Database(
    entities = [
        Repository::class,
        Product::class,
        Release::class,
        ReleaseTemp::class,
        ProductTemp::class,
        Category::class,
        CategoryTemp::class,
        RepoCategory::class,
        RepoCategoryTemp::class,
        Installed::class,
        Extras::class,
        ExodusInfo::class,
        Tracker::class,
        AntiFeature::class,
        AntiFeatureTemp::class,
        RBLog::class,
        InstallTask::class,        
        Downloaded::class,
        DownloadStats::class,
        DownloadStatsFileMetadata::class,        
    ],
    views = [
        PackageSum::class,
        ClientPackageSum::class,
        MonthlyPackageSum::class,
        ProductIconDetails::class,
    ],
    version = 1, // 纯净的 V1 版本
    exportSchema = false//true
)
@TypeConverters(Converters::class)
abstract class FdroidDatabase : RoomDatabase() {
    
     abstract fun getRepositoryDao(): RepositoryDao
     abstract fun getProductDao(): ProductDao
     abstract fun getReleaseDao(): ReleaseDao
     abstract fun getReleaseTempDao(): ReleaseTempDao 
     abstract fun getProductTempDao(): ProductTempDao 
     abstract fun getCategoryDao(): CategoryDao 
     abstract fun getCategoryTempDao(): CategoryTempDao 
     abstract fun getRepoCategoryDao(): RepoCategoryDao 
     abstract fun getRepoCategoryTempDao(): RepoCategoryTempDao 
     abstract fun getAntiFeatureDao(): AntiFeatureDao 
     abstract fun getAntiFeatureTempDao(): AntiFeatureTempDao 
     abstract fun getInstalledDao(): InstalledDao 
     abstract fun getExtrasDao(): ExtrasDao 
     abstract fun getExodusInfoDao(): ExodusInfoDao 
     abstract fun getTrackerDao(): TrackerDao 
     abstract fun getRBLogDao(): RBLogDao
     abstract fun getInstallTaskDao(): InstallTaskDao
     abstract fun getDownloadedDao(): DownloadedDao
     abstract fun getDownloadStatsDao(): DownloadStatsDao
     abstract fun getDownloadStatsFileDao(): DownloadStatsFileDao

     
     suspend fun cleanUp(vararg pairs: Pair<Long, Boolean>) {
        withTransaction {
            pairs.forEach { (id, enabled) ->
                getProductDao().deleteById(id)
                getCategoryDao().deleteById(id)
                getReleaseDao().deleteById(id)
                if (enabled) getRepositoryDao().deleteById(id)
            }
        }
    }

    suspend fun cleanUp(pairs: Set<Pair<Long, Boolean>>) = cleanUp(*pairs.toTypedArray())
    
    suspend fun finishTemporary(repository: Repository, success: Boolean) {
        withTransaction {
            if (success) {
                getProductDao().deleteById(repository.id)
                getCategoryDao().deleteById(repository.id)
                getRepoCategoryDao().deleteByRepoId(repository.id)
                getAntiFeatureDao().deleteByRepoId(repository.id)
                getReleaseDao().deleteById(repository.id)
                getProductDao().insert(*(getProductTempDao().getAll()))
                getCategoryDao().insert(*(getCategoryTempDao().getAll()))
                getRepoCategoryDao().insert(*(getRepoCategoryTempDao().getAll()))
                getAntiFeatureDao().insert(*(getAntiFeatureTempDao().getAll()))
                getReleaseDao().insert(*(getReleaseTempDao().getAll()))
                getRepositoryDao().put(repository)
            }
            getProductTempDao().emptyTable()
            getCategoryTempDao().emptyTable()
            getRepoCategoryTempDao().emptyTable()
            getAntiFeatureTempDao().emptyTable()
            getReleaseTempDao().emptyTable()
        }
    }    

    // 在 FdroidDatabase.kt 中
companion object {
    const val TAG = "fdroid.db"

    val dbCreateCallback = object : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // 注意：Room 的 onCreate 在数据库物理创建时触发
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = get<RepositoryDao>(RepositoryDao::class.java)
                    
                    // 只有数据库为空时才初始化
                    if (dao.getCount() == 0) {
                        val allRepos = (Repository.defaultRepositories + loadPresetRepos())
                            .distinctBy { it.address }
                            .toTypedArray()
                        
                        dao.put(*allRepos)
                        Log.d(TAG, "Database initialized with ${allRepos.size} repositories.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to seed initial repositories", e)
                }
            }
        }
    }

    /**
     * 从系统不同分区加载 OEM 预置仓库
     */
    private fun loadPresetRepos(): List<Repository> {
        val roots = listOf("/system", "/product", "/vendor", "/odm", "/oem")
        return roots.mapNotNull { root ->
            // F-Droid 标准的预置路径
            val additionalReposFile = File("$root/etc/org.fdroid.fdroid/additional_repos.xml")
            if (additionalReposFile.exists() && additionalReposFile.isFile) {
                Repository.parsePresetReposXML(additionalReposFile)
            } else {
                null
            }
        }.flatten()
    }
}
}

val databaseModule = module {
    single {
        Room.databaseBuilder(
            get(),
            FdroidDatabase::class.java,
            "main_fdroid_database.db"
        )
            .addCallback(dbCreateCallback)
            .fallbackToDestructiveMigration(true)
            .build()
    }
    single { get<FdroidDatabase>().getRepositoryDao() }
    single { get<FdroidDatabase>().getProductDao() }
    single { get<FdroidDatabase>().getReleaseDao() }
    single { get<FdroidDatabase>().getReleaseTempDao() }
    single { get<FdroidDatabase>().getProductTempDao() }
    single { get<FdroidDatabase>().getCategoryDao() }
    single { get<FdroidDatabase>().getCategoryTempDao() }
    single { get<FdroidDatabase>().getRepoCategoryDao() }
    single { get<FdroidDatabase>().getRepoCategoryTempDao() }
    single { get<FdroidDatabase>().getAntiFeatureDao() }
    single { get<FdroidDatabase>().getAntiFeatureTempDao() }
    single { get<FdroidDatabase>().getInstalledDao() }
    single { get<FdroidDatabase>().getExtrasDao() }
    single { get<FdroidDatabase>().getExodusInfoDao() }
    single { get<FdroidDatabase>().getTrackerDao() }
    single { get<FdroidDatabase>().getDownloadedDao() }
    single { get<FdroidDatabase>().getInstallTaskDao() }
    single { get<FdroidDatabase>().getRBLogDao() }
    single { get<FdroidDatabase>().getDownloadStatsDao() }
    single { get<FdroidDatabase>().getDownloadStatsFileDao() }
}