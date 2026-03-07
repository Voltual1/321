/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.index

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import me.voltual.pyrolysis.core.database.Converters
import me.voltual.pyrolysis.core.database.entity.IndexProduct
import me.voltual.pyrolysis.core.database.entity.Release
import me.voltual.pyrolysis.core.utils.extension.android.asSequence
import me.voltual.pyrolysis.core.utils.extension.android.execWithResult
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File

class IndexContentMerger(file: File) : Closeable {
    private val db = SQLiteDatabase.openOrCreateDatabase(file, null)

    init {
        db.execWithResult("PRAGMA synchronous = OFF")
        db.execWithResult("PRAGMA journal_mode = OFF")
        db.execSQL("CREATE TABLE product (package_name TEXT PRIMARY KEY, description TEXT NOT NULL, data BLOB NOT NULL)")
        db.execSQL("CREATE TABLE releases (package_name TEXT PRIMARY KEY, data BLOB NOT NULL)")
        db.beginTransaction()
    }

    fun addProducts(products: List<IndexProduct>) {
        for (product in products) {
            val outputStream = ByteArrayOutputStream()
            outputStream.write(product.toJSON().toByteArray())
            db.insert("product", null, ContentValues().apply {
                put("package_name", product.packageName)
                put("description", product.description)
                put("data", outputStream.toByteArray())
            })
        }
    }

    fun addReleases(pairs: List<Pair<String, List<Release>>>) {
        for (pair in pairs) {
            val (packageName, releases) = pair
            val outputStream = ByteArrayOutputStream()
            outputStream.write(Converters.toByteArray(releases))
            db.insert("releases", null, ContentValues().apply {
                put("package_name", packageName)
                put("data", outputStream.toByteArray())
            })
        }
    }

    fun forEach(repositoryId: Long, windowSize: Int, callback: (List<IndexProduct>, Int) -> Unit) {
        closeTransaction()
        db.rawQuery(
            """SELECT product.description, product.data AS pd, releases.data AS rd FROM product
      LEFT JOIN releases ON product.package_name = releases.package_name""", null
        )?.use { cursor ->
            cursor.asSequence().map {
                val description = it.getString(0)
                val product = IndexProduct.fromJson(String(it.getBlob(1))).apply {
                    this.repositoryId = repositoryId
                    this.description = description
                }
                val releases = it.getBlob(2)?.let(Converters::toReleases).orEmpty()
                product.apply {
                    this.releases = releases
                }
            }.windowed(windowSize, windowSize, true)
                .forEach { products -> callback(products, cursor.count) }
        }
    }

    override fun close() {
        db.use { closeTransaction() }
    }

    private fun closeTransaction() {
        if (db.inTransaction()) {
            db.setTransactionSuccessful()
            db.endTransaction()
        }
    }
}