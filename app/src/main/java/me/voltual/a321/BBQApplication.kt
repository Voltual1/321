@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package me.voltual.a321

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.voltual.a321.core.ui.theme.ThemeColorStore
import me.voltual.a321.core.ui.theme.ThemeManager
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinApplication
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

    override fun onCreate() {
        super.onCreate()
        // 初始化
        AuthManager.initialize(this)
        ThemeManager.initialize(this)
        ThemeManager.customColorSet = ThemeColorStore.loadColors(this)
    }

    override fun onKoinStartup() = koinConfiguration {
        androidContext(this@BBQApplication)
        modules(emptyList())//暂时这样
    }

}