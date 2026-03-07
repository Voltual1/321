/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.utils

import android.content.pm.PermissionInfo
import me.voltual.pyrolysis.HIGH_RISK_PERMISSIONS
import me.voltual.pyrolysis.LOW_RISK_PERMISSIONS
import me.voltual.pyrolysis.MEDIUM_RISK_PERMISSIONS
import me.voltual.pyrolysis.NON_FREE_COUNTRIES_TRACKERS
import me.voltual.pyrolysis.WIDESPREAD_TRACKERS
import me.voltual.pyrolysis.core.database.entity.AntiFeatureDetails
import me.voltual.pyrolysis.core.database.entity.Tracker
import me.voltual.pyrolysis.data.entity.AntiFeature
import me.voltual.pyrolysis.data.entity.PermissionGroup
import me.voltual.pyrolysis.data.entity.PrivacyData
import me.voltual.pyrolysis.data.entity.PrivacyNote
import me.voltual.pyrolysis.data.entity.SourceType

fun PrivacyData.toPrivacyNote(): PrivacyNote {
    val permissionsNote = 100 - permissions.privacyPoints.coerceAtMost(100)
    val trackersNote = 100 - trackers.privacyPoints.coerceAtMost(100)
    val sourceType = SourceType(
        open = !antiFeatures.map(AntiFeatureDetails::name)
            .contains(AntiFeature.NO_SOURCE_SINCE.key),
        free = !antiFeatures.any {
            it.name == AntiFeature.NON_FREE_NET.key ||
                    it.name == AntiFeature.NON_FREE_UPSTREAM.key
        },
        independent = !antiFeatures.any {
            it.name == AntiFeature.NON_FREE_DEP.key ||
                    it.name == AntiFeature.NON_FREE_ASSETS.key
        }
    )
    return PrivacyNote(
        permissionsNote,
        trackersNote,
        sourceType
    )
}

private val String.trackerCategoryNote: Int
    get() = when (this) {
        "Ads", "Profiling", "Location" -> 20
        "Analytics", "Identification"  -> 10
        "Crash reporting"              -> 5
        else                           -> 1
    }

private val Int.trackerNoteMultiplicator: Int
    get() = when (this) {
        in WIDESPREAD_TRACKERS,
        in NON_FREE_COUNTRIES_TRACKERS,
             -> 2

        else -> 1
    }

val Map<PermissionGroup, List<PermissionInfo>>.privacyPoints
    get() = values.flatten().sumOf {
        when (it.name) {
            in HIGH_RISK_PERMISSIONS   -> 15
            in MEDIUM_RISK_PERMISSIONS -> 7
            in LOW_RISK_PERMISSIONS    -> 3
            else                       -> 2
        }.toInt()
    }

val List<Tracker>.privacyPoints
    get() = sumOf {
        it.categories.sumOf(String::trackerCategoryNote) * it.key.trackerNoteMultiplicator
    }