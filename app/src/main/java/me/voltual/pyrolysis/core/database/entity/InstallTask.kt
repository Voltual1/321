package me.voltual.pyrolysis.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import me.voltual.pyrolysis.ROW_ADDED
import me.voltual.pyrolysis.ROW_CACHE_FILE_NAME
import me.voltual.pyrolysis.ROW_PACKAGE_NAME
import me.voltual.pyrolysis.ROW_REPOSITORY_ID
import me.voltual.pyrolysis.ROW_VERSION_CODE
import me.voltual.pyrolysis.TABLE_INSTALL_TASK
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
@Entity(
    tableName = TABLE_INSTALL_TASK,
    primaryKeys = [ROW_PACKAGE_NAME, ROW_REPOSITORY_ID, ROW_VERSION_CODE],
    indices = [
        Index(value = [ROW_PACKAGE_NAME, ROW_REPOSITORY_ID, ROW_VERSION_CODE], unique = true),
        Index(value = [ROW_PACKAGE_NAME]),
        Index(value = [ROW_ADDED]),
        Index(value = [ROW_CACHE_FILE_NAME]),
    ]
)
data class InstallTask(
    val packageName: String,
    val repositoryId: Long,
    val versionCode: Long,
    val versionName: String,
    val label: String,
    val cacheFileName: String,
    val added: Long,
    val requireUser: Boolean,
) {
    val key: String
        get() = "$packageName-$repositoryId-$versionName"

    fun toJSON() = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String) = Json.decodeFromString<InstallTask>(json)
    }
}