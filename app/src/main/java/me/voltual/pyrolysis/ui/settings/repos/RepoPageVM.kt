/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.ui.settings.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.core.database.entity.EmbeddedProduct
import me.voltual.pyrolysis.core.database.entity.Repository
import me.voltual.pyrolysis.data.entity.ProductItem
import me.voltual.pyrolysis.feature.store.repository.RepositoriesRepository
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import me.voltual.pyrolysis.data.entity.SyncRequest 
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import me.voltual.pyrolysis.feature.store.worker.*
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import me.voltual.pyrolysis.TAG_BATCH_SYNC_ONETIME

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class RepoPageVM(
    private val reposRepo: RepositoriesRepository,
    private val workManager: WorkManager,
) : ViewModel() {
    private val repoId = MutableStateFlow(0L)

    private val repo = repoId
        .flatMapLatest { reposRepo.getById(it) }
        .distinctUntilChanged()

    private val products = repoId
        .flatMapLatest { reposRepo.getProducts(it) }
        .mapLatest { it.map(EmbeddedProduct::toItem) }

    // 观察 WorkManager 的进度流
    private val syncWorkInfo: Flow<WorkInfo?> = repoId.flatMapLatest { id ->
        val uniqueName = "${TAG_BATCH_SYNC_ONETIME}_$id"
        workManager.getWorkInfosForUniqueWorkLiveData(uniqueName)
            .asFlow()
            .map { list -> 
                // 过滤掉已结束的状态，或者只取最新的
                list.find { !it.state.isFinished } ?: list.firstOrNull() 
            }
    }

    // 将所有流合并到 UI 状态中
    val repoPageState: StateFlow<RepoPageState> = combine(
    repo,
    products,
    syncWorkInfo
) { repo, prods, workInfo ->
    val progressData = workInfo?.progress
    
    RepoPageState(
        repo = repo,
        products = prods.toPersistentList(),
        productsCount = prods.size,
        isSyncing = workInfo?.state == WorkInfo.State.RUNNING,
        // 提取 Worker 中 setProgress 传回的字段
        currentStage = progressData?.getString("progress_stage"),
        stageIndex = progressData?.getInt("progress_index", 0) ?: 0,
        totalStages = progressData?.getInt("total_stages", 0) ?: 0
    )
}.stateIn(viewModelScope, SharingStarted.Lazily, RepoPageState())

    fun setRepo(id: Long) = repoId.update { id }

    fun updateRepo(newValue: Repository?) {
        newValue?.let {
            viewModelScope.launch {
                reposRepo.upsert(it)
            }
        }
    }

    fun syncRepo() {
        val currentRepo = repoPageState.value.repo ?: return
        BatchSyncWorker.enqueue(SyncRequest.MANUAL, setOf(currentRepo.id))
    }
}


data class RepoPageState(
    val repo: Repository? = null,
    val products: PersistentList<ProductItem> = persistentListOf(),
    val productsCount: Int = 0,
    val isSyncing: Boolean = false,
    val currentStage: String? = null, // 例如 "INDEX_FETCH"
    val stageIndex: Int = 0,         // 当前第 1 步
    val totalStages: Int = 0         // 总共 4 步
)