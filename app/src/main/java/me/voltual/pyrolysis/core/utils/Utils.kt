/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.utils

import android.Manifest
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import me.voltual.pyrolysis.ui.dialog.LaunchDialog
import androidx.fragment.app.FragmentManager
import android.content.pm.Signature
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import me.voltual.pyrolysis.core.utils.extension.isInstalled
import android.provider.Settings
import me.voltual.pyrolysis.BuildLanguageConfig
import android.text.format.DateUtils
import com.topjohnwu.superuser.Shell
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import me.voltual.pyrolysis.AM_PACKAGENAME
import me.voltual.pyrolysis.AM_PACKAGENAME_DEBUG
import me.voltual.pyrolysis.PREFS_LANGUAGE_DEFAULT
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.data.content.Preferences
import me.voltual.pyrolysis.core.database.entity.EmbeddedProduct
import me.voltual.pyrolysis.core.database.entity.Installed
import me.voltual.pyrolysis.core.database.entity.Product
import me.voltual.pyrolysis.core.database.entity.Release
import me.voltual.pyrolysis.core.database.entity.Repository
import me.voltual.pyrolysis.data.entity.AndroidVersion
import me.voltual.pyrolysis.data.entity.LinkType
import me.voltual.pyrolysis.data.entity.PermissionGroup
import me.voltual.pyrolysis.feature.store.worker.DownloadWorker
import me.voltual.pyrolysis.core.ui.icons.Phosphor
import me.voltual.pyrolysis.core.ui.icons.phosphor.ArrowsClockwise
import me.voltual.pyrolysis.core.ui.icons.phosphor.At
import me.voltual.pyrolysis.core.ui.icons.phosphor.Bug
import me.voltual.pyrolysis.core.ui.icons.phosphor.Copyleft
import me.voltual.pyrolysis.core.ui.icons.phosphor.GlobeSimple
import me.voltual.pyrolysis.core.ui.icons.phosphor.Translate
import me.voltual.pyrolysis.core.ui.icons.phosphor.User
//import me.voltual.pyrolysis.ui.dialog.LaunchDialog
import me.voltual.pyrolysis.core.utils.extension.android.Android
import me.voltual.pyrolysis.core.utils.extension.android.signerSHA256Signatures
import me.voltual.pyrolysis.core.utils.extension.android.versionCodeCompat
import me.voltual.pyrolysis.core.utils.extension.text.hex
import me.voltual.pyrolysis.core.utils.extension.text.nullIfEmpty
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import rikka.sui.Sui

object Utils {    

    fun calculateSHA256(signature: Signature): String {
        return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .hex()
    }
       
    fun calculateSHA256(hexadecString: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(
                hexadecString
                    .chunked(2)
                    .mapNotNull { byteStr ->
                        try {
                            byteStr.toInt(16).toByte()
                        } catch (_: NumberFormatException) {
                            null
                        }
                    }
                    .toByteArray()
            ).hex()
    }
    
    fun translateLocale(locale: Locale): String {
        val country = locale.getDisplayCountry(locale)
        val language = locale.getDisplayLanguage(locale)
        return (language.replaceFirstChar { it.uppercase(Locale.getDefault()) }
                + (if (country.isNotEmpty() && country.compareTo(language, true) != 0)
            "($country)" else ""))
    }
    
    fun startUpdate(
        packageName: String,
        installed: Installed?,
        products: List<Pair<EmbeddedProduct, Repository>>,
    ) {
        val productRepository = findSuggestedProduct(products, installed) { it.first }
        val selectedRelease : Release? = getCompatibleReleases(productRepository, installed)
            .getBestRelease()

        if (productRepository != null && selectedRelease != null) {
            DownloadWorker.enqueue(
                packageName,
                productRepository.first.product.label,
                productRepository.second,
                selectedRelease,
            )
        }
    }
    
    val languagesList: List<String>
        get() {
            val entryVals = arrayOfNulls<String>(1)
            entryVals[0] = PREFS_LANGUAGE_DEFAULT
            return entryVals.plus(BuildLanguageConfig.DETECTED_LOCALES.sorted()).filterNotNull()
        }    
    
    private fun getCompatibleReleases(
        productRepository: Pair<EmbeddedProduct, Repository>?,
        installed: Installed?
    ): List<Release> {
        val includeIncompatible = Preferences[Preferences.Key.IncompatibleVersions]
        val ignoreSigCheck = Preferences[Preferences.Key.DisableSignatureCheck]

        return productRepository?.first?.releases.orEmpty()
            .filter {
                it.repositoryId == productRepository?.second?.id
                        && (includeIncompatible || it.incompatibilities.isEmpty())
                        && (installed == null || it.signature in installed.signatures || ignoreSigCheck)
            }
            .sortedByDescending { it.versionCode }
    }
    
    private fun List<Release>.getBestRelease(): Release? {
        if (isEmpty()) return null
        if (size == 1) return first()

        return filter { it.platforms.contains(Android.primaryPlatform) }
            .minByOrNull { it.platforms.size }
            ?: maxByOrNull { it.platforms.size }
    }     
    
     fun Context.setLanguage(): Configuration {
        var setLocalCode = Preferences[Preferences.Key.Language]
        if (setLocalCode == PREFS_LANGUAGE_DEFAULT) {
            setLocalCode = Locale.getDefault().toString()
        }
        val config = resources.configuration
        val sysLocale = config.locales[0]
        if (setLocalCode != sysLocale.toString() || setLocalCode != "${sysLocale.language}-r${sysLocale.country}") {
            val newLocale = getLocaleOfCode(setLocalCode)
            Locale.setDefault(newLocale)
            config.setLocale(newLocale)
        }
        return config
    }
    
fun Context.getLocaleOfCode(localeCode: String): Locale {
    val defaultLocale = resources.configuration.locales[0]
    if (localeCode.isEmpty()) return defaultLocale

    return try {
        val builder = Locale.Builder()
        when {
            localeCode.contains("-r") -> {
                builder.setLanguage(localeCode.substringBefore("-r"))
                // 仅提取前两个字符作为 Region，防止后续脚本信息干扰
                val region = localeCode.substringAfter("-r").take(2)
                builder.setRegion(region)
            }
            localeCode.contains("_") -> {
                builder.setLanguage(localeCode.substringBefore("_"))
                // 只取下划线后的前两个字母，并确保它们是字母或数字
                val rawRegion = localeCode.substringAfter("_")
                val cleanRegion = rawRegion.filter { it.isLetterOrDigit() }.take(2)
                if (cleanRegion.isNotEmpty()) builder.setRegion(cleanRegion)
            }
            else -> builder.setLanguage(localeCode)
        }
        builder.build()
    } catch (e: Exception) {
        // 发生任何解析错误（如 IllformedLocaleException）时，绝不让 App 崩溃
        defaultLocale
    }
}


    /**
     * Checks if app is currently considered to be in the foreground by Android.
     */
    fun inForeground(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        val importance = appProcessInfo.importance
        return ((importance == IMPORTANCE_FOREGROUND) or (importance == IMPORTANCE_VISIBLE))
    }

private val charactersToBeEscaped = Regex("""[\\${'$'}"`]""")

fun quotePath(parameter: String): String =
        "\"${parameter.replace(charactersToBeEscaped) { "\\${it.value}" }}\""

    
}

fun <T> findSuggestedProduct(
    products: List<T>,
    installed: Installed?,
    extract: (T) -> EmbeddedProduct,
): T? {
    return products.maxWithOrNull(
        compareBy(
            {
                extract(it).compatible && (
                        installed == null ||
                                installed.signatures.intersect(extract(it).productSignatures.toSet())
                                    .isNotEmpty() ||
                                Preferences[Preferences.Key.DisableSignatureCheck]
                        )
            },
            { extract(it).versionCode },
        )
    )
}

fun Context.getLocaleDateString(time: Long): String {
    val date = Date(time)
    val format = if (DateUtils.isToday(date.time)) DateUtils.FORMAT_SHOW_TIME else
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
    return DateUtils.formatDateTime(this, date.time, format)
}

fun Int.dmReasonToHttpResponse() = when (this) {
    DownloadManager.ERROR_UNKNOWN             -> HttpStatusCode.NotImplemented
    DownloadManager.ERROR_FILE_ERROR          -> HttpStatusCode.Conflict
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> HttpStatusCode.NotImplemented
    DownloadManager.ERROR_HTTP_DATA_ERROR     -> HttpStatusCode.BadRequest
    DownloadManager.ERROR_TOO_MANY_REDIRECTS  -> HttpStatusCode.GatewayTimeout
    DownloadManager.ERROR_INSUFFICIENT_SPACE  -> HttpStatusCode.InsufficientStorage
    DownloadManager.ERROR_DEVICE_NOT_FOUND    -> HttpStatusCode.NotFound
    DownloadManager.ERROR_CANNOT_RESUME       -> HttpStatusCode.RequestedRangeNotSatisfiable
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> HttpStatusCode.NotModified
    else                                      -> HttpStatusCode.OK
}

fun Release.generatePermissionGroups(context: Context): Map<PermissionGroup, List<PermissionInfo>> {
    val packageManager = context.packageManager
    return permissions
        .asSequence().mapNotNull {
            try {
                packageManager.getPermissionInfo(it, 0)
            } catch (e: Exception) {
                null
            }
        }
        .groupBy(PackageItemResolver::getPermissionGroup)
}

fun Context.startLauncherActivity(packageName: String, name: String) {
    try {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(packageName, name))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Context.shareIntent(packageName: String, appName: String, repoWebUrl: String) {
    val shareIntent = Intent(Intent.ACTION_SEND)
    val extraText = when {
        repoWebUrl.isNotBlank()
            -> "${repoWebUrl.trimEnd('/')}/$packageName"

        else
            -> "https://f-droid.org/packages/${packageName}/"
    }

    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_TITLE, appName)
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, appName)
    shareIntent.putExtra(Intent.EXTRA_TEXT, extraText)

    startActivity(Intent.createChooser(shareIntent, "Where to Send?"))
}

val Context.installedAM: Intent?
    get() = packageManager.getLaunchIntentForPackage(AM_PACKAGENAME)
        ?: packageManager.getLaunchIntentForPackage(AM_PACKAGENAME_DEBUG)

val Context.amInstalled: Boolean
    get() = installedAM != null
    
fun Context.getHasSystemInstallPermission(): Boolean =
    ActivityCompat.checkSelfPermission(this, Manifest.permission.INSTALL_PACKAGES) ==
            PackageManager.PERMISSION_GRANTED

fun getAndroidVersionName(versionCode: Int): String =
    AndroidVersion.entries.getOrNull(versionCode)?.valueString ?: "Unknown sdk: $versionCode"    
    
fun List<PermissionInfo>.getLabelsAndDescriptions(context: Context): List<String> {
    val localCache = PackageItemResolver.LocalCache()

    return map { permission ->
        val labelFromPackage =
            PackageItemResolver.loadLabel(context, localCache, permission)
        val label = labelFromPackage ?: run {
            val prefixes =
                listOf("android.permission.", "com.android.browser.permission.")
            prefixes.find { permission.name.startsWith(it) }?.let { prefix ->
                val transform = permission.name.substring(prefix.length)
                if (transform.matches("[A-Z_]+".toRegex())) {
                    transform.split("_")
                        .joinToString(separator = " ") { it.lowercase(Locale.US) }
                } else {
                    null
                }
            }
        }
        val description =
            PackageItemResolver.loadDescription(context, localCache, permission)
                ?.nullIfEmpty()?.let { if (it == permission.name) null else it }

        if (description.isNullOrEmpty()) (label ?: permission.name).toString()
        else "${label ?: permission.name}: $description"
    }
}


fun Context.openPermissionPage(packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
        Uri.fromParts("package", packageName, null)
    )
    startActivity(intent)
}

fun virustotalUrl(hash: String): Uri = "https://www.virustotal.com/gui/file/$hash".toUri()

fun Product.generateLinks(context: Context): List<LinkType> {
    val links = mutableListOf<LinkType>()
    if (author.name.isNotEmpty() || author.web.isNotEmpty()) {
        links.add(
            LinkType(
                icon = Phosphor.User,
                title = author.name,
                link = author.web.nullIfEmpty()?.let(String::toUri)
            )
        )
    }
    author.email.nullIfEmpty()?.let {
        links.add(
            LinkType(
                Phosphor.At,
                context.getString(R.string.author_email),
                "mailto:$it".toUri()
            )
        )
    }
    translation.nullIfEmpty()?.let {
        links.add(
            LinkType(
                Phosphor.Translate,
                context.getString(R.string.translation),
                it.toUri()
            )
        )
    }
    links.addAll(licenses.map {
        LinkType(
            Phosphor.Copyleft,
            it,
            "https://spdx.org/licenses/$it.html".toUri()
        )
    })
    tracker.nullIfEmpty()?.let {
        links.add(
            LinkType(
                Phosphor.Bug,
                context.getString(R.string.bug_tracker),
                it.toUri()
            )
        )
    }
    changelog.nullIfEmpty()?.let {
        links.add(
            LinkType(
                Phosphor.ArrowsClockwise,
                context.getString(R.string.changelog),
                it.toUri()
            )
        )
    }
    web.nullIfEmpty()
        ?.let {
            links.add(
                LinkType(
                    Phosphor.GlobeSimple,
                    context.getString(R.string.project_website),
                    it.toUri()
                )
            )
        }
    return links
}

fun Context.isBiometricLockAvailable(): Boolean =
    BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

fun Context.isBiometricLockEnabled(): Boolean =
    isBiometricLockAvailable() &&
            Preferences[Preferences.Key.ActionLockDialog] == Preferences.ActionLock.Biometric

fun Context.shareReleaseIntent(appName: String, address: String) {
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_TITLE, appName)
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, appName)
    shareIntent.putExtra(Intent.EXTRA_TEXT, address)

    startActivity(Intent.createChooser(shareIntent, "Where to share?"))
}

fun Context.shareText(title: String, content: String) {
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_TITLE, title)
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, title)
    shareIntent.putExtra(Intent.EXTRA_TEXT, content)

    startActivity(Intent.createChooser(shareIntent, "Where to share?"))
}

fun Context.onLaunchClick(installed: Installed, fragmentManager: FragmentManager) {
    if (installed.launcherActivities.size >= 2) {
        LaunchDialog(installed.packageName, installed.launcherActivities)
            .show(fragmentManager, LaunchDialog::class.java.name)
    } else {
        installed.launcherActivities.firstOrNull()
            ?.let { startLauncherActivity(installed.packageName, it.first) }
    }
}

val Context.hasShizukuOrSui: Boolean
    get() = Android.sdk(Build.VERSION_CODES.O) &&
            (packageManager.isInstalled(ShizukuProvider.MANAGER_APPLICATION_ID) || Sui.isSui())
            
fun hasShizukuPermission(): Boolean =
    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED            
    
fun isShizukuRunning() = Shizuku.pingBinder()    

val shellIsRoot: Boolean
    get() = Shell.getCachedShell()?.isRoot ?: Shell.getShell().isRoot
    
fun Collection<EmbeddedProduct>.matchSearchQuery(searchQuery: String): List<EmbeddedProduct> {
    if (searchQuery.isBlank()) return toList()
    val now = System.currentTimeMillis()
    return filter {
        listOf(
            it.product.label,
            it.product.packageName,
            it.product.author.name,
            it.product.summary,
            it.product.description
        )
            .any { literal ->
                literal.contains(searchQuery, true)
            }
    }.sortedByDescending {
        (if ("${it.product.label} ${it.product.packageName}".contains(
                searchQuery,
                true
            )
        ) 7 else 0) or
                (if (isDifferenceMoreThanOneYear(it.product.updated, now)) 0 else 3) or
                (if ("${it.product.summary} ${it.product.author.name}".contains(
                        searchQuery,
                        true
                    )
                ) 1 else 0)
    }
}

fun isDifferenceMoreThanOneYear(time1: Long, time2: Long): Boolean {
    val difference = abs(time1 - time2)
    val oneYearInMilliseconds = 365 * 24 * 60 * 60 * 1000L
    return difference > oneYearInMilliseconds
}    