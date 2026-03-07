/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis

object BuildLanguageConfig {
    val DETECTED_LOCALES: Array<String> = arrayOf("zh") // 手动写死目前有的语言
    const val KEY_API_EXODUS: String = "81f30e4903bde25023857719e71c94829a41e6a5"
}