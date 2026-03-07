package me.voltual.pyrolysis.data.entity

import android.content.pm.PermissionInfo
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.machiav3lli.backup.ui.compose.icons.phosphor.GitPullRequest
import me.voltual.pyrolysis.IDENTIFICATION_DATA_PERMISSIONS
import me.voltual.pyrolysis.PERMISSION_GROUP_INTERNET
import me.voltual.pyrolysis.PHYSICAL_DATA_PERMISSIONS
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.core.database.entity.AntiFeatureDetails
import me.voltual.pyrolysis.core.database.entity.Tracker
import me.voltual.pyrolysis.core.ui.icons.Icon
import me.voltual.pyrolysis.core.ui.icons.Phosphor
import me.voltual.pyrolysis.core.ui.icons.icon.Opensource
import me.voltual.pyrolysis.core.ui.icons.phosphor.AddressBook
import me.voltual.pyrolysis.core.ui.icons.phosphor.Broadcast
import me.voltual.pyrolysis.core.ui.icons.phosphor.Bug
import me.voltual.pyrolysis.core.ui.icons.phosphor.Calendar
import me.voltual.pyrolysis.core.ui.icons.phosphor.Camera
import me.voltual.pyrolysis.core.ui.icons.phosphor.ChartLine
import me.voltual.pyrolysis.core.ui.icons.phosphor.Chat
import me.voltual.pyrolysis.core.ui.icons.phosphor.Copyleft
import me.voltual.pyrolysis.core.ui.icons.phosphor.Copyright
import me.voltual.pyrolysis.core.ui.icons.phosphor.CurrencyCircleDollar
import me.voltual.pyrolysis.core.ui.icons.phosphor.EyeSlash
import me.voltual.pyrolysis.core.ui.icons.phosphor.FolderNotch
import me.voltual.pyrolysis.core.ui.icons.phosphor.Globe
import me.voltual.pyrolysis.core.ui.icons.phosphor.MapPin
import me.voltual.pyrolysis.core.ui.icons.phosphor.Microphone
import me.voltual.pyrolysis.core.ui.icons.phosphor.Phone
import me.voltual.pyrolysis.core.ui.icons.phosphor.ShieldStar
import me.voltual.pyrolysis.core.ui.icons.phosphor.User
import me.voltual.pyrolysis.core.ui.icons.phosphor.UserFocus
import kotlin.math.truncate

class PrivacyData(
    val permissions: Map<PermissionGroup, List<PermissionInfo>> = emptyMap(),
    val trackers: List<Tracker> = emptyList(),
    val antiFeatures: List<AntiFeatureDetails> = emptyList(),
) {
    val physicalDataPermissions: Map<PermissionGroup, List<PermissionInfo>>
        get() = permissions.filter { it.key in PHYSICAL_DATA_PERMISSIONS }
    val identificationDataPermissions: Map<PermissionGroup, List<PermissionInfo>>
        get() = permissions.filter { it.key in IDENTIFICATION_DATA_PERMISSIONS }
    val otherPermissions: Map<PermissionGroup, List<PermissionInfo>>
        get() = permissions.filterNot { it.key in (PHYSICAL_DATA_PERMISSIONS + IDENTIFICATION_DATA_PERMISSIONS) }

}

// TODO rename or merge into PrivacyData
class PrivacyNote(
    val permissionsNote: Int = 100,
    val trackersNote: Int = 100,
    val sourceType: SourceType = SourceType(),
) {
    val trackersRank
        get() = truncate((trackersNote - 1) / 20f).toInt()
    val permissionsRank
        get() = truncate((permissionsNote - 1) / 20f).toInt()
}

class SourceType(
    val open: Boolean = true,
    val free: Boolean = true,
    val independent: Boolean = true,
) {
    val isFree: Boolean
        get() = open && free && independent
    val isOpenSource: Boolean
        get() = open && independent
    val isSourceAvailable: Boolean
        get() = open
}

open class PermissionGroup(
    val name: String,
    @param:StringRes val labelId: Int,
    val icon: ImageVector,
) {
    object Contacts : PermissionGroup(
        android.Manifest.permission_group.CONTACTS,
        R.string.permission_contacts,
        Phosphor.AddressBook
    )

    object Calendar : PermissionGroup(
        android.Manifest.permission_group.CALENDAR,
        R.string.permission_calendar,
        Phosphor.Calendar
    )

    object SMS : PermissionGroup(
        android.Manifest.permission_group.SMS,
        R.string.permission_sms,
        Phosphor.Chat
    )

    object Storage : PermissionGroup(
        android.Manifest.permission_group.STORAGE,
        R.string.permission_storage,
        Phosphor.FolderNotch
    )

    object Phone : PermissionGroup(
        android.Manifest.permission_group.PHONE,
        R.string.permission_phone,
        Phosphor.Phone
    )

    object Location : PermissionGroup(
        android.Manifest.permission_group.LOCATION,
        R.string.permission_location,
        Phosphor.MapPin
    )

    object Camera : PermissionGroup(
        android.Manifest.permission_group.CAMERA,
        R.string.permission_camera,
        Phosphor.Camera
    )

    object Microphone : PermissionGroup(
        android.Manifest.permission_group.MICROPHONE,
        R.string.permission_microphone,
        Phosphor.Microphone
    )

    object NearbyDevices : PermissionGroup(
        android.Manifest.permission_group.NEARBY_DEVICES,
        R.string.permission_nearby_devices,
        Phosphor.Broadcast
    )

    object Internet : PermissionGroup(
        PERMISSION_GROUP_INTERNET,
        R.string.permission_internet,
        Phosphor.Globe
    )

    object Other : PermissionGroup(
        "",
        R.string.permission_other,
        Phosphor.ShieldStar
    )

    companion object {
        fun String.getPermissionGroup() = when (this) {
            android.Manifest.permission_group.CONTACTS       -> Contacts
            android.Manifest.permission_group.CALENDAR       -> Calendar
            android.Manifest.permission_group.SMS            -> SMS
            android.Manifest.permission_group.STORAGE        -> Storage
            android.Manifest.permission_group.PHONE          -> Phone
            android.Manifest.permission_group.MICROPHONE     -> Microphone
            android.Manifest.permission_group.LOCATION       -> Location
            android.Manifest.permission_group.CAMERA         -> Camera
            android.Manifest.permission_group.NEARBY_DEVICES -> NearbyDevices
            PERMISSION_GROUP_INTERNET                        -> Internet
            else                                             -> Other
        }
    }
}

open class TrackersGroup(
    @param:StringRes val labelId: Int,
    @param:StringRes val descriptionId: Int,
    val icon: ImageVector,
) {
    object Identification : TrackersGroup(
        R.string.trackers_identification,
        R.string.trackers_identification_description,
        Phosphor.User
    )

    object Analytics : TrackersGroup(
        R.string.trackers_analytics,
        R.string.trackers_analytics_description,
        Phosphor.ChartLine
    )

    object Advertisement : TrackersGroup(
        R.string.trackers_ads,
        R.string.trackers_ads_description,
        Phosphor.CurrencyCircleDollar
    )

    object Location : TrackersGroup(
        R.string.trackers_location,
        R.string.trackers_location_description,
        Phosphor.MapPin
    )

    object Profiling : TrackersGroup(
        R.string.trackers_profiling,
        R.string.trackers_profiling_description,
        Phosphor.UserFocus
    )

    object CrashReporting : TrackersGroup(
        R.string.trackers_bug,
        R.string.trackers_bug_description,
        Phosphor.Bug
    )

    companion object {
        fun String.getTrackersGroup() = when (this) {
            "Analytics"      -> Analytics
            "Profiling"      -> Profiling
            "Identification" -> Identification
            "Advertisement"  -> Advertisement
            "Location"       -> Location
            else             -> CrashReporting // "Crash reporting"
        }
    }
}

open class SourceInfo(
    @param:StringRes val labelId: Int,
    @param:StringRes val descriptionId: Int,
    val icon: ImageVector,
) {
    object Proprietary : SourceInfo(
        R.string.source_proprietary,
        R.string.source_proprietary_description,
        Phosphor.EyeSlash
    )

    object Open : SourceInfo(
        R.string.source_open,
        R.string.source_open_description,
        Icon.Opensource
    )

    object Copyleft : SourceInfo(
        R.string.source_copyleft,
        R.string.source_copyleft_description,
        Phosphor.Copyleft
    )

    object Copyright : SourceInfo(
        R.string.source_copyright,
        R.string.source_copyright_description,
        Phosphor.Copyright
    )

    object Dependency : SourceInfo(
        R.string.source_dependencies,
        R.string.source_dependencies_description,
        Phosphor.GitPullRequest
    )

}