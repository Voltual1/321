/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.repository

import me.voltual.pyrolysis.core.database.dao.InstalledDao
import me.voltual.pyrolysis.core.database.dao.ProductDao
import me.voltual.pyrolysis.core.database.entity.EmbeddedProduct
import me.voltual.pyrolysis.core.database.entity.Installed
import me.voltual.pyrolysis.data.entity.Order
import me.voltual.pyrolysis.data.entity.Section
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InstalledRepository(
    private val productsDao: ProductDao,
    private val installedDao: InstalledDao,
) {
    fun get(packageName: String): Flow<Installed?> = installedDao.getFlow(packageName)

    fun getAll(): Flow<List<Installed>> = installedDao.getAllFlow()

    fun getMap(): Flow<Map<String, Installed>> = installedDao.getAllFlow()
        .map { it.associateBy(Installed::packageName) }

    suspend fun load(packageName: String): Installed? = installedDao.get(packageName)

    suspend fun loadUpdatedProducts() = productsDao.queryObject(
        installed = true,
        updates = true,
        section = Section.All,
        order = Order.NAME,
        ascending = true,
    )

    suspend fun loadListWithVulns(repoId: Long): List<EmbeddedProduct> =
        productsDao.getInstalledProductsWithVulnerabilities(repoId)

    suspend fun upsert(vararg installed: Installed) = installedDao.upsert(*installed)

    suspend fun emptyTable() = installedDao.emptyTable()

    suspend fun delete(packageName: String) = installedDao.delete(packageName)
}