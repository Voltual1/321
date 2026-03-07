/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.repository

import me.voltual.pyrolysis.core.database.dao.CategoryDao
import me.voltual.pyrolysis.core.database.dao.DownloadStatsDao
import me.voltual.pyrolysis.core.database.dao.ProductDao
import me.voltual.pyrolysis.core.database.dao.RepoCategoryDao
import me.voltual.pyrolysis.core.database.entity.CategoryDetails
import me.voltual.pyrolysis.core.database.entity.EmbeddedProduct
import me.voltual.pyrolysis.core.database.entity.Licenses
import me.voltual.pyrolysis.core.database.entity.PackageSum
import me.voltual.pyrolysis.core.database.entity.Product
import me.voltual.pyrolysis.core.database.entity.ProductIconDetails
import me.voltual.pyrolysis.data.entity.Order
import me.voltual.pyrolysis.data.entity.Request
import me.voltual.pyrolysis.data.entity.Section
import me.voltual.pyrolysis.core.utils.extension.text.getIsoDateOfMonthsAgo
import me.voltual.pyrolysis.core.utils.extension.text.isoDateToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class ProductsRepository(
    private val productsDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val repoCategoryDao: RepoCategoryDao,
    private val downloadStatsDao: DownloadStatsDao,
) {
    suspend fun upsertProduct(vararg product: Product) = productsDao.upsert(*product)

    fun getDownloadStatsNotEmpty(): Flow<Boolean> = downloadStatsDao.isNotEmpty()

    fun getProducts(req: Request): Flow<List<EmbeddedProduct>> = productsDao.queryFlowList(req)

    fun getProduct(packageName: String): Flow<List<EmbeddedProduct>> =
        productsDao.getFlow(packageName)

    fun getProductsOfRepo(repoId: Long): Flow<List<EmbeddedProduct>> =
        productsDao.productsForRepositoryFlow(repoId)

    fun getSpecificProducts(pkgs: Set<String>): Flow<List<EmbeddedProduct>> =
        productsDao.queryFlowOfPackages(pkgs)

    fun getRecentTopApps(client: String, numMonths: Int = 3): Flow<List<PackageSum>> =
        downloadStatsDao.getFlowRecentTopApps(
            getIsoDateOfMonthsAgo(numMonths).isoDateToInt(),
            50,
            client
        )

    fun getAllTimeTopApps(): Flow<List<PackageSum>> =
        downloadStatsDao.getFlowRecentTopApps(getIsoDateOfMonthsAgo(100).isoDateToInt(), 50)

    fun getAuthorList(author: String): Flow<List<EmbeddedProduct>> =
        productsDao.getAuthorPackagesFlow(author)

    fun getAllLicenses(): Flow<List<Licenses>> = productsDao.getAllLicensesFlow()

    fun getAllLicensesDistinct(): Flow<List<String>> = productsDao.getAllLicensesFlow()
        .mapLatest {
            it.map(Licenses::licenses).flatten().distinct()
        }

    fun getAllCategories(): Flow<List<String>> = categoryDao.getAllNamesFlow()

    fun getAllCategoryDetails(): Flow<List<CategoryDetails>> =
        repoCategoryDao.getAllCategoryDetailsFlow()

    fun getIconDetailsMap(): Flow<Map<String, ProductIconDetails>> =
        productsDao.getIconDetailsMapFlow()

    suspend fun loadList(
        installed: Boolean,
        updates: Boolean,
        section: Section,
        order: Order,
        ascending: Boolean
    ): List<EmbeddedProduct> = productsDao.queryObject(
        installed = installed,
        updates = updates,
        section = section,
        order = order,
        ascending = ascending,
    )

    suspend fun loadProduct(packageName: String): List<EmbeddedProduct> =
        productsDao.get(packageName)

    suspend fun productExists(packageName: String): Boolean = productsDao.exists(packageName)
}