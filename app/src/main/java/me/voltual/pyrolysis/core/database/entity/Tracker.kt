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
import androidx.room.PrimaryKey
import me.voltual.pyrolysis.ROW_KEY
import me.voltual.pyrolysis.TABLE_TRACKER
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

@Entity(
    tableName = TABLE_TRACKER,
    indices = [
        Index(value = [ROW_KEY], unique = true)
    ]
)
data class Tracker(
    @PrimaryKey
    val key: Int = 0,
    override val name: String = String(),
    override val network_signature: String = String(),
    override val code_signature: String = String(),
    override val creation_date: String = String(),
    override val website: String = String(),
    override val description: String = String(),
    override val categories: List<String> = emptyList(),
    override val documentation: List<String> = emptyList(),
) : TrackerData(
    name,
    network_signature,
    code_signature,
    creation_date,
    website,
    description,
    categories,
    documentation,
)

@Serializable
open class TrackerData(
    open val name: String = String(),
    open val network_signature: String = String(),
    open val code_signature: String = String(),
    open val creation_date: String = String(),
    open val website: String = String(),
    open val description: String = String(),
    open val categories: List<String> = emptyList(),
    open val documentation: List<String> = emptyList(),
)

@Serializable
data class Trackers(
    val trackers: Map<String, TrackerData> = emptyMap(),
) {
    fun toJSON() = Json.encodeToString(this)

    companion object {
        private val jsonConfig = Json { ignoreUnknownKeys = true }
        fun fromJson(json: String) = jsonConfig.decodeFromString<Trackers>(json)

        @OptIn(ExperimentalSerializationApi::class)
        fun fromStream(inst: InputStream) = jsonConfig.decodeFromStream<Trackers>(inst)
    }
}