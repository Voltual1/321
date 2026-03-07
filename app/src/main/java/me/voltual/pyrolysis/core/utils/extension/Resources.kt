/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.utils.extension.resources

import android.content.Context
import android.content.res.ColorStateList
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat

fun Context.getColorFromAttr(@AttrRes attrResId: Int): ColorStateList {
    val typedArray = obtainStyledAttributes(intArrayOf(attrResId))
    val (colorStateList, resId) = try {
        Pair(typedArray.getColorStateList(0), typedArray.getResourceId(0, 0))
    } finally {
        typedArray.recycle()
    }
    return colorStateList ?: ContextCompat.getColorStateList(this, resId)!!
}
