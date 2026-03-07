/*
 * This file is adapted from Neo Store (https://github.com/NeoApplications/Neo-Store)
 * Modified by Voltual to fit Pyrolysis architecture.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package me.voltual.pyrolysis.core.utils.extension

import android.content.pm.PackageInfo

val PackageInfo.grantedPermissions: Map<String, Boolean>
    get() = requestedPermissions?.mapIndexed { index, perm ->
        Pair(
            perm,
            (requestedPermissionsFlags?.get(index)
                ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED
                    == PackageInfo.REQUESTED_PERMISSION_GRANTED
        )
    }?.toMap().orEmpty()