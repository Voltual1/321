package me.voltual.a321.data.unified

import me.voltual.a321.KtorClient

fun KtorClient.FileInfo.toUnifiedModel(): PanFile {
    return PanFile(
        id = this.FileId,
        name = this.FileName,
        size = this.Size,
        isDirectory = this.isDirectory,
        updateTime = this.UpdateAt,
        category = this.Category,
        isAbnormal = this.isAbnormal
    )
}

fun List<KtorClient.FileInfo>.toUnifiedList(): List<PanFile> {
    return this.map { it.toUnifiedModel() }
}