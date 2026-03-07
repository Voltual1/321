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
import me.voltual.pyrolysis.ROW_FILE_NAME
import me.voltual.pyrolysis.ROW_LAST_MODIFIED
import me.voltual.pyrolysis.TABLE_DOWNLOAD_STATS_FILE_METADATA

@Entity(
    tableName = TABLE_DOWNLOAD_STATS_FILE_METADATA,
    indices = [
        Index(value = [ROW_FILE_NAME], unique = true),
        Index(value = [ROW_FILE_NAME, ROW_LAST_MODIFIED]),
    ]
)
data class DownloadStatsFileMetadata(
    @PrimaryKey
    @ColumnInfo(name = ROW_FILE_NAME)
    val fileName: String,
    @ColumnInfo(name = ROW_LAST_MODIFIED)
    val lastModified: String,
    val lastFetched: Long = System.currentTimeMillis(),
    val fetchSuccess: Boolean = true,
    val fileSize: Long? = null,
    val recordsCount: Int? = null,
)