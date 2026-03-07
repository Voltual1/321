/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.feature.store.index

enum class IndexType(
        val jarName: String,
        val contentName: String,
        val certificateFromIndex: Boolean,
    ) {
        INDEX("index.jar", "index.xml", true),
        INDEX_V1("index-v1.jar", "index-v1.json", false),
        INDEX_V2("", "index-v2.json", false),
        INDEX_V2_ENTRY("entry.jar", "entry.json", false),
        INDEX_V2_DIFF("", "TIMESTAMP.json", false),
    }
