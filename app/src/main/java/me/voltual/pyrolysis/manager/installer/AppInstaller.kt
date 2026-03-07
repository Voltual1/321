package me.voltual.pyrolysis.manager.installer

import android.content.Context
import me.voltual.pyrolysis.data.content.Preferences
import me.voltual.pyrolysis.data.entity.InstallerType
import me.voltual.pyrolysis.manager.installer.InstallQueue.Companion.InstallTask
import me.voltual.pyrolysis.manager.installer.type.AppManagerInstaller
import me.voltual.pyrolysis.manager.installer.type.BaseInstaller
import me.voltual.pyrolysis.manager.installer.type.LegacyInstaller
import me.voltual.pyrolysis.manager.installer.type.RootInstaller
import me.voltual.pyrolysis.manager.installer.type.SessionInstaller
import me.voltual.pyrolysis.manager.installer.type.ShizukuInstaller
import me.voltual.pyrolysis.manager.installer.type.SystemInstaller
import me.voltual.pyrolysis.core.utils.amInstalled
import me.voltual.pyrolysis.core.utils.getHasSystemInstallPermission
import me.voltual.pyrolysis.core.utils.hasShizukuOrSui
import me.voltual.pyrolysis.core.utils.hasShizukuPermission
import me.voltual.pyrolysis.core.utils.isShizukuRunning
import me.voltual.pyrolysis.core.utils.shellIsRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Factory class for creating the appropriate installer based on user preferences
 * and device capabilities.
 */
class AppInstaller(private val context: Context) : KoinComponent, InstallationEvents {
    val currentInstaller: StateFlow<BaseInstaller>
        field = MutableStateFlow(create())

    fun recreateInstaller() {
        currentInstaller.update { create() }
    }

    fun isLegacy() = currentInstaller.value is LegacyInstaller
    fun isRoot() = currentInstaller.value is RootInstaller

    fun create(): BaseInstaller {
        val preferredInstaller = Preferences[Preferences.Key.Installer].installer

        return when (preferredInstaller) {
            InstallerType.SYSTEM
                 -> if (context.getHasSystemInstallPermission()) SystemInstaller(context)
            // Fall back to SESSION if NS doesn't have system permission
            else SessionInstaller(context)

            InstallerType.ROOT
                 -> if (shellIsRoot) RootInstaller(context)
            // Fall back to SESSION if root is not available
            else SessionInstaller(context)

            InstallerType.LEGACY
                 -> LegacyInstaller(context)

            InstallerType.AM
                 -> if (context.amInstalled) AppManagerInstaller(context)
            // Fall back to SESSION if AM is not installed
            else SessionInstaller(context)

            InstallerType.SHIZUKU
                 -> if (context.hasShizukuOrSui && hasShizukuPermission() && isShizukuRunning())
                ShizukuInstaller(context)
            // Fall back to SESSION if Shizuku/Sui is not installed, permission not granted or not running
            else SessionInstaller(context)

            else ->
                // Default to SESSION installer
                SessionInstaller(context)
        }
    }

    // BaseInstaller interface
    override suspend fun install(
        packageLabel: String,
        cacheFileName: String,
        postInstall: (Result<String>) -> Unit
    ) = currentInstaller.value.install(packageLabel, cacheFileName, postInstall)

    override suspend fun isEnqueued(packageName: String): Boolean =
        currentInstaller.value.isEnqueued(packageName)

    override suspend fun uninstall(packageName: String) =
        currentInstaller.value.uninstall(packageName)

    override suspend fun cancelInstall(packageName: String) =
        currentInstaller.value.cancelInstall(packageName)

    suspend fun runInstall(task: InstallTask, onFailure: suspend (Throwable) -> Unit) =
        currentInstaller.value.runInstall(task, onFailure)

    fun reportUserInteraction(packageName: String?) =
        currentInstaller.value.reportUserInteraction(packageName)

    fun isInUserInteraction(packageName: String?) =
        currentInstaller.value.isInUserInteraction(packageName)

    suspend fun checkQueueHealth() =
        currentInstaller.value.checkQueueHealth()

    suspend fun reportSuccess(packageName: String) =
        currentInstaller.value.reportSuccess(packageName)

    suspend fun reportFailure(error: InstallationError, packageName: String? = null) =
        currentInstaller.value.reportFailure(error, packageName)
}

val installerModule = module {
    singleOf(::AppInstaller)
    single { InstallQueue() }
}
