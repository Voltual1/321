/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package me.voltual.pyrolysis.feature.store.repository

import me.voltual.pyrolysis.core.database.dao.AntiFeatureDao
import me.voltual.pyrolysis.core.database.dao.ProductDao
import me.voltual.pyrolysis.core.database.dao.RepositoryDao
import me.voltual.pyrolysis.core.database.entity.AntiFeatureDetails
import me.voltual.pyrolysis.core.database.entity.EmbeddedProduct
import me.voltual.pyrolysis.core.database.entity.LatestSyncs
import me.voltual.pyrolysis.core.database.entity.Repository
import me.voltual.pyrolysis.data.entity.AntiFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

class RepositoriesRepository(
    private val productsDao: ProductDao,
    private val reposDao: RepositoryDao,
    private val antiFeatureDao: AntiFeatureDao,
) {
    fun getById(repoId: Long): Flow<Repository?> = reposDao.getFlow(repoId)

    fun getAll(): Flow<List<Repository>> = reposDao.getAllFlow()

    fun getAllMap(): Flow<Map<Long, Repository>> = reposDao.getAllMapFlow()

    fun getAllEnabled(): Flow<List<Repository>> = reposDao.getAllEnabledFlow()

    fun getLatestUpdates(): Flow<LatestSyncs> = reposDao.latestUpdatesFlow()

    fun productsCount(repoId: Long): Flow<Long> = productsDao.countForRepositoryFlow(repoId)

    fun getProducts(repoId: Long): Flow<List<EmbeddedProduct>> = productsDao.productsForRepositoryFlow(repoId)

    fun getRepoAntiFeatures(): Flow<List<AntiFeatureDetails>> =
        antiFeatureDao.getAllAntiFeatureDetailsFlow()

    fun getRepoAntiFeaturesMap(): Flow<Map<String, AntiFeatureDetails>> =
        antiFeatureDao.getAllAntiFeatureDetailsFlow()
            .mapLatest {
                it.associateBy(AntiFeatureDetails::name)
            }

    fun getRepoAntiFeaturePairs(): Flow<List<Pair<String, String>>> =
        antiFeatureDao.getAllAntiFeatureDetailsFlow()
            .mapLatest { afs ->
                val detailsMap = afs.associateBy(AntiFeatureDetails::name)
                val enumMap = AntiFeature.entries.associateBy(AntiFeature::key)
                (detailsMap.keys + enumMap.keys).map { name ->
                    detailsMap[name]?.let { Pair(it.name, it.label) } ?: Pair(name, "")
                }
            }

    suspend fun load(repoId: Long): Repository? = reposDao.get(repoId)

    suspend fun loadAll(): List<Repository> = reposDao.getAll()

    suspend fun upsert(repo: Repository) = reposDao.put(repo)

    suspend fun insertReturn(repository: Repository): Long = reposDao.insertReturn(repository)

    suspend fun insertOrUpdate(vararg repository: Repository) = reposDao.insertOrUpdate(*repository)

    suspend fun isDuplicateAddress(address: String) = reposDao.isDuplicateAddress(address)

    suspend fun deleteById(repoId: Long) = reposDao.deleteById(repoId)
}