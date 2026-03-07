/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
@file:Suppress("UNCHECKED_CAST")
package me.voltual.pyrolysis.ui.components.prefs

import androidx.compose.runtime.Composable
import me.voltual.pyrolysis.data.content.Preferences

@Composable
fun PrefsBuilder(
    prefKey: Preferences.Key<*>,
    index: Int,
    size: Int,
    onDialogPref: (Preferences.Key<*>) -> Unit,
) {
    when {
        prefKey.default is Preferences.Value.BooleanValue   -> SwitchPreference(
            prefKey = prefKey as Preferences.Key<Boolean>,
            index = index,
            groupSize = size,
        )

        prefKey is Preferences.Key.Language                 -> LanguagePreference(
            prefKey = prefKey as Preferences.Key<String>,
            index = index,
            groupSize = size,
        ) { onDialogPref(prefKey) }

        prefKey is Preferences.Key.DownloadDirectory        -> LaunchPreference(
            prefKey = prefKey as Preferences.Key<String>,
            index = index,
            groupSize = size,
        ) { onDialogPref(prefKey) }

        prefKey.default is Preferences.Value.StringValue    -> StringPreference(
            prefKey = prefKey as Preferences.Key<String>,
            index = index,
            groupSize = size,
        ) { onDialogPref(prefKey) }

        prefKey.default is Preferences.Value.IntValue       -> IntPreference(
            prefKey = prefKey as Preferences.Key<Int>,
            index = index,
            groupSize = size,
        ) { onDialogPref(prefKey) }

        prefKey.default.value is Preferences.Enumeration<*> -> EnumPreference(
            prefKey = prefKey as Preferences.Key<Preferences.Enumeration<*>>,
            index = index,
            groupSize = size,
        ) { onDialogPref(prefKey) }

        else                                                -> {}
    }
}