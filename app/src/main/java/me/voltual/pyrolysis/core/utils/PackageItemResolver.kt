/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.utils

import android.content.Context
import android.content.pm.PackageItemInfo
import android.content.pm.PermissionInfo
import android.content.res.Resources
import android.os.Build
import me.voltual.pyrolysis.CALENDAR_PERMISSIONS
import me.voltual.pyrolysis.CAMERA_PERMISSIONS
import me.voltual.pyrolysis.CONTACTS_PERMISSIONS
import me.voltual.pyrolysis.INTERNET_PERMISSIONS
import me.voltual.pyrolysis.LOCATION_PERMISSIONS
import me.voltual.pyrolysis.MICROPHONE_PERMISSIONS
import me.voltual.pyrolysis.NEARBY_DEVICES_PERMISSIONS
import me.voltual.pyrolysis.PHONE_PERMISSIONS
import me.voltual.pyrolysis.SMS_PERMISSIONS
import me.voltual.pyrolysis.STORAGE_PERMISSIONS
import me.voltual.pyrolysis.data.entity.PermissionGroup
import me.voltual.pyrolysis.data.entity.PermissionGroup.Companion.getPermissionGroup
import me.voltual.pyrolysis.core.utils.extension.android.Android
import java.util.Locale

object PackageItemResolver {
    class LocalCache {
        internal val resources = mutableMapOf<String, Resources>()
    }

    private data class CacheKey(val locales: List<Locale>, val packageName: String, val resId: Int)

    private val cache = mutableMapOf<CacheKey, String?>()

    private fun load(
        context: Context, localCache: LocalCache, packageName: String,
        nonLocalized: CharSequence?, resId: Int,
    ): CharSequence? {
        return when {
            nonLocalized != null -> {
                nonLocalized
            }

            resId != 0           -> {
                val localesList = context.resources.configuration.locales
                val locales = (0 until localesList.size()).map(localesList::get)
                val cacheKey = CacheKey(locales, packageName, resId)
                if (cache.containsKey(cacheKey)) {
                    cache[cacheKey]
                } else {
                    val resources = localCache.resources[packageName] ?: run {
                        val resources = try {
                            val resources =
                                context.packageManager.getResourcesForApplication(packageName)
                            @Suppress("DEPRECATION")
                            resources.updateConfiguration(context.resources.configuration, null)
                            resources
                        } catch (e: Exception) {
                            null
                        }
                        resources?.let { localCache.resources[packageName] = it }
                        resources
                    }
                    val label = resources?.getString(resId)
                    cache[cacheKey] = label
                    label
                }
            }

            else                 -> {
                null
            }
        }
    }

    fun loadLabel(
        context: Context,
        localCache: LocalCache,
        packageItemInfo: PackageItemInfo,
    ): CharSequence? {
        return load(
            context, localCache, packageItemInfo.packageName,
            packageItemInfo.nonLocalizedLabel, packageItemInfo.labelRes
        )
    }

    fun loadDescription(
        context: Context,
        localCache: LocalCache,
        permissionInfo: PermissionInfo,
    ): CharSequence? {
        return load(
            context, localCache, permissionInfo.packageName,
            permissionInfo.nonLocalizedDescription, permissionInfo.descriptionRes
        )
    }

    fun getPermissionGroup(permissionInfo: PermissionInfo): PermissionGroup {
        return if (Android.sdk(Build.VERSION_CODES.Q)) {
            when (permissionInfo.name) {
                in CONTACTS_PERMISSIONS       -> PermissionGroup.Contacts
                in CALENDAR_PERMISSIONS       -> PermissionGroup.Calendar
                in SMS_PERMISSIONS            -> PermissionGroup.SMS
                in STORAGE_PERMISSIONS        -> PermissionGroup.Storage
                in PHONE_PERMISSIONS          -> PermissionGroup.Phone
                in LOCATION_PERMISSIONS       -> PermissionGroup.Location
                in MICROPHONE_PERMISSIONS     -> PermissionGroup.Microphone
                in CAMERA_PERMISSIONS         -> PermissionGroup.Camera
                in NEARBY_DEVICES_PERMISSIONS -> PermissionGroup.NearbyDevices
                in INTERNET_PERMISSIONS       -> PermissionGroup.Internet
                else                          -> PermissionGroup.Other
            }
        } else {
            permissionInfo.group?.getPermissionGroup() ?: PermissionGroup.Other
        }
    }
}
