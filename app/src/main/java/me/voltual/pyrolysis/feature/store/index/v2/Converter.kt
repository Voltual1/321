/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.index.v2

import android.content.res.Resources
import androidx.core.os.ConfigurationCompat.getLocales
import androidx.core.os.LocaleListCompat
import me.voltual.pyrolysis.core.database.entity.IndexProduct
import me.voltual.pyrolysis.core.database.entity.Release
import me.voltual.pyrolysis.data.entity.Author
import me.voltual.pyrolysis.data.entity.Donate
import me.voltual.pyrolysis.feature.store.index.v0.IndexV0Parser
import me.voltual.pyrolysis.feature.store.index.v2.IndexV2.File
import me.voltual.pyrolysis.core.utils.extension.android.Android
import java.util.Locale

internal fun IndexV2.Package.toProduct(repositoryId: Long, packageName: String) = IndexProduct(
    repositoryId = repositoryId,
    packageName = packageName,
    label = metadata.name.findLocalized(packageName),
    summary = metadata.summary.findLocalized(""),
    description = metadata.description.findLocalized("")
        .removeSurrounding("\n")
        .replace("\n", "<br/>"),
    added = metadata.added,
    updated = metadata.lastUpdated,
    icon = metadata.icon.findLocalized(File("")).name,
    metadataIcon = metadata.icon.findLocalized(File("")).name,
    releases = emptyList(),
    categories = metadata.categories,
    antiFeatures = versions.entries.maxBy { it.value.added }.value.antiFeatures.keys.toList(),
    licenses = metadata.license.split(',').filter { it.isNotEmpty() },
    donates = listOfNotNull(
        *metadata.donate.map { Donate.Regular(it) }.toTypedArray(),
        metadata.bitcoin?.let { Donate.Bitcoin(it) },
        metadata.openCollective?.let { Donate.OpenCollective(it) },
        metadata.liberapay?.let { Donate.Liberapay(it) },
        metadata.litecoin?.let { Donate.Litecoin(it) },
    )
        .sortedWith(IndexV0Parser.DonateComparator),
    screenshots = with(metadata.screenshots) {
        listOfNotNull(this?.phone, this?.sevenInch, this?.tenInch, this?.wear, this?.tv)
            .fold(mutableMapOf<String, List<File>>()) { acc, map ->
                map.forEach { (key, value) ->
                    acc.merge(key, value) { oldList, newList ->
                        oldList + newList
                    }
                }
                acc
            }
            .findLocalized(emptyList())
            .map(File::name)
    },
    suggestedVersionCode = 0L,
    author = Author(
        metadata.authorName.orEmpty(),
        metadata.authorEmail.orEmpty(),
        metadata.authorWebsite.orEmpty()
    ),
    source = metadata.sourceCode.orEmpty(),
    web = metadata.webSite.orEmpty(),
    video = metadata.video.findLocalized(""),
    tracker = metadata.issueTracker.orEmpty(),
    translation = metadata.translation.orEmpty(),
    changelog = metadata.changelog.orEmpty(),
    // TODO convert usage to Map<VersionCode,Changelog>
    whatsNew = versions.entries
        .maxBy { it.value.added }.value
        .whatsNew.findLocalized(""),
)

internal fun IndexV2.Version.toRelease(
    repositoryId: Long,
    packageName: String,
) = Release(
    packageName = packageName,
    repositoryId = repositoryId,
    selected = false,
    version = manifest.versionName,
    versionCode = manifest.versionCode,
    added = added,
    size = file.size ?: 0L,
    minSdkVersion = manifest.usesSdk?.minSdkVersion ?: 0,
    targetSdkVersion = manifest.usesSdk?.targetSdkVersion ?: 0,
    maxSdkVersion = 0,
    source = src?.name.orEmpty(),
    release = file.name.removePrefix("/"),
    hash = file.sha256.orEmpty(),
    hashType = "SHA-256",
    signature = manifest.signer?.sha256?.first().orEmpty(),
    obbMain = "",
    obbMainHash = "",
    obbMainHashType = "",
    obbPatch = "",
    obbPatchHash = "",
    obbPatchHashType = "",
    permissions = manifest.usesPermission.orEmpty().plus(manifest.usesPermissionSdk23.orEmpty())
        .mapNotNull {
            it.takeIf { it.name.isNotEmpty() && (it.maxSdkVersion <= 0 || Android.sdk <= it.maxSdkVersion) }
                ?.name
        },
    features = emptyList(),
    platforms = manifest.nativecode,
    incompatibilities = emptyList(),
    isCompatible = true,
    releaseChannels = releaseChannels,
)

internal fun <T> Localized<T>?.findLocalized(fallback: T): T =
    getBestLocale(getLocales(Resources.getSystem().configuration)) ?: fallback

private fun <T> Localized<T>?.getBestLocale(localeList: LocaleListCompat): T? {
    if (isNullOrEmpty()) return null
    val defLocale = Locale.getDefault()
    val defTag = defLocale.toLanguageTag()
    val sysLocaleMatch = localeList.getFirstMatch(keys.toTypedArray()) ?: return null
    val sysTag = sysLocaleMatch.toLanguageTag()
    // try the user-set default language
    return entries.find { it.key == defTag }?.value
        ?: run {
            // split away stuff like script and try language and region only
            val langCountryTag = "${defLocale.language}-${defLocale.country}"
            (getOrStartsWith(langCountryTag) ?: run {
                // split away region tag and try language only
                val langTag = defLocale.language
                // try language, then English and then just take the first of the list
                if (langTag == "en") get("en-US") ?: get("en-GB") ?: getOrStartsWith(langTag)
                else getOrStartsWith(langTag)
            })
        }
        // now try first matched system tag (usually has region tag, e.g. de-DE)
        ?: entries.find { it.key == sysTag }?.value
        ?: run {
            // split away stuff like script and try language and region only
            val langCountryTag = "${sysLocaleMatch.language}-${sysLocaleMatch.country}"
            (getOrStartsWith(langCountryTag) ?: run {
                // split away region tag and try language only
                val langTag = sysLocaleMatch.language
                // try language, then English and then just take the first of the list
                getOrStartsWith(langTag)
                    ?: get("en-US")
                    ?: get("en-GB")
                    ?: getOrStartsWith("en")
                    ?: entries.first().value
            })
        }
}

private fun <T> Map<String, T>.getOrStartsWith(s: String): T? =
    entries.find { it.key == s }?.value ?: run {
        entries.forEach { entry ->
            if (entry.key.startsWith(s)) return entry.value
        }
        return null
    }
