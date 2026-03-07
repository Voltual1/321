//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

// DataStore 实例
val Context.userFilterDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_filter")

/**
 * 用于存储多个用户筛选信息的 DataStore
 * 
 * 数据结构：
 * - 使用 SET 存储用户ID列表
 * - 每个用户ID对应一个昵称键值对
 * - 维护激活状态的用户ID
 */
@Single
class UserFilterDataStore(private val context: Context) {
    
    companion object {
        // 存储所有用户ID的集合
        private val FILTER_USER_IDS_SET = stringSetPreferencesKey("filter_user_ids_set")
        
        // 用户昵称键前缀
        private const val NICKNAME_KEY_PREFIX = "filter_nickname_"
        
        // 激活状态的用户ID
        private val ACTIVE_FILTER_USER_ID = longPreferencesKey("active_filter_user_id")
        
        // 是否激活筛选
        private val IS_FILTER_ACTIVE = booleanPreferencesKey("is_filter_active")
        
        // 生成用户昵称的键
        private fun getNicknameKey(userId: Long): Preferences.Key<String> {
            return stringPreferencesKey("$NICKNAME_KEY_PREFIX$userId")
        }
    }
    
    /**
     * 添加或更新用户筛选信息
     */
    suspend fun addOrUpdateUserFilter(userId: Long, nickname: String) {
        context.userFilterDataStore.edit { preferences ->
            // 获取现有的用户ID集合
            val existingUserIds = preferences[FILTER_USER_IDS_SET]?.toMutableSet() ?: mutableSetOf()
            
            // 添加新用户ID到集合
            existingUserIds.add(userId.toString())
            preferences[FILTER_USER_IDS_SET] = existingUserIds
            
            // 存储用户昵称
            preferences[getNicknameKey(userId)] = nickname
            
            // 如果当前没有激活的筛选，则将此用户设为激活
            val currentActiveUserId = preferences[ACTIVE_FILTER_USER_ID]
            if (currentActiveUserId == null) {
                preferences[ACTIVE_FILTER_USER_ID] = userId
                preferences[IS_FILTER_ACTIVE] = true
            }
        }
    }
    
    /**
     * 移除用户筛选信息
     */
    suspend fun removeUserFilter(userId: Long) {
        context.userFilterDataStore.edit { preferences ->
            // 获取现有的用户ID集合
            val existingUserIds = preferences[FILTER_USER_IDS_SET]?.toMutableSet() ?: mutableSetOf()
            
            // 从集合中移除用户ID
            existingUserIds.remove(userId.toString())
            preferences[FILTER_USER_IDS_SET] = existingUserIds
            
            // 移除用户昵称
            preferences.remove(getNicknameKey(userId))
            
            // 如果移除的是当前激活的用户
            val currentActiveUserId = preferences[ACTIVE_FILTER_USER_ID]
            if (currentActiveUserId == userId) {
                // 尝试设置另一个用户为激活状态
                val nextUserId = existingUserIds.firstOrNull()?.toLongOrNull()
                preferences[ACTIVE_FILTER_USER_ID] = nextUserId ?: 0L
                preferences[IS_FILTER_ACTIVE] = nextUserId != null
            }
        }
    }
    
    /**
     * 设置激活的用户筛选
     */
    suspend fun setActiveUserFilter(userId: Long?) {
        context.userFilterDataStore.edit { preferences ->
            if (userId != null) {
                // 检查用户是否存在
                val existingUserIds = preferences[FILTER_USER_IDS_SET] ?: setOf()
                if (existingUserIds.contains(userId.toString())) {
                    preferences[ACTIVE_FILTER_USER_ID] = userId
                    preferences[IS_FILTER_ACTIVE] = true
                }
            } else {
                // 清除激活状态
                preferences[ACTIVE_FILTER_USER_ID] = 0L
                preferences[IS_FILTER_ACTIVE] = false
            }
        }
    }
    
    /**
     * 清除所有用户筛选信息
     */
    suspend fun clearAllUserFilters() {
        context.userFilterDataStore.edit { preferences ->
            // 获取所有用户ID以便清除对应的昵称
            val existingUserIds = preferences[FILTER_USER_IDS_SET] ?: setOf()
            
            // 清除所有用户昵称
            existingUserIds.forEach { userIdStr ->
                userIdStr.toLongOrNull()?.let { userId ->
                    preferences.remove(getNicknameKey(userId))
                }
            }
            
            // 清除用户ID集合
            preferences.remove(FILTER_USER_IDS_SET)
            
            // 清除激活状态
            preferences.remove(ACTIVE_FILTER_USER_ID)
            preferences[IS_FILTER_ACTIVE] = false
        }
    }
    
    /**
     * 获取所有用户ID
     */
    val userIdsFlow: Flow<Set<Long>> = context.userFilterDataStore.data
        .map { preferences ->
            preferences[FILTER_USER_IDS_SET]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        }
    
    /**
     * 获取所有用户筛选信息（用户ID和昵称对）
     */
    val allUserFiltersFlow: Flow<Map<Long, String>> = context.userFilterDataStore.data
        .map { preferences ->
            val userIds = preferences[FILTER_USER_IDS_SET] ?: emptySet()
            val result = mutableMapOf<Long, String>()
            
            userIds.forEach { userIdStr ->
                userIdStr.toLongOrNull()?.let { userId ->
                    val nickname = preferences[getNicknameKey(userId)]
                    if (nickname != null) {
                        result[userId] = nickname
                    }
                }
            }
            
            result
        }
    
    /**
     * 获取当前激活的用户ID
     */
    val activeUserIdFlow: Flow<Long?> = context.userFilterDataStore.data
        .map { preferences ->
            val activeUserId = preferences[ACTIVE_FILTER_USER_ID]
            if (activeUserId != null && activeUserId != 0L) activeUserId else null
        }
    
    /**
     * 获取当前激活的用户昵称
     */
    val activeNicknameFlow: Flow<String?> = context.userFilterDataStore.data
        .map { preferences ->
            val activeUserId = preferences[ACTIVE_FILTER_USER_ID]
            if (activeUserId != null && activeUserId != 0L) {
                preferences[getNicknameKey(activeUserId)]
            } else {
                null
            }
        }
    
    /**
     * 获取当前激活的完整用户筛选信息
     */
    val activeUserFilterFlow: Flow<Pair<Long?, String?>> = context.userFilterDataStore.data
        .map { preferences ->
            val activeUserId = preferences[ACTIVE_FILTER_USER_ID]
            if (activeUserId != null && activeUserId != 0L) {
                val nickname = preferences[getNicknameKey(activeUserId)]
                Pair(activeUserId, nickname)
            } else {
                Pair(null, null)
            }
        }
    
    /**
     * 获取筛选是否激活
     */
    val isFilterActiveFlow: Flow<Boolean> = context.userFilterDataStore.data
        .map { preferences ->
            preferences[IS_FILTER_ACTIVE] ?: false
        }
    
    /**
     * 检查指定用户是否存在
     */
    suspend fun hasUserFilter(userId: Long): Boolean {
        val preferences = context.userFilterDataStore.data.first()
        val userIdsSet = preferences[FILTER_USER_IDS_SET]
        return userIdsSet?.contains(userId.toString()) ?: false
    }
    
    /**
     * 获取指定用户的昵称
     */
    suspend fun getNickname(userId: Long): String? {
        val preferences = context.userFilterDataStore.data.first()
        return preferences[getNicknameKey(userId)]
    }
}