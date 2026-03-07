/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.database.dao

import androidx.room.Dao
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Transaction
import me.voltual.pyrolysis.ROW_FILE_NAME
import me.voltual.pyrolysis.ROW_LAST_MODIFIED
import me.voltual.pyrolysis.core.database.entity.DownloadStatsFileMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadStatsFileDao : BaseDao<DownloadStatsFileMetadata> {

    @Query("SELECT * FROM ds_file_meta_data ORDER BY fileName")
    fun getAllFlow(): Flow<List<DownloadStatsFileMetadata>>

    @Query("SELECT * FROM ds_file_meta_data ORDER BY fileName")
    suspend fun getAll(): List<DownloadStatsFileMetadata>

    @Query("SELECT $ROW_FILE_NAME, $ROW_LAST_MODIFIED FROM ds_file_meta_data WHERE fetchSuccess = 1")
    suspend fun getLastModifiedDates(): Map<
            @MapColumn(columnName = ROW_FILE_NAME) String,
            @MapColumn(columnName = ROW_LAST_MODIFIED) String
            >

    @Transaction
    suspend fun multipleUpserts(updates: Collection<DownloadStatsFileMetadata>) {
        updates.forEach { metadata ->
            upsert(metadata)
        }
    }

    @Query("DELETE FROM ds_file_meta_data WHERE $ROW_FILE_NAME NOT IN (:activeFileNames)")
    suspend fun deleteObsoleteFiles(activeFileNames: List<String>)

    @Query("DELETE FROM ds_file_meta_data")
    suspend fun emptyTable()
}