//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.


package me.voltual.pyrolysis.core.database

import android.net.Uri
import me.voltual.pyrolysis.BBQApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class PostDraftRepository {
    private val postDraftDao = BBQApplication.instance.database.postDraftDao()

    // 将数据库 Flow 转换为 UI 更易于使用的 Draft DTO Flow
    val draftFlow: Flow<DraftDto?> = postDraftDao.getDraft().map { entity ->
        entity?.let {
            DraftDto(
                title = it.title,
                content = it.content,
                imageUris = it.imageUris.split(",").filter { s -> s.isNotEmpty() }.map { s -> Uri.parse(s) },
                imageUrls = it.imageUrls,
                subsectionId = it.subsectionId
            )
        }
    }

    suspend fun saveDraft(draft: DraftDto) {
        val entity = PostDraft(
            title = draft.title,
            content = draft.content,
            imageUris = draft.imageUris.joinToString(",") { it.toString() },
            imageUrls = draft.imageUrls,
            subsectionId = draft.subsectionId
        )
        postDraftDao.save(entity)
    }

    suspend fun clearDraft() {
        postDraftDao.clear()
    }

    // 一个简单的数据传输对象 (DTO)
    data class DraftDto(
        val title: String,
        val content: String,
        val imageUris: List<Uri>,
        val imageUrls: String,
        val subsectionId: Int
    )
}