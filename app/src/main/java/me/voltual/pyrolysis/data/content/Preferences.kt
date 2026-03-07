package me.voltual.pyrolysis.data.content

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.work.NetworkType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.voltual.pyrolysis.core.utils.extension.android.Android
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.FILTER_CATEGORY_ALL
import me.voltual.pyrolysis.core.utils.*
import me.voltual.pyrolysis.PREFS_LANGUAGE
import me.voltual.pyrolysis.PREFS_LANGUAGE_DEFAULT
import me.voltual.pyrolysis.data.entity.*
import me.voltual.pyrolysis.BBQApplication

/**
 * 全局首选项管理对象
 * 采用响应式设计，通过 MutableSharedFlow 暴露配置变更
 */
data object Preferences : OnSharedPreferenceChangeListener {
    private lateinit var preferences: SharedPreferences
    private val subject = MutableSharedFlow<Key<*>>()

    // 映射所有注册的 Key，用于在监听器中根据 String 找回 Key 对象
    private val keys = sequenceOf(
        // 通用应用设置
        Key.HideNewApps, Key.UpdatedApps, Key.NewApps, Key.Language,
        Key.IndexV2, Key.UpdateUnstable, Key.UpdateNotify,
        Key.KidsMode,
        Key.ShowScreenshots,
        Key.RootAllowInstallingOldApps,
        Key.AltBlockLayout,
        Key.DownloadShowDialog,
        Key.BottomSearchBar,
        Key.RootAllowDowngrades,
        Key.InstallAfterSync,
        Key.RootSessionInstaller,
        Key.KeepInstallNotification,
        Key.Installer,
        Key.AndroidInsteadOfSDK,
        Key.ReleasesCacheRetention,
        Key.ActionLockDialog,
        
        // 排序设置
        Key.SortOrderExplore, Key.SortOrderLatest, Key.SortOrderInstalled, Key.SortOrderSearch,
        Key.SortOrderAscendingExplore, Key.SortOrderAscendingLatest, 
        Key.SortOrderAscendingInstalled, Key.SortOrderAscendingSearch,

        // 过滤器 - 仓库
        Key.ReposFilterExplore, Key.ReposFilterLatest, Key.ReposFilterInstalled, Key.ReposFilterSearch,

        // 过滤器 - 分类
        Key.CategoriesFilterExplore, Key.CategoriesFilterLatest, 
        Key.CategoriesFilterInstalled, Key.CategoriesFilterSearch,

        // 过滤器 - 特性与许可
        Key.AntifeaturesFilterExplore, Key.AntifeaturesFilterLatest, 
        Key.AntifeaturesFilterInstalled, Key.AntifeaturesFilterSearch,
        Key.LicensesFilterExplore, Key.LicensesFilterLatest, 
        Key.LicensesFilterInstalled, Key.LicensesFilterSearch,

        // 过滤器 - SDK 版本
        Key.MinSDKExplore, Key.MinSDKLatest, Key.MinSDKInstalled, Key.MinSDKSearch,
        Key.TargetSDKExplore, Key.TargetSDKLatest, Key.TargetSDKInstalled, Key.TargetSDKSearch,

        // 下载与网络设置
        Key.MaxParallelDownloads, Key.MaxIdleConnections, Key.DownloadManager,
        Key.DownloadDirectory, Key.EnableDownloadDirectory, Key.DisableCertificateValidation,
        
        // 同步与元数据
        Key.AutoSync, Key.AutoSyncInterval, Key.ShowTrackers, Key.TrackersLastModified,
        Key.RBLogsLastModified, Key.RBProvider, Key.DLStatsProvider,
        
        // 安全与兼容性
        Key.DisableSignatureCheck, Key.DisablePermissionsCheck, Key.IncompatibleVersions
    ).associateBy { it.name }

    fun init(context: Context) {
        preferences = context.getSharedPreferences(
            "${context.packageName}_preferences",
            Context.MODE_PRIVATE
        )
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        CoroutineScope(Dispatchers.Default).launch {
            keys[key]?.let { subject.emit(it) }
        }
    }

    suspend fun addPreferencesChangeListener(listener: suspend (Key<*>) -> Unit) {
        subject.collect { listener(it) }
    }

    operator fun <T> get(key: Key<T>): T {
        return key.default.get(preferences, key.name, key.default)
    }

    operator fun <T> set(key: Key<T>, value: T) {
        key.default.set(preferences, key.name, value)
    }

    // --- 数据存储实现 (Value Definitions) ---

    sealed class Value<T> {
        abstract val value: T
        internal abstract fun get(preferences: SharedPreferences, key: String, defaultValue: Value<T>): T
        internal abstract fun set(preferences: SharedPreferences, key: String, value: T)

        class BooleanValue(override val value: Boolean) : Value<Boolean>() {
            override fun get(preferences: SharedPreferences, key: String, defaultValue: Value<Boolean>) =
                preferences.getBoolean(key, defaultValue.value)
            override fun set(preferences: SharedPreferences, key: String, value: Boolean) =
                preferences.edit().putBoolean(key, value).apply()
        }

        class IntValue(override val value: Int) : Value<Int>() {
            override fun get(preferences: SharedPreferences, key: String, defaultValue: Value<Int>) =
                preferences.getInt(key, defaultValue.value)
            override fun set(preferences: SharedPreferences, key: String, value: Int) =
                preferences.edit().putInt(key, value).apply()
        }

        class StringValue(override val value: String) : Value<String>() {
            override fun get(preferences: SharedPreferences, key: String, defaultValue: Value<String>) =
                preferences.getString(key, defaultValue.value) ?: defaultValue.value
            override fun set(preferences: SharedPreferences, key: String, value: String) =
                preferences.edit().putString(key, value).apply()
        }

        class StringSetValue(override val value: Set<String>) : Value<Set<String>>() {
            override fun get(preferences: SharedPreferences, key: String, defaultValue: Value<Set<String>>) =
                preferences.getStringSet(key, defaultValue.value) ?: emptySet()
            override fun set(preferences: SharedPreferences, key: String, value: Set<String>) =
                preferences.edit().putStringSet(key, value).apply()
        }

        class EnumerationValue<T : Enumeration<T>>(override val value: T) : Value<T>() {
            override fun get(preferences: SharedPreferences, key: String, defaultValue: Value<T>): T {
                val v = preferences.getString(key, defaultValue.value.valueString)
                return defaultValue.value.values.find { it.valueString == v } ?: defaultValue.value
            }
            override fun set(preferences: SharedPreferences, key: String, value: T) =
                preferences.edit().putString(key, value.valueString).apply()
        }

        class EnumValue<T>(override val value: T, private val enumClass: Class<T>) : Value<T>()
                where T : Enum<T>, T : EnumEnumeration {
            override fun get(preferences: SharedPreferences, key: String, defaultValue: Value<T>): T {
                val v = preferences.getInt(key, defaultValue.value.ordinal)
                return enumClass.enumConstants?.getOrNull(v) ?: defaultValue.value
            }
            override fun set(preferences: SharedPreferences, key: String, value: T) =
                preferences.edit().putInt(key, value.ordinal).apply()
        }
    }

    interface Enumeration<T> { val values: List<T>; val valueString: String }
    interface EnumEnumeration { val valueString: String }

    // --- 键定义 (Key Definitions) ---

    sealed class Key<T>(val name: String, val default: Value<T>) {
        
        // 1. 应用基础设置
        data object HideNewApps : Key<Boolean>("hide_new_apps", Value.BooleanValue(false))
        data object UpdatedApps : Key<Int>("updated_apps", Value.IntValue(150))
        data object NewApps : Key<Int>("new_apps", Value.IntValue(30))
        data object Language : Key<String>(PREFS_LANGUAGE, Value.StringValue(PREFS_LANGUAGE_DEFAULT))
        data object IndexV2 : Key<Boolean>("index_v2", Value.BooleanValue(true))
        data object UpdateNotify : Key<Boolean>("update_notify", Value.BooleanValue(true))

        // 2. 排序逻辑 (Sort Orders)
        sealed class SortOrder(override val valueString: String, val order: Order) : Enumeration<SortOrder> {
            override val values: List<SortOrder> get() = listOf(Name, Added, Update)
            data object Name : SortOrder("name", Order.NAME)
            data object Added : SortOrder("added", Order.DATE_ADDED)
            data object Update : SortOrder("update", Order.LAST_UPDATE)
        }

        data object SortOrderExplore : Key<SortOrder>("sort_order_explore", Value.EnumerationValue(SortOrder.Update))
        data object SortOrderLatest : Key<SortOrder>("sort_order_latest_fix", Value.EnumerationValue(SortOrder.Update))
        data object SortOrderInstalled : Key<SortOrder>("sort_order_installed", Value.EnumerationValue(SortOrder.Update))
        data object SortOrderSearch : Key<SortOrder>("sort_order_search", Value.EnumerationValue(SortOrder.Update))

        data object SortOrderAscendingExplore : Key<Boolean>("sort_order_ascending_explore", Value.BooleanValue(false))
        data object SortOrderAscendingLatest : Key<Boolean>("sort_order_ascending_latest", Value.BooleanValue(false))
        data object SortOrderAscendingInstalled : Key<Boolean>("sort_order_ascending_installed", Value.BooleanValue(false))
        data object SortOrderAscendingSearch : Key<Boolean>("sort_order_ascending_search", Value.BooleanValue(false))
        data object AltBlockLayout : Key<Boolean>("alt_block_layout", Value.BooleanValue(false))

        // 3. 过滤器设置 (Filters)
        data object ReposFilterExplore : Key<Set<String>>("repos_filter_explore", Value.StringSetValue(emptySet()))
        data object ReposFilterLatest : Key<Set<String>>("repos_filter_latest", Value.StringSetValue(emptySet()))
        data object ReposFilterInstalled : Key<Set<String>>("repos_filter_installed", Value.StringSetValue(emptySet()))
        data object ReposFilterSearch : Key<Set<String>>("repos_filter_search", Value.StringSetValue(emptySet()))

        data object CategoriesFilterExplore : Key<String>("category_filter_explore_fix", Value.StringValue(""))
        data object CategoriesFilterLatest : Key<String>("category_filter_latest", Value.StringValue(FILTER_CATEGORY_ALL))
        data object CategoriesFilterInstalled : Key<String>("category_filter_installed", Value.StringValue(FILTER_CATEGORY_ALL))
        data object CategoriesFilterSearch : Key<String>("category_filter_search", Value.StringValue(FILTER_CATEGORY_ALL))

        data object AntifeaturesFilterExplore : Key<Set<String>>("antifeatures_filter_explore", Value.StringSetValue(emptySet()))
        data object AntifeaturesFilterLatest : Key<Set<String>>("antifeatures_filter_latest", Value.StringSetValue(emptySet()))
        data object AntifeaturesFilterInstalled : Key<Set<String>>("antifeatures_filter_installed", Value.StringSetValue(emptySet()))
        data object AntifeaturesFilterSearch : Key<Set<String>>("antifeatures_filter_search", Value.StringSetValue(emptySet()))

        data object LicensesFilterExplore : Key<Set<String>>("licenses_filter_explore", Value.StringSetValue(emptySet()))
        data object LicensesFilterLatest : Key<Set<String>>("licenses_filter_latest", Value.StringSetValue(emptySet()))
        data object LicensesFilterInstalled : Key<Set<String>>("licenses_filter_installed", Value.StringSetValue(emptySet()))
        data object LicensesFilterSearch : Key<Set<String>>("licenses_filter_search", Value.StringSetValue(emptySet()))

        // 4. SDK 版本过滤
        data object MinSDKExplore : Key<AndroidVersion>("minsdk_filter_explore", Value.EnumValue(AndroidVersion.Unknown, AndroidVersion::class.java))
        data object MinSDKLatest : Key<AndroidVersion>("minsdk_filter_latest", Value.EnumValue(AndroidVersion.Unknown, AndroidVersion::class.java))
        data object MinSDKInstalled : Key<AndroidVersion>("minsdk_filter_installed", Value.EnumValue(AndroidVersion.Unknown, AndroidVersion::class.java))
        data object MinSDKSearch : Key<AndroidVersion>("minsdk_filter_search", Value.EnumValue(AndroidVersion.Unknown, AndroidVersion::class.java))

        data object TargetSDKExplore : Key<AndroidVersion>("targetsdk_filter_explore", Value.EnumValue(AndroidVersion.Unknown, AndroidVersion::class.java))
        data object TargetSDKLatest : Key<AndroidVersion>("targetsdk_filter_latest", Value.EnumValue(AndroidVersion.Unknown, AndroidVersion::class.java))
        data object TargetSDKInstalled : Key<AndroidVersion>("targetsdk_filter_installed", Value.EnumValue(AndroidVersion.Unknown, AndroidVersion::class.java))
        data object TargetSDKSearch : Key<AndroidVersion>("targetsdk_filter_search", Value.EnumValue(AndroidVersion.Unknown, AndroidVersion::class.java))
        data object ShowTrackers : Key<Boolean>("show_trackers", Value.BooleanValue(true))

        // 5. 网络与下载管理
        data object MaxParallelDownloads : Key<Int>("max_num_parallel_downloads", Value.IntValue(5))
        data object MaxIdleConnections : Key<Int>("max_num_idle_connections", Value.IntValue(10))
        data object DownloadManager : Key<Boolean>("system_download_manager", Value.BooleanValue(false))
        data object DownloadDirectory : Key<String>("download_directory_value", Value.StringValue(""))
        data object EnableDownloadDirectory : Key<Boolean>("download_directory_enable", Value.BooleanValue(false))
        data object DisableCertificateValidation : Key<Boolean>("disable_certificate_validation", Value.BooleanValue(false))

        // 6. 同步与提供程序
        data object AutoSync : Key<Preferences.AutoSync>("auto_sync", Value.EnumerationValue(Preferences.AutoSync.Wifi))
        data object AutoSyncInterval : Key<Int>("auto_sync_interval_hours", Value.IntValue(8))
        data object RBProvider : Key<Preferences.RBProvider>("rb_provider", Value.EnumerationValue(Preferences.RBProvider.IzzyOnDroid))
        data object DLStatsProvider : Key<Preferences.DLStatsProvider>("dlstats_provider", Value.EnumerationValue(Preferences.DLStatsProvider.IzzyOnDroid))
        data object TrackersLastModified : Key<String>("last_modified_trackers", Value.StringValue(""))
        data object RBLogsLastModified : Key<String>("last_modified_rblogs", Value.StringValue(""))
        data object ShowScreenshots :
            Key<Boolean>("show_screenshots", Value.BooleanValue(true))
        data object DisableDownloadVersionCheck :
            Key<Boolean>("disable_download_version_check", Value.BooleanValue(false))
        data object ActionLockDialog :
            Key<ActionLock>("action_lock", Value.EnumerationValue(ActionLock.None))

        // 7. 安全与高级
        data object DisableSignatureCheck : Key<Boolean>("disable_signature_check", Value.BooleanValue(false))
        data object DisablePermissionsCheck : Key<Boolean>("disable_permissions_check", Value.BooleanValue(false))
        data object IncompatibleVersions : Key<Boolean>("incompatible_versions", Value.BooleanValue(false))
        data object KidsMode : Key<Boolean>("kids_mode", Value.BooleanValue(false))
        data object UpdateUnstable : Key<Boolean>("update_unstable", Value.BooleanValue(false))
        data object DownloadShowDialog :
            Key<Boolean>("download_show_dialog", Value.BooleanValue(false))
            data object AndroidInsteadOfSDK :
            Key<Boolean>("android_instead_of_sdk", Value.BooleanValue(true))
        data object Installer : Key<Preferences.Installer>(
            "installer_type",
            Value.EnumerationValue(Preferences.Installer.Default)
        )
        data object KeepInstallNotification :
            Key<Boolean>("keep_install_notification", Value.BooleanValue(false))
        data object BottomSearchBar : Key<Boolean>("bottom_search_bar", Value.BooleanValue(false))
        data object ReleasesCacheRetention : Key<Int>("releases_cache_retention", Value.IntValue(1))
        data object RootAllowDowngrades :
            Key<Boolean>(
                "root_allow_downgrades",
                Value.BooleanValue(false)
            )
            data object RootAllowInstallingOldApps :
            Key<Boolean>(
                "root_allow_low_target_sdk",
                Value.BooleanValue(false)
            )
            data object RootSessionInstaller :
            Key<Boolean>(
                "root_session_installer",
                Value.BooleanValue(Android.sdk(Build.VERSION_CODES.TIRAMISU))
            )
            data object InstallAfterSync :
            Key<Boolean>(
                "auto_sync_install",
                Value.BooleanValue(Android.sdk(Build.VERSION_CODES.S))
            )
    }

    // --- 枚举类定义 (Enums) ---
    
    sealed class Installer(override val valueString: String, val installer: InstallerType) :
        Enumeration<Installer> {
        override val values: List<Installer>
            get() = buildList {
                addAll(listOf(Default, Root, Legacy))
                if (BBQApplication.context.amInstalled)
                    add(AM)
                if (BBQApplication.context.getHasSystemInstallPermission())
                    add(System)
                if (BBQApplication.context.hasShizukuOrSui)
                    add(Shizuku)
            }

        data object Default : Installer("session", InstallerType.DEFAULT)
        data object Root : Installer("root", InstallerType.ROOT)
        data object AM : Installer("app_manager", InstallerType.AM)
        data object Legacy : Installer("legacy", InstallerType.LEGACY)
        data object System : Installer("system", InstallerType.SYSTEM)
        data object Shizuku : Installer("shizuku", InstallerType.SHIZUKU)
    }

    sealed class AutoSync(override val valueString: String) : Enumeration<AutoSync> {
        override val values: List<AutoSync> get() = listOf(Never, Wifi, WifiBattery, Battery, Always)
        data object Never : AutoSync("never")
        data object Wifi : AutoSync("wifi")
        data object WifiBattery : AutoSync("wifi-battery")
        data object Battery : AutoSync("battery")
        data object Always : AutoSync("always")

        fun requireBattery() = this is Battery || this is WifiBattery
        fun connectionType() = when (this) {
            Wifi, WifiBattery -> NetworkType.UNMETERED
            else -> NetworkType.CONNECTED
        }
    }
    
    sealed class ActionLock(override val valueString: String, val order: Order) :
        Enumeration<ActionLock> {
        override val values: List<ActionLock>
            get() = buildList {
                addAll(listOf(None, Device))
                if (BBQApplication.context.isBiometricLockAvailable())
                    add(Biometric)
            }

        data object None : ActionLock("none", Order.NAME)
        data object Device : ActionLock("device", Order.DATE_ADDED)
        data object Biometric : ActionLock("biometric", Order.LAST_UPDATE)
    }

    sealed class RBProvider(override val valueString: String) : Enumeration<RBProvider> {
        override val values: List<RBProvider> get() = persistentListOf(None, IzzyOnDroid, BG443, OBFUSK)
        abstract val url: String

        data object None : RBProvider("none") { override val url = "" }
        data object IzzyOnDroid : RBProvider("iod") { override val url = "https://codeberg.org/IzzyOnDroid/rbtlog/raw/branch/izzy/log" }
        data object BG443 : RBProvider("bg443") { override val url = "https://codeberg.org/bg443/rbtlog/raw/branch/master" }
        data object OBFUSK : RBProvider("obfusk") { override val url = "https://codeberg.org/obfusk/rbtlog/raw/branch/log" }
    }

    sealed class DLStatsProvider(override val valueString: String) : Enumeration<DLStatsProvider> {
        override val values: List<DLStatsProvider> get() = persistentListOf(None, IzzyOnDroid)
        abstract val url: String

        data object None : DLStatsProvider("none") { override val url = "" }
        data object IzzyOnDroid : DLStatsProvider("iod") { override val url = "https://dlstats.izzyondroid.org/iod-stats-collector" }
    }
}