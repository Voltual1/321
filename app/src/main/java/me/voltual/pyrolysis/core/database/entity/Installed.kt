/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.voltual.pyrolysis.ROW_PACKAGE_NAME
import me.voltual.pyrolysis.TABLE_INSTALLED

@Entity(
    tableName = TABLE_INSTALLED,
    indices = [
        Index(value = [ROW_PACKAGE_NAME], unique = true)
    ]
)
data class Installed(
    @PrimaryKey
    val packageName: String = "",
    val version: String = "",
    val versionCode: Long = 0L,
    @ColumnInfo(defaultValue = "[]")
    val signatures: List<String> = emptyList(),
    val isSystem: Boolean = false,
    val launcherActivities: List<Pair<String, String>> = emptyList()
)