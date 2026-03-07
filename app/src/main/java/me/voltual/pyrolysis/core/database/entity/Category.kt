/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import me.voltual.pyrolysis.ROW_NAME
import me.voltual.pyrolysis.ROW_PACKAGE_NAME
import me.voltual.pyrolysis.ROW_REPOSITORY_ID
import me.voltual.pyrolysis.TABLE_CATEGORY
import me.voltual.pyrolysis.TABLE_CATEGORY_TEMP

@Entity(
    tableName = TABLE_CATEGORY,
    primaryKeys = [ROW_REPOSITORY_ID, ROW_PACKAGE_NAME, ROW_NAME],
    indices = [
        Index(value = [ROW_REPOSITORY_ID, ROW_PACKAGE_NAME, ROW_NAME], unique = true),
        Index(value = [ROW_PACKAGE_NAME, ROW_NAME]),
        Index(value = [ROW_NAME]),
        Index(value = [ROW_REPOSITORY_ID]),
    ]
)
open class Category(
    val repositoryId: Long = 0,
    val packageName: String = "",
    val name: String = "",
)

@Entity(tableName = TABLE_CATEGORY_TEMP,inheritSuperIndices = true)
class CategoryTemp(repositoryId: Long, packageName: String, name: String) :
    Category(repositoryId, packageName, name)