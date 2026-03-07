//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。

package me.voltual.pyrolysis.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import kotlinx.coroutines.flow.map

val Context.draftDataStore: DataStore<Preferences> by preferencesDataStore(name = "post_draft")

@Single
class PostDraftDataStore(private val context: Context) {
    companion object {
        private val DRAFT_TITLE = stringPreferencesKey("draft_title")
        private val DRAFT_CONTENT = stringPreferencesKey("draft_content")
        private val DRAFT_SUBSECTION_ID = intPreferencesKey("draft_subsection_id")
        private val HAS_DRAFT = booleanPreferencesKey("has_draft")
        private val DRAFT_IMAGE_URIS = stringPreferencesKey("draft_image_uris")
        private val DRAFT_IMAGE_URLS = stringPreferencesKey("draft_image_urls")        
        private val AUTO_RESTORE_DRAFT = booleanPreferencesKey("auto_restore_draft")
        private val NO_STORE_DRAFT = booleanPreferencesKey("no_store_draft")
    }

    suspend fun saveDraft(
        title: String,
        content: String,
        imageUris: List<Uri>,
        imageUrls: String,
        subsectionId: Int
    ) {
        context.draftDataStore.edit { preferences ->
            preferences[DRAFT_TITLE] = title
            preferences[DRAFT_CONTENT] = content
            preferences[DRAFT_IMAGE_URIS] = imageUris.joinToString(",") { it.toString() }
            preferences[DRAFT_IMAGE_URLS] = imageUrls
            preferences[DRAFT_SUBSECTION_ID] = subsectionId
            preferences[HAS_DRAFT] = true
        }
    }

    suspend fun clearDraft() {
        context.draftDataStore.edit { preferences ->
            preferences.remove(DRAFT_TITLE)
            preferences.remove(DRAFT_CONTENT)
            preferences.remove(DRAFT_IMAGE_URIS)
            preferences.remove(DRAFT_IMAGE_URLS)
            preferences.remove(DRAFT_SUBSECTION_ID)
            preferences[HAS_DRAFT] = false
        }
    }

    suspend fun setAutoRestoreDraft(enabled: Boolean) {
        context.draftDataStore.edit { preferences ->
            preferences[AUTO_RESTORE_DRAFT] = enabled
        }
    }

    suspend fun setNoStoreDraft(enabled: Boolean) {
        context.draftDataStore.edit { preferences ->
            preferences[NO_STORE_DRAFT] = enabled
        }
    }

    val draftFlow: Flow<Draft> = context.draftDataStore.data
        .map { preferences ->
            val uriStrings = preferences[DRAFT_IMAGE_URIS] ?: ""
            val imageUris = uriStrings.split(",")
                .filter { it.isNotEmpty() }
                .map { Uri.parse(it) }
            
            Draft(
                title = preferences[DRAFT_TITLE] ?: "",
                content = preferences[DRAFT_CONTENT] ?: "",
                imageUris = imageUris,
                imageUrls = preferences[DRAFT_IMAGE_URLS] ?: "",
                subsectionId = preferences[DRAFT_SUBSECTION_ID] ?: 11,
                hasDraft = preferences[HAS_DRAFT] ?: false
            )
        }

    val preferencesFlow: Flow<DraftPreferences> = context.draftDataStore.data
        .map { preferences ->
            DraftPreferences(
                autoRestoreDraft = preferences[AUTO_RESTORE_DRAFT] ?: false,
                noStoreDraft = preferences[NO_STORE_DRAFT] ?: false
            )
        }
    
    data class Draft(
        val title: String,
        val content: String,
        val imageUris: List<Uri>,
        val imageUrls: String,
        val subsectionId: Int,
        val hasDraft: Boolean
    )

    // 新增：偏好设置数据类
    data class DraftPreferences(
        val autoRestoreDraft: Boolean,
        val noStoreDraft: Boolean
    )
}