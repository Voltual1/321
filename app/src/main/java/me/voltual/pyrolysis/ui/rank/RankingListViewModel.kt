// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.rank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.voltual.pyrolysis.KtorClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

// 排序类型枚举
enum class SortType(val displayName: String, val apiValue: String) {
    MONEY("硬币排行", "money"),
    EXP("经验排行", "exp")
}

data class RankingListState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val rankingList: List<KtorClient.RankingUser> = emptyList(),
    val page: Int = 1,
    val limit: Int = 15,
    val hasMore: Boolean = true,
    val sortType: SortType = SortType.MONEY // 默认硬币排行
)

@KoinViewModel
class RankingListViewModel : ViewModel() {

    private val _state = MutableStateFlow(RankingListState())
    val state: StateFlow<RankingListState> = _state

    private val apiService = KtorClient.ApiServiceImpl

    init {
        loadRankingList()
    }

    fun loadRankingList() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = apiService.getRankingList(
                    sort = _state.value.sortType.apiValue,
                    sortOrder = "desc",
                    limit = _state.value.limit,
                    page = _state.value.page
                )
                if (result.isSuccess) {
                    val rankingResponse = result.getOrThrow()
                    if (rankingResponse.code == 1) {
                        val rankingUserList = rankingResponse.data
                        // 验证数据完整性
                        val validatedList = rankingUserList.map { user ->
                            // 确保当前排序类型对应的字段有值
                            when (_state.value.sortType) {
                                SortType.MONEY -> {
                                    if (user.money == null) {
                                        user.copy(money = 0)
                                    } else {
                                        user
                                    }
                                }
                                SortType.EXP -> {
                                    if (user.exp == null) {
                                        user.copy(exp = 0)
                                    } else {
                                        user
                                    }
                                }
                            }
                        }
                        _state.value = _state.value.copy(
                            isLoading = false,
                            rankingList = validatedList,
                            hasMore = validatedList.size == _state.value.limit
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = rankingResponse.msg
                        )
                    }
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "未知错误"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "加载异常: ${e.message}"
                )
            }
        }
    }

    fun refreshRankingList() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null, page = 1)
            try {
                val result = apiService.getRankingList(
                    sort = _state.value.sortType.apiValue,
                    sortOrder = "desc",
                    limit = _state.value.limit,
                    page = 1
                )
                if (result.isSuccess) {
                    val rankingResponse = result.getOrThrow()
                    if (rankingResponse.code == 1) {
                        val rankingUserList = rankingResponse.data
                        // 验证数据完整性
                        val validatedList = rankingUserList.map { user ->
                            when (_state.value.sortType) {
                                SortType.MONEY -> {
                                    if (user.money == null) {
                                        user.copy(money = 0)
                                    } else {
                                        user
                                    }
                                }
                                SortType.EXP -> {
                                    if (user.exp == null) {
                                        user.copy(exp = 0)
                                    } else {
                                        user
                                    }
                                }
                            }
                        }
                        _state.value = _state.value.copy(
                            isRefreshing = false,
                            rankingList = validatedList,
                            hasMore = validatedList.size == _state.value.limit,
                            page = 1
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isRefreshing = false,
                            error = rankingResponse.msg
                        )
                    }
                } else {
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        error = result.exceptionOrNull()?.message ?: "未知错误"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = "加载异常: ${e.message}"
                )
            }
        }
    }

    fun loadNextRankingList() {
        if (!_state.value.hasMore || _state.value.isLoading) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val nextPage = _state.value.page + 1
                val result = apiService.getRankingList(
                    sort = _state.value.sortType.apiValue,
                    sortOrder = "desc",
                    limit = _state.value.limit,
                    page = nextPage
                )
                if (result.isSuccess) {
                    val rankingResponse = result.getOrThrow()
                    if (rankingResponse.code == 1) {
                        val rankingUserList = rankingResponse.data
                        // 验证数据完整性
                        val validatedList = rankingUserList.map { user ->
                            when (_state.value.sortType) {
                                SortType.MONEY -> {
                                    if (user.money == null) {
                                        user.copy(money = 0)
                                    } else {
                                        user
                                    }
                                }
                                SortType.EXP -> {
                                    if (user.exp == null) {
                                        user.copy(exp = 0)
                                    } else {
                                        user
                                    }
                                }
                            }
                        }
                        _state.value = _state.value.copy(
                            isLoading = false,
                            rankingList = _state.value.rankingList + validatedList,
                            hasMore = validatedList.size == _state.value.limit,
                            page = nextPage
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = rankingResponse.msg
                        )
                    }
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "未知错误"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "加载异常: ${e.message}"
                )
            }
        }
    }

    // 切换排序方式
    fun changeSortType(sortType: SortType) {
        if (_state.value.sortType != sortType) {
            _state.value = _state.value.copy(
                sortType = sortType,
                page = 1,
                rankingList = emptyList()
            )
            loadRankingList()
        }
    }
}