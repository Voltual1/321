@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package me.voltual.pyrolysis

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.voltual.pyrolysis.core.database.*
import me.voltual.pyrolysis.core.ui.theme.ThemeColorStore
import me.voltual.pyrolysis.core.ui.theme.ThemeManager
import me.voltual.pyrolysis.data.content.Preferences
import me.voltual.pyrolysis.feature.store.repository.privacyModule
import me.voltual.pyrolysis.feature.store.worker.WorkerManager
import me.voltual.pyrolysis.feature.store.worker.workmanagerModule
import me.voltual.pyrolysis.manager.network.downloadClientModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinApplication
import me.voltual.pyrolysis.manager.installer.*
import org.koin.dsl.koinConfiguration
import org.koin.java.KoinJavaComponent.inject
import java.lang.ref.WeakReference

/**
 * Copyright (C) 2025 Voltual
 * GNU General Public License v3.0 or later.
 */
@KoinApplication
class BBQApplication : Application(), KoinStartup {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val wm: WorkerManager by inject()
    val db: FdroidDatabase by inject()

    // 数据库单例
    lateinit var database: AppDatabase
        private set

    // 用于适配 InstallWorker 的 Activity 引用
    private var activityRef: WeakReference<AppCompatActivity> = WeakReference(null)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 核心：自动追踪当前活跃的 Activity，解决 InstallWorker 的引用需求
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is AppCompatActivity) {
                    activityRef = WeakReference(activity)
                }
            }
            override fun onActivityPaused(activity: Activity) {
                if (activityRef.get() == activity) {
                    activityRef.clear()
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // 其他初始化
        AuthManager.initialize(this)
        Preferences.init(this)
        database = AppDatabase.getDatabase(this)
        ThemeManager.initialize(this)
        ThemeManager.customColorSet = ThemeColorStore.loadColors(this)
    }

    override fun onKoinStartup() = koinConfiguration {
        androidContext(this@BBQApplication)
        modules(
            downloadClientModule,
            workmanagerModule,
            databaseModule,
            privacyModule,
            installerModule,
            appModule,
        )
    }

    companion object {
        lateinit var instance: BBQApplication
            private set

        // 适配 InstallWorker 调用 BBQApplication.mainActivity
        var mainActivity: AppCompatActivity?
            get() = instance.activityRef.get()
            set(_) {} // 禁止手动设置，由 Lifecycle 自动维护
        //暴露给全局类作用域
        val wm: WorkerManager get() = instance.wm
        val db: FdroidDatabase get() = instance.db
        val context: Context get() = instance
        val latestSyncs: MutableMap<Long, Long> = mutableMapOf()
        val installer: AppInstaller by inject(AppInstaller::class.java)
    }
}