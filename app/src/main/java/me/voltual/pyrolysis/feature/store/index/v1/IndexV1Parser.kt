/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package me.voltual.pyrolysis.feature.store.index.v1

import me.voltual.pyrolysis.core.database.entity.IndexProduct
import me.voltual.pyrolysis.core.database.entity.Release
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

class IndexV1Parser(private val repositoryId: Long, private val callback: Callback) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun parse(inputStream: InputStream) {
        try {
            val indexV1 = json.decodeFromStream<IndexV1>(inputStream)

            with(indexV1.repo) {
                callback.onRepository(
                    mirrors = listOf(address).plus(mirrors).distinct(),
                    name = name,
                    description = description,
                    version = version,
                    timestamp = timestamp
                )
            }

            indexV1.apps.forEach { product ->
                callback.onProduct(product.toProduct(repositoryId))
            }

            indexV1.packages.forEach { (packageName, releases) ->
                callback.onReleases(
                    packageName,
                    releases.map { it.toRelease(repositoryId, packageName) },
                )
            }
        } catch (e: Exception) {
            throw ParsingException("Error parsing index", e)
        }
    }

    interface Callback {
        fun onRepository(
            mirrors: List<String>,
            name: String,
            description: String,
            version: Int,
            timestamp: Long,
        )

        fun onProduct(product: IndexProduct)
        fun onReleases(packageName: String, releases: List<Release>)
    }

    class ParsingException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
