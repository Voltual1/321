package me.voltual.pyrolysis.data.content

import android.os.Build
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.core.utils.extension.android.Android

/**
 * 这里的 Map 只保留你 Preferences 对象中真实存在的 Key
 */
val BooleanPrefsMeta : Map<Preferences.Key<Boolean>, Pair<Int, Int>> = mapOf(
    Preferences.Key.ShowScreenshots to Pair(
        R.string.show_screenshots,
        R.string.show_screenshots_description
    ),
    Preferences.Key.ShowTrackers to Pair(
        R.string.show_trackers,
        R.string.show_trackers_description
    ),
    Preferences.Key.HideNewApps to Pair(
        R.string.hide_new_apps,
        R.string.hide_new_apps_description
    ),
    Preferences.Key.AltBlockLayout to Pair(
        R.string.alt_block_layout,
        R.string.alt_block_layout_summary
    ),
    Preferences.Key.AndroidInsteadOfSDK to Pair(
        R.string.android_instead_of_sdk,
        R.string.android_instead_of_sdk_summary
    ),
    Preferences.Key.UpdateNotify to Pair(
        R.string.notify_about_updates,
        R.string.notify_about_updates_summary
    ),
    Preferences.Key.UpdateUnstable to Pair(
        R.string.unstable_updates,
        R.string.unstable_updates_summary
    ),
    Preferences.Key.IncompatibleVersions to Pair(
        R.string.incompatible_versions,
        R.string.incompatible_versions_summary
    ),
    Preferences.Key.DisableSignatureCheck to Pair(
        R.string.disable_signature_check,
        R.string.disable_signature_check_summary
    ),
    Preferences.Key.DisablePermissionsCheck to Pair(
        R.string.disable_permissions_check,
        R.string.disable_permissions_check_summary
    ),
    Preferences.Key.EnableDownloadDirectory to Pair(
        R.string.enable_download_directory,
        R.string.enable_download_directory_summary
    ),
    Preferences.Key.DownloadManager to Pair(
        R.string.download_manager,
        R.string.download_manager_summary
    ),
    Preferences.Key.IndexV2 to Pair(
        R.string.index_v2,
        R.string.index_v2_summary
    ),
    Preferences.Key.DownloadShowDialog to Pair(
        R.string.download_show_dialog,
        R.string.download_show_dialog_summary
    ),
    Preferences.Key.KidsMode to Pair(
        R.string.kids_mode,
        // 这里注意：Preferences[Key] 是你的自定义操作符
        if (Preferences[Preferences.Key.KidsMode]) R.string.kids_mode_summary
        else R.string.kids_mode_summary_full
    ),
    Preferences.Key.DisableCertificateValidation to Pair(
        R.string.disable_certificate_check,
        R.string.disable_certificate_check_summary
    ),
)

val NonBooleanPrefsMeta = mapOf(
    Preferences.Key.Language to R.string.prefs_language_title,
    Preferences.Key.UpdatedApps to R.string.prefs_updated_apps,
    Preferences.Key.NewApps to R.string.prefs_new_apps,
    Preferences.Key.AutoSync to R.string.sync_repositories_automatically,
    Preferences.Key.AutoSyncInterval to R.string.auto_sync_interval_hours,
    Preferences.Key.DownloadDirectory to R.string.custom_download_directory,
    Preferences.Key.RBProvider to R.string.rb_provider,
    Preferences.Key.DLStatsProvider to R.string.dlstats_provider,
    Preferences.Key.ActionLockDialog to R.string.action_lock_dialog,
)

val PrefsEntries = mapOf(
    Preferences.Key.ActionLockDialog to mapOf(
        Preferences.ActionLock.None to R.string.action_lock_none,
        Preferences.ActionLock.Device to R.string.action_lock_device,
        Preferences.ActionLock.Biometric to R.string.action_lock_biometric,
    ),
    Preferences.Key.AutoSync to mapOf(
        Preferences.AutoSync.Never to R.string.never,
        Preferences.AutoSync.Wifi to R.string.only_on_wifi,
        Preferences.AutoSync.WifiBattery to R.string.only_on_wifi_and_battery,
        Preferences.AutoSync.Battery to R.string.only_on_battery,
        Preferences.AutoSync.Always to R.string.always,
    ),
    Preferences.Key.RBProvider to mapOf(
        Preferences.RBProvider.None to R.string.rb_none,
        Preferences.RBProvider.IzzyOnDroid to R.string.rb_izzyondroid,
        Preferences.RBProvider.BG443 to R.string.rb_bg443,
        Preferences.RBProvider.OBFUSK to R.string.rb_obfusk,
    ),
    Preferences.Key.DLStatsProvider to mapOf(
        Preferences.DLStatsProvider.None to R.string.dlstats_none,
        Preferences.DLStatsProvider.IzzyOnDroid to R.string.dlstats_izzyondroid,
    ),
)

val IntPrefsRanges = mapOf(
    Preferences.Key.UpdatedApps to 1..1000,
    Preferences.Key.NewApps to 1..300,
    Preferences.Key.AutoSyncInterval to 1..720,
    Preferences.Key.MaxIdleConnections to 1..32,
    Preferences.Key.MaxParallelDownloads to 1..32,
)

val PrefsDependencies = mapOf(
    Preferences.Key.DownloadDirectory to Pair(
        Preferences.Key.EnableDownloadDirectory,
        listOf(true)
    ),
    Preferences.Key.ActionLockDialog to Pair(
        Preferences.Key.DownloadShowDialog,
        listOf(true)
    ),
)