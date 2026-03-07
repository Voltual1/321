package me.voltual.pyrolysis.core.utils.extension

import me.voltual.pyrolysis.data.entity.UpdateListItem
import java.text.Collator

private val collator = Collator.getInstance().apply {
    strength = Collator.PRIMARY
}

fun <T> Collection<T>.sortedLocalized(selector: T.() -> String): List<T> =
    sortedWith { a, b -> collator.compare(a.selector(), b.selector()) }

fun <T> Collection<T>.sortedLocalizedDescending(selector: T.() -> String): List<T> =
    sortedWith { a, b -> collator.compare(b.selector(), a.selector()) }

fun Collection<UpdateListItem>.partitionTypes(): Pair<List<UpdateListItem.UpdateItem>, List<UpdateListItem.DownloadOnlyItem>> {
    val updates = filterIsInstance<UpdateListItem.UpdateItem>()
    val downloads = filterIsInstance<UpdateListItem.DownloadOnlyItem>()
    return updates to downloads
}