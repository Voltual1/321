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
import me.voltual.pyrolysis.FIELD_CACHEFILENAME
import me.voltual.pyrolysis.FIELD_VERSION
import me.voltual.pyrolysis.ROW_CHANGED
import me.voltual.pyrolysis.ROW_PACKAGE_NAME
import me.voltual.pyrolysis.TABLE_DOWNLOADED
import me.voltual.pyrolysis.data.entity.DownloadState

@Entity(
    tableName = TABLE_DOWNLOADED,
    primaryKeys = [ROW_PACKAGE_NAME, FIELD_VERSION, FIELD_CACHEFILENAME],
    indices = [
        Index(value = [ROW_PACKAGE_NAME, FIELD_VERSION, FIELD_CACHEFILENAME], unique = true),
        Index(value = [ROW_PACKAGE_NAME]),
        Index(value = [ROW_CHANGED]),
    ]
)
data class Downloaded(
    val packageName: String = "",
    @ColumnInfo(defaultValue = "")
    val label: String = "",
    val version: String = "",
    @ColumnInfo(defaultValue = "0")
    val repositoryId: Long = 0L,
    val cacheFileName: String = "",
    val changed: Long = 0L,
    val state: DownloadState,
) {
    val itemKey: String
        get() = "$packageName-$repositoryId-$version-$cacheFileName"
}